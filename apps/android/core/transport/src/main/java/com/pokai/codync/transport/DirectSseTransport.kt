package com.pokai.codync.transport

import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.CodyncSig
import com.pokai.codync.crypto.Keys
import com.pokai.codync.model.Computer
import com.pokai.codync.model.Hello
import com.pokai.codync.model.PairOffer
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Direct LAN / Tailscale transport: signed HTTP `POST /api/{method}` + SSE `/events`.
 * Matches desktop loopback/direct host API shape.
 */
class DirectSseTransport(
    private val deviceKey: Ed25519PrivateKeyParameters,
    private val deviceName: String = "Android",
    private val client: OkHttpClient = defaultClient(),
) : HostTransport {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _state = MutableStateFlow(ConnectionState())
    private val _events = MutableSharedFlow<HostEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private var eventSource: EventSource? = null
    private var baseUrl: String? = null
    private var sinceRev: Long = 0

    override val state: StateFlow<ConnectionState> = _state.asStateFlow()
    override val events: SharedFlow<HostEvent> = _events.asSharedFlow()

    override suspend fun pair(offer: PairOffer, deviceName: String): Result<Computer> {
        return runCatching { DirectPairingClient.pair(offer, deviceKey, deviceName) }
    }

    override suspend fun connect(computer: Computer): Result<Hello> {
        disconnectQuiet()
        val urls = computer.urls
        if (urls.isEmpty()) {
            return Result.failure(IOException("No host URLs on this computer"))
        }
        var lastError: Throwable? = null
        for (url in urls) {
            val base = url.trimEnd('/')
            try {
                val hello = rpc<Hello>(base, "hello", buildJsonObject {})
                baseUrl = base
                sinceRev = hello.rev
                _state.value = ConnectionState(
                    kind = TransportKind.Direct,
                    computer = computer,
                    hello = hello,
                    rev = hello.rev,
                    connectedAt = System.currentTimeMillis(),
                )
                _events.emit(HostEvent.Hello(hello))
                startEvents(base, sinceRev)
                return Result.success(hello)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        val err = lastError ?: IOException("Can't reach your computer")
        _state.update { it.copy(kind = TransportKind.Offline, lastError = err.message) }
        _events.emit(HostEvent.Error(err.message ?: "connect failed"))
        return Result.failure(err)
    }

    override suspend fun disconnect() {
        disconnectQuiet()
        _state.value = ConnectionState(kind = TransportKind.Offline)
    }

    override suspend fun sendUserMessage(
        botId: String,
        chatId: String,
        text: String,
        clientNonce: String,
    ): Result<Unit> = runCatching {
        val base = baseUrl ?: error("Not connected")
        rpc<JsonObject>(
            base,
            "send",
            buildJsonObject {
                put("botId", botId)
                put("text", text)
                put("clientNonce", clientNonce)
                if (chatId.isNotBlank() && chatId != "new") put("threadId", chatId)
                put("newChat", chatId.isBlank() || chatId == "new")
            },
        )
        Unit
    }

    override suspend fun respondPermission(entryId: String, optionId: String): Result<Unit> = runCatching {
        val base = baseUrl ?: error("Not connected")
        rpc<JsonObject>(
            base,
            "respondPermission",
            buildJsonObject {
                put("entryId", entryId)
                put("optionId", optionId)
            },
        )
        Unit
    }

    private fun startEvents(base: String, since: Long) {
        val clientId = Codec.b64(Keys.ed25519Public(deviceKey)).take(16)
        val path = "/events?since=$since&client=$clientId"
        val uri = URI.create(base)
        val authority = authorityOf(uri)
        val ts = System.currentTimeMillis()
        val nonce = Codec.b64(Keys.randomBytes(16))
        val sig = CodyncSig.header(
            deviceKey,
            ts,
            nonce,
            CodyncSig.canonical("GET", authority, path, ts, nonce, ByteArray(0)),
        )
        val request = Request.Builder()
            .url(base.trimEnd('/') + path)
            .header("Codync-Sig", sig)
            .header("Codync-Device-Name", deviceName)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        eventSource = EventSources.createFactory(client).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                    val event = HostEventParser.parse(data) ?: return
                    when (event) {
                        is HostEvent.Hello -> {
                            sinceRev = event.hello.rev
                            _state.update {
                                it.copy(hello = event.hello, rev = event.hello.rev, lastError = null)
                            }
                        }
                        is HostEvent.Rev -> {
                            sinceRev = event.rev
                            _state.update { it.copy(rev = event.rev) }
                        }
                        else -> Unit
                    }
                    _events.tryEmit(event)
                }

                override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                    val msg = t?.message ?: response?.message ?: "SSE ended"
                    _state.update { it.copy(lastError = msg) }
                    _events.tryEmit(HostEvent.Error(msg))
                }
            },
        )
    }

    private suspend inline fun <reified T> rpc(base: String, method: String, body: JsonObject): T {
        val path = "/api/$method"
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        val uri = URI.create(base)
        val authority = authorityOf(uri)
        val ts = System.currentTimeMillis()
        val nonce = Codec.b64(Keys.randomBytes(16))
        val sig = CodyncSig.header(
            deviceKey,
            ts,
            nonce,
            CodyncSig.canonical("POST", authority, path, ts, nonce, payload),
        )
        val request = Request.Builder()
            .url(base.trimEnd('/') + path)
            .header("Codync-Sig", sig)
            .header("Codync-Device-Name", deviceName)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(JSON))
            .build()
        val responseBody = await(request)
        return json.decodeFromString(responseBody)
    }

    private suspend fun await(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        if (cont.isActive) {
                            cont.resumeWith(
                                Result.failure(IOException("HTTP ${res.code}: ${body.take(200)}")),
                            )
                        }
                    } else if (cont.isActive) {
                        cont.resume(body)
                    }
                }
            }
        })
    }

    private fun disconnectQuiet() {
        eventSource?.cancel()
        eventSource = null
        baseUrl = null
    }

    private fun authorityOf(uri: URI): String = buildString {
        append(uri.host ?: "")
        if (uri.port > 0) append(':').append(uri.port)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        fun fromSeed(seed: ByteArray, deviceName: String = "Android"): DirectSseTransport =
            DirectSseTransport(Keys.ed25519FromSeed(seed), deviceName)
    }
}
