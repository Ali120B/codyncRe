package com.pokai.codync.transport

import com.pokai.codync.crypto.ChannelCrypto
import com.pokai.codync.crypto.ChannelHandshake
import com.pokai.codync.crypto.ChannelKeys
import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.Keys
import com.pokai.codync.model.Computer
import com.pokai.codync.model.Hello
import com.pokai.codync.model.PairOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Encrypted direct LAN/Tailscale host channel. The host rejects device HTTP RPCs by design. */
class DirectChannelTransport(
    private val deviceKey: Ed25519PrivateKeyParameters,
    private val deviceName: String = "Android",
    private val client: OkHttpClient = defaultClient(),
) : HostTransport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Mutex()
    private val nextId = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val _state = MutableStateFlow(ConnectionState())
    // Catch-up can contain hundreds of rows; dropping a burst here silently corrupts the roster.
    private val _events = Channel<HostEvent>(Channel.UNLIMITED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    override val events: Flow<HostEvent> = _events.receiveAsFlow()

    @Volatile private var socket: WebSocket? = null
    @Volatile private var listener: ChannelListener? = null
    @Volatile private var keys: ChannelKeys? = null
    @Volatile private var reader: Job? = null
    @Volatile private var heartbeat: Job? = null
    @Volatile private var outboundCounter = 0L
    @Volatile private var inboundCounter = 0L
    @Volatile private var sinceRev = 0L
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var targetComputer: Computer? = null
    @Volatile private var deliberatelyDisconnected = true

    override suspend fun pair(offer: PairOffer, deviceName: String): Result<Computer> =
        runCatching { DirectPairingClient.pair(offer, deviceKey, deviceName) }

    override suspend fun connect(computer: Computer): Result<Hello> {
        val switchingComputers = targetComputer?.id != computer.id
        deliberatelyDisconnected = false
        targetComputer = computer
        reconnectJob?.cancel()
        disconnect()
        deliberatelyDisconnected = false
        targetComputer = computer
        // Revisions belong to one host. Reusing another computer's revision would make the
        // host skip its entire catch-up and leave the roster empty.
        if (switchingComputers) sinceRev = 0
        val failures = mutableListOf<String>()
        for (url in computer.urls) {
            try {
                val result = connectDirect(computer, url)
                if (result.isSuccess) {
                    startReconnectLoop(computer)
                    return result
                }
                result.exceptionOrNull()?.message?.let { failures += "$url: $it" }
                disconnectSocket()
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failures += "$url: ${error.message ?: error::class.simpleName}"
                disconnectSocket()
            }
        }
        computer.cloud?.let { cloud ->
            val relay = connectRelay(computer, cloud)
            if (relay.isSuccess) {
                startReconnectLoop(computer)
                return relay
            }
            relay.exceptionOrNull()?.message?.let { failures += "relay: $it" }
            disconnectSocket()
        }
        val error = IOException(
            failures.takeIf { it.isNotEmpty() }?.joinToString(" · ")
                ?: "This computer has no direct network addresses",
        )
        _state.value = ConnectionState(kind = TransportKind.Offline, computer = computer, lastError = error.message)
        _events.trySend(HostEvent.Error(error.message ?: "Can't reach the computer"))
        startReconnectLoop(computer)
        return Result.failure(error)
    }

    private suspend fun connectDirect(computer: Computer, baseUrl: String): Result<Hello> {
        val opened = ChannelListener()
        val webSocket = client.newWebSocket(Request.Builder().url(channelUrl(baseUrl)).build(), opened)
        return connectOnSocket(computer, opened, webSocket, waitForRelayPresence = false)
    }

    private suspend fun connectRelay(computer: Computer, cloud: String): Result<Hello> {
        val opened = ChannelListener()
        val request = RelaySocketRequest.device(computer.id, cloud, deviceKey)
        val webSocket = client.newWebSocket(request, opened)
        return connectOnSocket(computer, opened, webSocket, waitForRelayPresence = true)
    }

    private suspend fun connectOnSocket(
        computer: Computer,
        opened: ChannelListener,
        webSocket: WebSocket,
        waitForRelayPresence: Boolean,
    ): Result<Hello> {
        listener = opened
        socket = webSocket
        try {
            opened.awaitOpen()
            if (waitForRelayPresence) {
                val online = withTimeout(12_000) {
                    while (true) {
                        val packet = parse(opened.receive())
                        when (packet.string("t")) {
                            "presence" -> return@withTimeout packet["online"]?.jsonPrimitive?.booleanOrNull == true
                            "reject" -> throw IOException(packet.string("message") ?: "The relay rejected this computer")
                        }
                    }
                    false
                }
                check(online) { "The computer is offline on the Codync relay" }
            }
            val handshake = ChannelHandshake(computer, deviceKey)
            val hello = buildJsonObject {
                put("t", "hello")
                put("v", 1)
                put("dk", Codec.b64(handshake.devicePublicKey))
                put("ek", Codec.b64(handshake.ephemeralPublicKey))
                put("n", handshake.nonceBase64())
                put("sig", Codec.b64(Keys.ed25519Sign(deviceKey, handshake.signInput)))
            }
            check(webSocket.send(hello.toString())) { "Couldn't start the secure host connection" }
            val welcome = withTimeout(10_000) {
                while (true) {
                    val packet = parse(opened.receive())
                    when (packet.string("t")) {
                        "welcome" -> return@withTimeout packet
                        "reject" -> throw IOException(packet.string("message") ?: "The host rejected this device")
                    }
                }
                error("Missing host handshake")
            }
            val channelKeys = handshake.finish(
                Codec.unb64n(welcome.string("ek") ?: error("Missing host ephemeral key"), 32),
                Codec.unb64n(welcome.string("sig") ?: error("Missing host signature"), 64),
            )
            keys = channelKeys
            outboundCounter = 0
            inboundCounter = 0
            val generationSocket = webSocket
            reader = scope.launch { readLoop(opened, generationSocket, channelKeys) }
            if (waitForRelayPresence) {
                heartbeat = scope.launch {
                    while (socket === generationSocket) {
                        delay(30_000)
                        if (socket === generationSocket) generationSocket.send("{\"t\":\"ping\"}")
                    }
                }
            }

            val response = rpc("hello", buildJsonObject {})
            val hostHello = json.decodeFromJsonElement(Hello.serializer(), response)
            require(hostHello.computerId == null || hostHello.computerId == computer.id) {
                "The host identified itself as another computer"
            }
            val resumeFromRev = sinceRev
            sinceRev = hostHello.rev
            _state.value = ConnectionState(
                kind = if (waitForRelayPresence) TransportKind.Relay else TransportKind.Direct,
                computer = computer,
                hello = hostHello,
                rev = hostHello.rev,
                connectedAt = System.currentTimeMillis(),
            )
            _events.trySend(HostEvent.Hello(hostHello))
            // No durable Android mirror exists yet; request the host's initial catch-up.
            subscribeEvents(resumeFromRev)
            return Result.success(hostHello)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            return Result.failure(error)
        }
    }

    override suspend fun disconnect() {
        deliberatelyDisconnected = true
        targetComputer = null
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectSocket()
    }

    private suspend fun disconnectSocket() {
        heartbeat?.cancel()
        heartbeat = null
        reader?.cancel()
        reader = null
        socket?.close(1000, "disconnect")
        socket = null
        listener = null
        keys = null
        sendLock.withLock {
            outboundCounter = 0
            inboundCounter = 0
        }
        pending.values.forEach { it.completeExceptionally(IOException("Disconnected")) }
        pending.clear()
        _state.value = ConnectionState(kind = TransportKind.Offline)
    }

    private fun startReconnectLoop(computer: Computer) {
        if (deliberatelyDisconnected || reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            var backoff = 1_000L
            while (!deliberatelyDisconnected && targetComputer?.id == computer.id) {
                if (_state.value.kind == TransportKind.Direct || _state.value.kind == TransportKind.Relay) {
                    delay(1_000)
                    continue
                }
                delay(backoff)
                if (deliberatelyDisconnected || targetComputer?.id != computer.id) break
                var connected = false
                var retryError: Throwable? = null
                for (url in computer.urls) {
                    val result = connectDirect(computer, url)
                    if (result.isSuccess) {
                        connected = true
                        backoff = 1_000L
                        break
                    }
                    retryError = result.exceptionOrNull()
                    disconnectSocket()
                }
                val cloud = computer.cloud
                if (!connected && !cloud.isNullOrBlank()) {
                    val result = connectRelay(computer, cloud)
                    if (result.isSuccess) {
                        connected = true
                        backoff = 1_000L
                    } else {
                        retryError = result.exceptionOrNull() ?: retryError
                        disconnectSocket()
                    }
                }
                if (!connected) {
                    backoff = (backoff * 2).coerceAtMost(30_000L)
                    _state.update {
                        it.copy(kind = TransportKind.Offline, computer = computer, lastError = retryError?.message)
                    }
                }
            }
        }
    }

    override suspend fun sendUserMessage(
        botId: String,
        chatId: String,
        text: String,
        clientNonce: String,
    ): Result<Unit> = runCatching {
        call("send", buildJsonObject {
            put("botId", botId)
            put("text", text)
            put("clientNonce", clientNonce)
            if (chatId.isNotBlank() && chatId != "main" && chatId != "new") put("threadId", chatId)
            put("newChat", chatId == "new")
        }).getOrThrow()
        Unit
    }

    override suspend fun respondPermission(entryId: String, optionId: String): Result<Unit> = runCatching {
        call("respondPermission", buildJsonObject {
            put("entryId", entryId)
            put("optionId", optionId)
        }).getOrThrow()
        Unit
    }

    override suspend fun call(method: String, body: JsonObject): Result<JsonElement> = runCatching {
        rpc(method, body)
    }

    private suspend fun rpc(method: String, body: JsonObject): JsonElement {
        val id = nextRequestId()
        check(!pending.containsKey(id)) { "Too many host requests are in flight" }
        val answer = CompletableDeferred<JsonObject>()
        pending[id] = answer
        try {
            sendInner(buildJsonObject {
                put("id", id)
                put("m", method)
                put("b", body)
            }.toString().toByteArray(Charsets.UTF_8))
            val response = withTimeout(timeoutFor(method)) { answer.await() }
            response["err"]?.let { err ->
                val status = err.jsonObject["status"]?.jsonPrimitive?.content
                val detail = err.jsonObject["message"]?.jsonPrimitive?.content ?: "Host call failed"
                throw IOException(if (status == null) detail else "$detail (host error $status)")
            }
            return response["ok"] ?: error("Malformed host response")
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun subscribeEvents(rev: Long) {
        val id = nextRequestId()
        val device = Codec.b64(Keys.ed25519Public(deviceKey)).take(16)
        sendInner(buildJsonObject {
            put("id", id)
            put("sub", "events")
            put("b", buildJsonObject {
                put("since", rev)
                put("client", "android-$device")
            })
        }.toString().toByteArray(Charsets.UTF_8))
    }

    private fun nextRequestId(): Long = nextId.incrementAndGet().and(0xffff_ffffL).let { if (it == 0L) 1L else it }

    private fun timeoutFor(method: String): Long = when (method) {
        "agentAuthenticate" -> 660_000L
        "agentSetup" -> 300_000L
        "agentAuth", "setAgentEnv", "connectorVerify", "credentialUpdateConnector", "connectorRequestFinish" -> 120_000L
        "marketConnectors", "marketSkills", "composioToolkits", "composioConnect", "composioConnectFields",
        "composioConnection", "setComposioKey", "connectorInfo", "connectorSignIn", "connectorSignInFinish",
        "installConnector", "importConnectors", "installSkill", "skillDetails", "credentialStatus",
        "credentialLogins", "credentialSaveLogin", "credentialRemoveLogin", "credentialSetOnePassword" -> 60_000L
        else -> 30_000L
    }

    private suspend fun sendInner(message: ByteArray) {
        val ws = socket ?: error("Not connected to a computer")
        val channelKeys = keys ?: error("The secure host channel isn't ready")
        require(message.size <= 16 * 1024 * 1024) { "Host request is too large" }
        sendLock.withLock {
            var rest = message
            do {
                val count = minOf(rest.size, ChannelCrypto.CHUNK)
                val chunk = rest.copyOfRange(0, count)
                rest = rest.copyOfRange(count, rest.size)
                val counter = outboundCounter++
                val encrypted = ChannelCrypto.sealFrame(channelKeys.d2h, counter, rest.isEmpty(), chunk)
                check(ws.send(buildJsonObject {
                    put("t", "f")
                    put("c", counter)
                    put("d", Codec.b64(encrypted))
                }.toString())) { "The secure host channel closed" }
            } while (rest.isNotEmpty())
        }
    }

    private suspend fun readLoop(opened: ChannelListener, ws: WebSocket, channelKeys: ChannelKeys) {
        var joined = ByteArrayOutputStream()
        try {
            while (true) {
                val packet = parse(opened.receive())
                when (packet.string("t")) {
                    "reject" -> throw IOException(packet.string("message") ?: "The host rejected this device")
                    "presence" -> {
                        if (packet["online"]?.jsonPrimitive?.booleanOrNull == false) {
                            throw IOException("The computer went offline on the Codync relay")
                        }
                    }
                    "pong" -> Unit
                    "f" -> {
                        val counter = packet["c"]?.jsonPrimitive?.longOrNull ?: error("Invalid encrypted frame counter")
                        check(counter == inboundCounter) { "Encrypted host frames arrived out of order" }
                        val cipher = Codec.unb64(packet.string("d") ?: error("Missing encrypted host frame"))
                        val (final, chunk) = ChannelCrypto.openFrame(channelKeys.h2d, counter, cipher)
                        inboundCounter++
                        joined.write(chunk)
                        check(joined.size() <= 16 * 1024 * 1024) { "Host message is too large" }
                        if (final) {
                            handleInner(parse(joined.toString(Charsets.UTF_8)))
                            joined = ByteArrayOutputStream()
                        }
                    }
                }
            }
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            if (socket === ws) {
                val message = error.message ?: "Host connection ended"
                _state.update { it.copy(kind = TransportKind.Offline, lastError = message) }
                _events.trySend(HostEvent.Error(message))
                socket = null
                keys = null
                pending.values.forEach { it.completeExceptionally(error) }
                pending.clear()
                heartbeat?.cancel()
                heartbeat = null
                targetComputer?.let(::startReconnectLoop)
            }
        }
    }

    private fun handleInner(message: JsonObject) {
        val rawId = message["id"]?.jsonPrimitive?.longOrNull ?: return
        message["ev"]?.let { eventJson ->
            val raw = eventJson.toString()
            val event = HostEventParser.parse(raw) ?: return
            when (event) {
                is HostEvent.Hello -> sinceRev = event.hello.rev
                is HostEvent.Rev -> sinceRev = event.rev
                is HostEvent.BotUpsert -> sinceRev = maxOf(sinceRev, event.bot.rev)
                is HostEvent.EntryUpsert -> sinceRev = maxOf(sinceRev, event.entry.rev)
                is HostEvent.BotDeleted -> sinceRev = maxOf(sinceRev, event.rev)
                is HostEvent.EntriesDeleted -> sinceRev = maxOf(sinceRev, event.rev)
                else -> Unit
            }
            _state.update { it.copy(rev = maxOf(it.rev, sinceRev)) }
            _events.trySend(event)
            return
        }
        message["end"]?.let {
            pending.remove(rawId)?.complete(message)
            return
        }
        pending.remove(rawId)?.complete(message)
    }

    private fun channelUrl(raw: String): String {
        val uri = URI(raw)
        require(uri.scheme == "http" || uri.scheme == "https") { "Unsupported direct host address" }
        val scheme = if (uri.scheme == "https") "wss" else "ws"
        return "$scheme://${uri.rawAuthority}/channel?v=1"
    }

    private fun parse(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

    private class ChannelListener : WebSocketListener() {
        private val opened = CompletableDeferred<WebSocket>()
        private val incoming = Channel<String>(Channel.UNLIMITED)

        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            incoming.trySend(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            opened.completeExceptionally(t)
            incoming.close(t)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            incoming.close(IOException("Host channel closed ($code): $reason"))
        }

        suspend fun awaitOpen(): WebSocket = opened.await()
        suspend fun receive(): String = incoming.receive()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
