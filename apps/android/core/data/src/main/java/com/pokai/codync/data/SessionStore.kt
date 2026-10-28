package com.pokai.codync.data

import com.pokai.codync.model.AccessRequest
import com.pokai.codync.model.Bot
import com.pokai.codync.model.Computer
import com.pokai.codync.model.Entry
import com.pokai.codync.model.WireJson
import com.pokai.codync.transport.ConnectionState
import com.pokai.codync.transport.HostEvent
import com.pokai.codync.transport.HostTransport
import com.pokai.codync.transport.PairUrl
import com.pokai.codync.transport.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SessionSnapshot(
    val connection: ConnectionState = ConnectionState(),
    val computers: List<Computer> = emptyList(),
    val bots: List<Bot> = emptyList(),
    val rosterLoaded: Boolean = false,
    val entriesByBot: Map<String, List<Entry>> = emptyMap(),
    val chatTitlesByBot: Map<String, Map<String, String>> = emptyMap(),
    val pendingApprovals: List<Entry> = emptyList(),
    val accessRequests: List<AccessRequest> = emptyList(),
    val pairingMessage: String? = null,
    val actionError: String? = null,
)

data class BotLookup(val rosterLoaded: Boolean = false, val bot: Bot? = null)

/** Identity-equal wrapper keeps Compose from structurally comparing a full transcript per delta. */
class EntryTimeline(val entries: List<Entry>)

/**
 * M1/M2 session mirror. Wraps a [HostTransport] and keeps roster/chat state for the UI.
 */
class SessionStore(
    private val transport: HostTransport,
    private val onComputerSelected: suspend (Computer, Boolean) -> Unit = { _, _ -> },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val outbox: PendingSendOutbox? = null,
) {
    private val _snapshot = MutableStateFlow(SessionSnapshot())
    val snapshot: StateFlow<SessionSnapshot> = _snapshot.asStateFlow()
    private val historyLoading = ConcurrentHashMap.newKeySet<String>()
    private val historyComplete = ConcurrentHashMap.newKeySet<String>()
    private val sentOutboxNonces = ConcurrentHashMap.newKeySet<String>()
    private val outboxMutex = Mutex()
    @Volatile private var expectedComputerId: String? = null
    @Volatile private var syncedComputerId: String? = null

    fun bot(botId: String): Flow<Bot?> = snapshot
        .map { state -> state.bots.firstOrNull { it.id == botId } }
        .distinctUntilChanged { old, new -> old === new }
        .flowOn(Dispatchers.Default)

    fun botLookup(botId: String): Flow<BotLookup> = snapshot
        .map { state -> BotLookup(state.rosterLoaded, state.bots.firstOrNull { it.id == botId }) }
        .distinctUntilChanged { old, new -> old.rosterLoaded == new.rosterLoaded && old.bot === new.bot }
        .flowOn(Dispatchers.Default)

    fun bots(): Flow<List<Bot>> = snapshot
        .map { it.bots }
        .distinctUntilChanged { old, new -> old === new }
        .flowOn(Dispatchers.Default)

    fun computers(): Flow<List<Computer>> = snapshot
        .map { it.computers }
        .distinctUntilChanged { old, new -> old === new }
        .flowOn(Dispatchers.Default)

    fun pairingMessage(): Flow<String?> = snapshot
        .map { it.pairingMessage }
        .distinctUntilChanged()

    /** Maintained from permission events so chat token updates do not rescan every transcript. */
    fun approvalEntries(): Flow<List<Entry>> = snapshot
        .map { it.pendingApprovals }
        .distinctUntilChanged { old, new -> old === new }
        .flowOn(Dispatchers.Default)

    fun entries(botId: String): Flow<EntryTimeline> = snapshot
        .map { state -> state.entriesByBot[botId].orEmpty() }
        // State updates for other bots retain this list instance. Avoid a structural scan of a
        // large transcript just to discover that the selected bot's entries did not change.
        .distinctUntilChanged { old, new -> old === new }
        .map(::EntryTimeline)
        .flowOn(Dispatchers.Default)

    fun entry(botId: String, entryId: String): Flow<Entry?> = if (entryId == "main" || entryId == "new") {
        flowOf<Entry?>(null)
    } else {
        snapshot
            .map { state -> state.entriesByBot[botId].orEmpty().firstOrNull { it.id == entryId } }
            .distinctUntilChanged { old, new -> old === new }
            .flowOn(Dispatchers.Default)
    }

    /** One visible conversation, filtered off the UI thread so stream deltas stay cheap. */
    fun timelineEntries(botId: String, chatId: String): Flow<EntryTimeline> = snapshot
        .map { state -> state.entriesByBot[botId].orEmpty() }
        // Bot status, connection and cloud updates should not refilter a transcript whose list
        // reference did not change. In particular, streamed text in another bot must stay cheap.
        .distinctUntilChanged { old, new -> old === new }
        .map { entries ->
            when (chatId) {
                "new" -> EntryTimeline(emptyList())
                "main" -> EntryTimeline(entries.filter { it.threadId == null && it.data.chat != true })
                else -> EntryTimeline(entries.asSequence()
                    .filter { it.threadId == chatId || (it.id == chatId && it.data.chat == true) }
                    .toList())
            }
        }
        .distinctUntilChanged { old, new -> old.entries === new.entries }
        .flowOn(Dispatchers.Default)

    fun chatTitles(botId: String): Flow<Map<String, String>> = snapshot
        .map { state -> state.chatTitlesByBot[botId].orEmpty() }
        .distinctUntilChanged()

    fun connection(): Flow<ConnectionState> = snapshot
        .map { state -> state.connection }
        .distinctUntilChanged()

    /** Connection details for screens that do not render the per-event revision counter. */
    fun connectionStatus(): Flow<ConnectionState> = snapshot
        .map { state -> state.connection.copy(rev = 0) }
        .distinctUntilChanged()

    fun actionError(): Flow<String?> = snapshot
        .map { state -> state.actionError }
        .distinctUntilChanged()

    fun restoreComputers(computers: List<Computer>) {
        _snapshot.update { it.copy(computers = computers.distinctBy(Computer::id)) }
        computers.firstOrNull()?.let(::connect)
    }

    fun restoreCache(
        computer: Computer,
        bots: List<Bot>,
        entriesByBot: Map<String, List<Entry>>,
        chatTitlesByBot: Map<String, Map<String, String>> = emptyMap(),
    ) {
        expectedComputerId = computer.id
        syncedComputerId = null
        _snapshot.update {
            it.copy(
                connection = ConnectionState(computer = computer),
                bots = bots,
                rosterLoaded = true,
                entriesByBot = entriesByBot,
                chatTitlesByBot = chatTitlesByBot,
                pendingApprovals = pendingApprovals(entriesByBot.values.asSequence().flatMap { it.asSequence() }),
            )
        }
        outbox?.let { queue ->
            scope.launch {
                queue.pending(computer.id).forEach(::restoreQueuedSend)
            }
        }
    }

    private fun restoreQueuedSend(send: PendingSend) {
        if (send.clientNonce in sentOutboxNonces) return
        val entry = Entry(
            id = "local-${send.clientNonce}",
            seq = Long.MAX_VALUE,
            botId = send.botId,
            threadId = send.chatId.takeIf { it != "main" },
            kind = "user",
            data = com.pokai.codync.model.EntryData(
                text = send.text,
                clientNonce = send.clientNonce,
                status = "queued",
            ),
            createdAt = send.createdAt,
            updatedAt = send.createdAt,
        )
        _snapshot.update { state ->
            val current = state.entriesByBot[send.botId].orEmpty()
            if (current.any { it.data.clientNonce == send.clientNonce }) state
            else state.copy(entriesByBot = state.entriesByBot + (send.botId to (current + entry)))
        }
    }

    init {
        scope.launch {
            var connectedComputerId: String? = null
            transport.state.collect { conn ->
                _snapshot.update { state ->
                    val keepSelectedComputer = conn.computer == null && state.connection.computer != null
                    state.copy(connection = if (keepSelectedComputer) conn.copy(computer = state.connection.computer) else conn)
                }
                val onlineComputerId = conn.computer?.id
                    ?.takeIf { conn.kind != TransportKind.Offline && conn.hello != null }
                if (onlineComputerId == null) {
                    connectedComputerId = null
                } else if (onlineComputerId != connectedComputerId) {
                    connectedComputerId = onlineComputerId
                    scope.launch { flushPendingSends(onlineComputerId) }
                }
            }
        }
        val incoming = Channel<HostEvent>(Channel.UNLIMITED)
        scope.launch {
            transport.events.collect { incoming.send(it) }
        }
        scope.launch {
            for (first in incoming) {
                val batch = mutableListOf(first)
                // Collapse event bursts from the host's catch-up into one state publication.
                delay(12)
                while (true) {
                    val next = incoming.tryReceive().getOrNull() ?: break
                    batch += next
                }
                applyEvents(batch)
            }
        }
    }

    private fun applyEvents(events: List<HostEvent>) {
        _snapshot.update { snapshot -> events.fold(snapshot, ::reduceEvent) }
    }

    private fun reduceEvent(snapshot: SessionSnapshot, event: HostEvent): SessionSnapshot {
        val expected = expectedComputerId
        if (event is HostEvent.Hello) {
            val actual = event.hello.computerId
            if (expected != null && actual != null && actual != expected) return snapshot
            syncedComputerId = expected
            return snapshot.copy(connection = snapshot.connection.copy(hello = event.hello, rev = event.hello.rev))
        }
        if (expected != null && syncedComputerId != expected) return snapshot
        return when (event) {
        is HostEvent.Hello -> snapshot
        is HostEvent.Bots -> snapshot.copy(bots = event.bots, rosterLoaded = true)
        is HostEvent.BotUpsert -> snapshot.copy(
            bots = snapshot.bots.filterNot { it.id == event.bot.id } + event.bot,
            rosterLoaded = true,
        )
        is HostEvent.BotDeleted -> snapshot.copy(
            bots = snapshot.bots.filterNot { it.id == event.id },
            rosterLoaded = true,
            entriesByBot = snapshot.entriesByBot - event.id,
            chatTitlesByBot = snapshot.chatTitlesByBot - event.id,
            pendingApprovals = snapshot.pendingApprovals.filterNot { it.botId == event.id },
        )
        is HostEvent.EntryUpsert -> {
            val botId = event.entry.botId
            val existing = snapshot.entriesByBot[botId].orEmpty()
            // Stream deltas frequently update the same entry while a response is being written.
            // Replace that row in place; sorting the full transcript for every token made long
            // chats progressively more expensive to scroll.
            val bySequence = existing.binarySearchBy(event.entry.seq) { it.seq }
            val sequenceIndex = if (bySequence < 0) -1 else existing.indexOfFirstFromSequence(
                sequence = event.entry.seq,
                id = event.entry.id,
            )
            val index = when {
                sequenceIndex >= 0 -> sequenceIndex
                event.entry.kind == "user" && event.entry.data.clientNonce != null -> existing.indexOfFirst {
                    it.kind == "user" && it.data.clientNonce == event.entry.data.clientNonce
                }
                else -> -1
            }
            val next = if (index >= 0) {
                val previous = existing[index]
                if (previous.rev > event.entry.rev) existing else existing.toMutableList().apply {
                    set(index, event.entry)
                }.let { replaced ->
                    if (previous.seq == Long.MAX_VALUE && event.entry.seq != Long.MAX_VALUE) replaced.sortedBy(Entry::seq)
                    else replaced
                }
            } else {
                val insertion = existing.binarySearchBy(event.entry.seq) { it.seq }.let { result ->
                    if (result < 0) -result - 1 else result
                }
                existing.toMutableList().apply { add(insertion, event.entry) }
            }
            val approvals = updatePendingApproval(
                current = snapshot.pendingApprovals,
                previous = existing.getOrNull(index),
                updated = event.entry,
            ).takeUnless { existing.getOrNull(index)?.rev?.let { it > event.entry.rev } == true }
                ?: snapshot.pendingApprovals
            snapshot.copy(
                entriesByBot = snapshot.entriesByBot + (botId to next),
                pendingApprovals = approvals,
            )
        }
        is HostEvent.EntriesDeleted -> {
            val existing = snapshot.entriesByBot[event.botId].orEmpty()
            snapshot.copy(
                entriesByBot = snapshot.entriesByBot + (event.botId to existing.filterNot { it.id in event.ids }),
                pendingApprovals = snapshot.pendingApprovals.filterNot { it.botId == event.botId && it.id in event.ids },
            )
        }
        is HostEvent.Entries -> snapshot.copy(
            entriesByBot = snapshot.entriesByBot + (event.chatId to event.entries),
            pendingApprovals = mergeBotApprovals(snapshot.pendingApprovals, event.chatId, event.entries),
        )
        is HostEvent.AccessRequests -> snapshot.copy(accessRequests = event.requests)
        is HostEvent.Rev -> snapshot.copy(connection = snapshot.connection.copy(rev = event.rev))
        is HostEvent.Error -> snapshot.copy(connection = snapshot.connection.copy(lastError = event.message))
        is HostEvent.ChatDeleted -> {
            val current = snapshot.entriesByBot[event.botId].orEmpty()
            val remove = (current.filter { it.id == event.chatId || it.threadId == event.chatId }.map { it.id }).toSet()
            snapshot.copy(
                entriesByBot = snapshot.entriesByBot + (event.botId to current.filterNot { it.id in remove }),
                pendingApprovals = snapshot.pendingApprovals.filterNot { it.botId == event.botId && it.id in remove },
                chatTitlesByBot = snapshot.chatTitlesByBot + (
                    event.botId to (snapshot.chatTitlesByBot[event.botId].orEmpty() - event.chatId)
                ),
            )
        }
        is HostEvent.ChatTitle -> snapshot.copy(
            chatTitlesByBot = snapshot.chatTitlesByBot + (
                event.botId to (snapshot.chatTitlesByBot[event.botId].orEmpty() + (event.chatId to event.title))
            ),
        )
        is HostEvent.Cloud,
        is HostEvent.Resync,
        is HostEvent.Undecodable,
        -> snapshot
        }
    }

    private fun pendingApprovals(entries: Sequence<Entry>): List<Entry> = entries
        .filter { it.kind == "permission" && it.data.status == "pending" }
        .distinctBy(Entry::id)
        .sortedByDescending(Entry::createdAt)
        .toList()

    private fun updatePendingApproval(
        current: List<Entry>,
        previous: Entry?,
        updated: Entry,
    ): List<Entry> {
        val shouldInclude = updated.kind == "permission" && updated.data.status == "pending"
        val replacedId = previous?.id
        val alreadyPresent = current.any { it.id == updated.id || (replacedId != null && it.id == replacedId) }
        if (!shouldInclude && !alreadyPresent) return current
        return current.asSequence()
            .filterNot { it.id == updated.id || (replacedId != null && it.id == replacedId) }
            .let { remaining -> if (shouldInclude) remaining + sequenceOf(updated) else remaining }
            .distinctBy(Entry::id)
            .sortedByDescending(Entry::createdAt)
            .toList()
    }

    private fun mergeBotApprovals(current: List<Entry>, botId: String, entries: List<Entry>): List<Entry> =
        (current.asSequence().filterNot { it.botId == botId } + pendingApprovals(entries.asSequence()))
            .distinctBy(Entry::id)
            .sortedByDescending(Entry::createdAt)
            .toList()

    private fun List<Entry>.indexOfFirstFromSequence(sequence: Long, id: String): Int {
        val any = binarySearchBy(sequence) { it.seq }
        if (any < 0) return -1
        var first = any
        while (first > 0 && this[first - 1].seq == sequence) first--
        var index = first
        while (index < size && this[index].seq == sequence) {
            if (this[index].id == id) return index
            index++
        }
        return -1
    }

    fun pairFromPaste(raw: String, deviceName: String = "Android") {
        scope.launch {
            val offer = PairUrl.parse(raw)
            if (offer == null) {
                _snapshot.update { it.copy(pairingMessage = "That doesn't look like a Codync pairing link.") }
                return@launch
            }
            _snapshot.update { it.copy(pairingMessage = "Pairing…") }
            transport.pair(offer, deviceName)
                .onSuccess { computer ->
                    _snapshot.update {
                        it.copy(
                            computers = (listOf(computer) + it.computers).distinctBy { c -> c.id },
                            pairingMessage = "Paired with ${computer.name}",
                        )
                    }
                    connect(computer)
                }
                .onFailure { err ->
                    _snapshot.update { it.copy(pairingMessage = err.message ?: "Pairing failed") }
                }
        }
    }

    fun connect(computer: Computer) {
        scope.launch {
            val switching = _snapshot.value.connection.computer?.id != computer.id
            expectedComputerId = computer.id
            if (switching) syncedComputerId = null
            _snapshot.update {
                it.copy(
                    connection = ConnectionState(computer = computer),
                    computers = (listOf(computer) + it.computers).distinctBy { c -> c.id },
                    bots = if (switching) emptyList() else it.bots,
                    rosterLoaded = if (switching) false else it.rosterLoaded,
                    entriesByBot = if (switching) emptyMap() else it.entriesByBot,
                    chatTitlesByBot = if (switching) emptyMap() else it.chatTitlesByBot,
                    pendingApprovals = if (switching) emptyList() else it.pendingApprovals,
                    accessRequests = if (switching) emptyList() else it.accessRequests,
                    actionError = null,
                )
            }
            if (switching) {
                historyComplete.clear()
                historyLoading.clear()
            }
            onComputerSelected(computer, switching)
            transport.connect(computer).onFailure { error ->
                _snapshot.update { state ->
                    if (state.pairingMessage?.startsWith("Paired with ") == true) {
                        state.copy(pairingMessage = "Paired, reconnecting: ${error.message}")
                    } else state
                }
            }
        }
    }

    suspend fun send(
        botId: String,
        chatId: String,
        text: String,
        attachments: List<String> = emptyList(),
        clientNonce: String = UUID.randomUUID().toString(),
    ): Result<Entry?> {
        val connection = _snapshot.value.connection
        if (connection.kind == TransportKind.Offline) {
            if (attachments.isNotEmpty()) return Result.failure(IllegalStateException("Attachments need a connection to upload"))
            if (chatId == "new") return Result.failure(IllegalStateException("Start a new chat when the computer is connected"))
            val computerId = connection.computer?.id
                ?: return Result.failure(IllegalStateException("Pair a computer before sending"))
            val queued = PendingSend(
                computerId = computerId,
                botId = botId,
                chatId = chatId,
                text = text,
                clientNonce = clientNonce,
                createdAt = System.currentTimeMillis(),
            )
            val result = runCatching {
                (outbox ?: error("Offline send queue is unavailable")).enqueue(queued)
                Entry(
                    id = "local-$clientNonce",
                    seq = Long.MAX_VALUE,
                    botId = botId,
                    threadId = chatId.takeIf { it != "main" },
                    kind = "user",
                    data = com.pokai.codync.model.EntryData(text = text, clientNonce = clientNonce, status = "queued"),
                    createdAt = queued.createdAt,
                    updatedAt = queued.createdAt,
                )
            }
            result.getOrNull()?.let { entry ->
                _snapshot.update { state ->
                    val current = state.entriesByBot[botId].orEmpty()
                    val merged = (current.filterNot { it.id == entry.id || it.data.clientNonce == clientNonce } + entry)
                        .sortedBy(Entry::seq)
                    state.copy(entriesByBot = state.entriesByBot + (botId to merged))
                }
            }
            _snapshot.update { it.copy(actionError = result.exceptionOrNull()?.message) }
            return result
        }
        val result = transport.call("send", buildJsonObject {
            put("botId", botId)
            put("text", text)
            put("clientNonce", clientNonce)
            put("newChat", chatId == "new")
            if (chatId.isNotBlank() && chatId != "main" && chatId != "new") put("threadId", chatId)
            put("attachments", kotlinx.serialization.json.buildJsonArray {
                attachments.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        }).map { response ->
            response.jsonObject["entry"]?.let { WireJson.json.decodeFromJsonElement(Entry.serializer(), it) }
        }
        result.getOrNull()?.let { entry -> _snapshot.update { reduceEvent(it, HostEvent.EntryUpsert(entry)) } }
        _snapshot.update { it.copy(actionError = result.exceptionOrNull()?.message) }
        return result
    }

    private suspend fun flushPendingSends(computerId: String) = outboxMutex.withLock {
        val queue = outbox ?: return@withLock
        for (send in queue.pending(computerId)) {
            val connection = transport.state.value
            if (connection.kind == TransportKind.Offline || connection.computer?.id != computerId) return@withLock
            val result = transport.call("send", buildJsonObject {
                put("botId", send.botId)
                put("text", send.text)
                put("clientNonce", send.clientNonce)
                put("newChat", false)
                if (send.chatId != "main") put("threadId", send.chatId)
                put("attachments", kotlinx.serialization.json.buildJsonArray {})
            })
            val response = result.getOrNull() ?: return@withLock
            response.jsonObject["entry"]?.let { value ->
                runCatching { WireJson.json.decodeFromJsonElement(Entry.serializer(), value) }
                    .getOrNull()?.let { entry ->
                        _snapshot.update { state -> reduceEvent(state, HostEvent.EntryUpsert(entry)) }
                    }
            }
            queue.remove(send.clientNonce)
            sentOutboxNonces += send.clientNonce
            _snapshot.update { state ->
                val current = state.entriesByBot[send.botId].orEmpty()
                val remaining = current.filterNot { it.data.clientNonce == send.clientNonce && it.id.startsWith("local-") }
                state.copy(entriesByBot = state.entriesByBot + (send.botId to remaining))
            }
        }
    }

    suspend fun loadChatTitles(botId: String): Result<Map<String, String>> {
        val result = transport.call("chatTitles", buildJsonObject { put("botId", botId) }).map { response ->
            response.jsonObject["titles"]?.jsonObject?.mapValues { (_, value) -> value.jsonPrimitive.content }
                .orEmpty()
        }
        result.getOrNull()?.let { titles ->
            _snapshot.update { state -> state.copy(chatTitlesByBot = state.chatTitlesByBot + (botId to titles)) }
        }
        return result
    }

    suspend fun deleteChat(botId: String, chatId: String): Result<Unit> {
        val result = transport.call("deleteChat", buildJsonObject {
            put("botId", botId)
            put("chatId", chatId)
        }).map { Unit }
        if (result.isSuccess) {
            _snapshot.update { state ->
                val current = state.entriesByBot[botId].orEmpty()
                val removed = current.asSequence()
                    .filter { it.id == chatId || it.threadId == chatId }
                    .map(Entry::id)
                    .toSet()
                val titles = state.chatTitlesByBot[botId].orEmpty() - chatId
                state.copy(
                    entriesByBot = state.entriesByBot + (botId to current.filterNot { it.id in removed }),
                    pendingApprovals = state.pendingApprovals.filterNot { it.botId == botId && it.id in removed },
                    chatTitlesByBot = state.chatTitlesByBot + (botId to titles),
                )
            }
        }
        _snapshot.update { it.copy(actionError = result.exceptionOrNull()?.message) }
        return result
    }

    suspend fun respondPermission(entryId: String, optionId: String): Result<Unit> {
        val result = transport.respondPermission(entryId, optionId)
        _snapshot.update { it.copy(actionError = result.exceptionOrNull()?.message) }
        return result
    }

    suspend fun call(method: String, body: JsonObject): Result<JsonElement> {
        val result = transport.call(method, body)
        _snapshot.update { it.copy(actionError = result.exceptionOrNull()?.message) }
        return result
    }

    suspend fun loadOlder(botId: String) {
        if (botId in historyComplete || !historyLoading.add(botId)) return
        try {
            val current = _snapshot.value.entriesByBot[botId].orEmpty()
            val beforeSeq = current.asSequence()
                .filter { it.threadId == null && it.seq > 0 && it.seq != Long.MAX_VALUE }
                .map(Entry::seq)
                .minOrNull() ?: Long.MAX_VALUE
            val result = transport.call("history", buildJsonObject {
                put("botId", botId)
                put("beforeSeq", beforeSeq)
                put("limit", 100)
            })
            val values = result.getOrNull()?.let { response ->
                runCatching { response.jsonObject["entries"]?.jsonArray.orEmpty().mapNotNull { value ->
                    runCatching { WireJson.json.decodeFromJsonElement(Entry.serializer(), value) }.getOrNull()
                } }.getOrNull()
            } ?: return
            if (values.size < 100) historyComplete.add(botId)
            _snapshot.update { state ->
                val latest = state.entriesByBot[botId].orEmpty().associateBy(Entry::id).toMutableMap()
                values.forEach { entry ->
                    val previous = latest[entry.id]
                    if (previous == null || entry.rev >= previous.rev) latest[entry.id] = entry
                }
                val entries = latest.values.sortedBy(Entry::seq)
                state.copy(
                    entriesByBot = state.entriesByBot + (botId to entries),
                    pendingApprovals = mergeBotApprovals(state.pendingApprovals, botId, entries),
                )
            }
        } finally {
            historyLoading.remove(botId)
        }
    }

    suspend fun ensureEntry(botId: String, entryId: String) {
        repeat(100) {
            val current = _snapshot.value.entriesByBot[botId].orEmpty()
            if (current.any { it.id == entryId } || botId in historyComplete) return
            val previousSize = current.size
            loadOlder(botId)
            if (_snapshot.value.entriesByBot[botId].orEmpty().size <= previousSize) return
        }
    }

    fun markOffline() {
        _snapshot.update {
            it.copy(connection = it.connection.copy(kind = TransportKind.Offline))
        }
    }
}
