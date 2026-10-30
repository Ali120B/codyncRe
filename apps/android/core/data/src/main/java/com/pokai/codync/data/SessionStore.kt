package com.pokai.codync.data

import com.pokai.codync.model.Bot
import com.pokai.codync.model.Computer
import com.pokai.codync.model.Entry
import com.pokai.codync.transport.ConnectionState
import com.pokai.codync.transport.HostTransport
import com.pokai.codync.transport.PairUrl
import com.pokai.codync.transport.TransportKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A phone can keep several paired computers connected at once. Each computer owns an
 * independent [ComputerSession]; this facade merges their roster, chats and approvals for the
 * UI and routes every host call to the computer that owns the bot/message.
 */
class SessionStore(
    private val createTransport: () -> HostTransport,
    private val outbox: PendingSendOutbox? = null,
    private val onComputerSaved: suspend (Computer) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _computers = MutableStateFlow<List<Computer>>(emptyList())
    private val _lanes = MutableStateFlow<Map<String, ComputerSession>>(emptyMap())
    private val laneStates = MutableStateFlow<Map<String, SessionSnapshot>>(emptyMap())
    private val _active = MutableStateFlow<String?>(null)
    private val _pairing = MutableStateFlow<String?>(null)

    /** Per-computer mirrors, keyed by computer id. Used by push registration and the cache. */
    val sessions: StateFlow<Map<String, ComputerSession>> = _lanes.asStateFlow()

    val snapshot: StateFlow<SessionSnapshot> = combine(_computers, laneStates, _active, _pairing) { computers, lanes, active, pairing ->
        aggregate(computers, lanes, active, pairing)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, SessionSnapshot())

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

    fun botComputers(): Flow<Map<String, String>> = snapshot
        .map { it.botComputers }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun computerConnections(): Flow<Map<String, ConnectionState>> = laneStates
        .map { lanes -> lanes.mapValues { it.value.connection } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun connectionForComputer(computerId: String): Flow<ConnectionState> = snapshot
        .map { state ->
            state.connections[computerId]
                ?: ConnectionState(computer = state.computers.firstOrNull { it.id == computerId })
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun connectionForBot(botId: String): Flow<ConnectionState> = snapshot
        .map { state ->
            val computerId = state.botComputers[botId]
            state.connections[computerId]
                ?: state.connection
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    fun pairingMessage(): Flow<String?> = _pairing.asStateFlow()

    fun approvalEntries(): Flow<List<Entry>> = snapshot
        .map { it.pendingApprovals }
        .distinctUntilChanged { old, new -> old === new }
        .flowOn(Dispatchers.Default)

    fun entries(botId: String): Flow<EntryTimeline> = snapshot
        .map { state -> state.entriesByBot[botId].orEmpty() }
        .distinctUntilChanged { old, new -> old === new }
        .map(::EntryTimeline)
        .flowOn(Dispatchers.Default)

    fun entry(botId: String, entryId: String): Flow<Entry?> = if (entryId == "main" || entryId == "new") {
        kotlinx.coroutines.flow.flowOf<Entry?>(null)
    } else {
        snapshot
            .map { state -> state.entriesByBot[botId].orEmpty().firstOrNull { it.id == entryId } }
            .distinctUntilChanged { old, new -> old === new }
            .flowOn(Dispatchers.Default)
    }

    fun timelineEntries(botId: String, chatId: String): Flow<EntryTimeline> = snapshot
        .map { state -> state.entriesByBot[botId].orEmpty() }
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
        .map { it.connection }
        .distinctUntilChanged()

    fun connectionStatus(): Flow<ConnectionState> = snapshot
        .map { it.connection.copy(rev = 0) }
        .distinctUntilChanged()

    fun actionError(): Flow<String?> = snapshot
        .map { it.actionError }
        .distinctUntilChanged()

    fun computerIdForBot(botId: String): String? =
        snapshot.value.botComputers[botId]

    fun computerForBot(botId: String): Computer? =
        computerIdForBot(botId)?.let { id -> snapshot.value.computers.firstOrNull { it.id == id } }

    fun activeComputerId(): String? = _active.value

    fun selectComputer(computerId: String) {
        _active.value = computerId
    }

    fun restoreComputers(computers: List<Computer>) {
        val distinct = computers.distinctBy(Computer::id)
        _computers.value = distinct
        distinct.firstOrNull()?.let { if (_active.value == null) _active.value = it.id }
        // Every paired computer connects at the same time; they stay independent.
        distinct.forEach { connect(it, activate = false) }
    }

    fun restoreCache(
        computer: Computer,
        bots: List<Bot>,
        entriesByBot: Map<String, List<Entry>>,
        chatTitlesByBot: Map<String, Map<String, String>> = emptyMap(),
    ) {
        ensureComputer(computer)
        lane(computer).restoreCache(computer, bots, entriesByBot, chatTitlesByBot)
    }

    fun connect(computer: Computer) = connect(computer, activate = true)

    private fun connect(computer: Computer, activate: Boolean) {
        ensureComputer(computer)
        if (activate) _active.value = computer.id
        scope.launch {
            val lane = lane(computer)
            val state = lane.snapshot.value
            if (state.connection.kind == TransportKind.Offline || state.connection.hello == null) {
                lane.connect(computer)
            }
            onComputerSaved(computer)
        }
    }

    fun pairFromPaste(raw: String, deviceName: String = "Android") {
        scope.launch {
            val offer = PairUrl.parse(raw)
            if (offer == null) {
                _pairing.value = "That doesn't look like a Codync pairing link."
                return@launch
            }
            _pairing.value = "Pairing…"
            runCatching { createTransport().pair(offer, deviceName).getOrThrow() }
                .onSuccess { computer ->
                    ensureComputer(computer)
                    _pairing.value = "Paired with ${computer.name}"
                    onComputerSaved(computer)
                    connect(computer)
                }
                .onFailure { error ->
                    _pairing.value = error.message ?: "Pairing failed"
                }
        }
    }

    suspend fun send(
        botId: String,
        chatId: String,
        text: String,
        attachments: List<String> = emptyList(),
        clientNonce: String = java.util.UUID.randomUUID().toString(),
    ): Result<Entry?> {
        val lane = laneForBody(JsonObject(emptyMap()), botId = botId)
            ?: return Result.failure(IllegalStateException("Pair a computer before sending"))
        activate(computerIdForBot(botId))
        return lane.send(botId, chatId, text, attachments, clientNonce)
    }

    suspend fun loadChatTitles(botId: String): Result<Map<String, String>> =
        laneForBot(botId)?.loadChatTitles(botId)
            ?: Result.failure(IllegalStateException("Unknown bot"))

    suspend fun deleteChat(botId: String, chatId: String): Result<Unit> =
        laneForBot(botId)?.deleteChat(botId, chatId)
            ?: Result.failure(IllegalStateException("Unknown bot"))

    suspend fun respondPermission(entryId: String, optionId: String): Result<Unit> {
        val lane = laneForEntry(entryId)
            ?: laneForActive()
            ?: return Result.failure(IllegalStateException("Pair a computer first"))
        return lane.respondPermission(entryId, optionId)
    }

    suspend fun call(method: String, body: JsonObject): Result<JsonElement> {
        val byBot = body["botId"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        if (byBot != null) activate(computerIdForBot(byBot))
        val lane = when {
            byBot != null -> laneForBot(byBot)
            body.containsKey("entryId") -> laneForEntry(body["entryId"]?.toString()?.trim('"').orEmpty())
            else -> null
        } ?: laneForActive() ?: return Result.failure(IllegalStateException("Pair a computer first"))
        return lane.call(method, body)
    }

    /** Explicit computer target for screen-scoped calls such as models and folders. */
    suspend fun callOn(computerId: String, method: String, body: JsonObject): Result<JsonElement> {
        val lane = _lanes.value[computerId]
            ?: return Result.failure(IllegalStateException("That computer is not paired"))
        return lane.call(method, body)
    }

    /**
     * Tells the host whether this device is actively looking at a chat, so pushes can be
     * suppressed only for what is actually on screen. A null bot clears every computer.
     */
    suspend fun setViewing(botId: String?) {
        if (botId == null) {
            _lanes.value.keys.forEach { computerId ->
                callOn(computerId, "setViewing", JsonObject(emptyMap()))
            }
            return
        }
        laneForBot(botId)?.call("setViewing", JsonObject(mapOf("botId" to kotlinx.serialization.json.JsonPrimitive(botId))))
    }

    suspend fun loadOlder(botId: String) {
        laneForBot(botId)?.loadOlder(botId)
    }

    suspend fun ensureEntry(botId: String, entryId: String) {
        laneForBot(botId)?.ensureEntry(botId, entryId)
    }

    fun markOffline() {
        _lanes.value.values.forEach { it.markOffline() }
    }

    private fun ensureComputer(computer: Computer) {
        _computers.update { current -> if (current.any { it.id == computer.id }) current else current + computer }
    }

    private fun lane(computer: Computer): ComputerSession {
        _lanes.value[computer.id]?.let { return it }
        val lane = ComputerSession(createTransport(), outbox = outbox)
        _lanes.update { it + (computer.id to lane) }
        scope.launch {
            lane.snapshot.collect { state ->
                laneStates.update { it + (computer.id to state) }
            }
        }
        return lane
    }

    private fun laneForBot(botId: String): ComputerSession? =
        computerIdForBot(botId)?.let { _lanes.value[it] }

    private fun laneForActive(): ComputerSession? =
        _active.value?.let { _lanes.value[it] }
            ?: _lanes.value.values.firstOrNull { it.snapshot.value.connection.hello != null }
            ?: _lanes.value.values.firstOrNull()

    private fun laneForEntry(entryId: String): ComputerSession? {
        if (entryId.isBlank()) return null
        laneStates.value.forEach { (computerId, state) ->
            if (state.pendingApprovals.any { it.id == entryId }) return _lanes.value[computerId]
            if (state.entriesByBot.values.any { entries -> entries.any { it.id == entryId } }) {
                return _lanes.value[computerId]
            }
        }
        return null
    }

    private fun laneForBody(body: JsonObject, botId: String? = null): ComputerSession? =
        (botId ?: body["botId"]?.toString()?.trim('"'))?.let(::laneForBot) ?: laneForActive()

    private fun activate(computerId: String?) {
        if (computerId != null) _active.value = computerId
    }

    private fun aggregate(
        computers: List<Computer>,
        lanes: Map<String, SessionSnapshot>,
        active: String?,
        pairing: String?,
    ): SessionSnapshot {
        val ordered = computers.mapNotNull { computer -> lanes[computer.id]?.let { computer.id to it } }
        val botComputers = HashMap<String, String>()
        val bots = ArrayList<Bot>()
        val entriesByBot = HashMap<String, List<Entry>>()
        val titles = HashMap<String, Map<String, String>>()
        val approvals = ArrayList<Entry>()
        val requests = ArrayList<com.pokai.codync.model.AccessRequest>()
        for ((computerId, state) in ordered) {
            state.bots.forEach { bot ->
                if (!botComputers.containsKey(bot.id)) {
                    botComputers[bot.id] = computerId
                    bots += bot
                }
            }
            state.entriesByBot.forEach { (botId, list) ->
                val existing = entriesByBot[botId]
                entriesByBot[botId] = if (existing == null) list else mergeEntries(existing, list)
            }
            titles.putAll(state.chatTitlesByBot)
            approvals += state.pendingApprovals
            requests += state.accessRequests
        }
        val connections = ordered.associate { (id, state) -> id to state.connection }
        val connection = connections[active]
            ?: connections.values.firstOrNull { it.hello != null }
            ?: connections.values.firstOrNull()
            ?: ConnectionState()
        return SessionSnapshot(
            connection = connection,
            connections = connections,
            computers = computers,
            bots = bots,
            botComputers = botComputers,
            rosterLoaded = ordered.any { it.second.rosterLoaded },
            entriesByBot = entriesByBot,
            chatTitlesByBot = titles,
            pendingApprovals = approvals.distinctBy(Entry::id).sortedByDescending(Entry::createdAt),
            accessRequests = requests.distinctBy { it.requestId },
            pairingMessage = pairing,
            actionError = ordered.firstNotNullOfOrNull { it.second.actionError },
        )
    }

    private fun mergeEntries(left: List<Entry>, right: List<Entry>): List<Entry> {
        val merged = LinkedHashMap<String, Entry>(left.size + right.size)
        left.forEach { merged[it.id] = it }
        right.forEach { entry ->
            val existing = merged[entry.id]
            if (existing == null || entry.rev >= existing.rev) merged[entry.id] = entry
        }
        return merged.values.sortedBy(Entry::seq)
    }
}
