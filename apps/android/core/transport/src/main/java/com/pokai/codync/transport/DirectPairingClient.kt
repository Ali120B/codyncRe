package com.pokai.codync.transport

import com.pokai.codync.crypto.ChannelCrypto
import com.pokai.codync.crypto.ChannelHandshake
import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.Keys
import com.pokai.codync.model.Computer
import com.pokai.codync.model.PairOffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit

/** One-time QR pairing over the host's pinned, end-to-end encrypted direct channel. */
internal object DirectPairingClient {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    suspend fun pair(
        offer: PairOffer,
        deviceKey: Ed25519PrivateKeyParameters,
        deviceName: String,
    ): Computer {
        val hostKey = Codec.unb64n(offer.signKey, 32)
        require(Codec.b64(Codec.computerIdRaw(hostKey)) == offer.computerId) {
            "This pairing code doesn't match its host identity. Scan a fresh QR code."
        }
        require(Codec.unb64n(offer.boxKey ?: error("Pairing code is missing its mailbox key"), 32).size == 32)
        require(Codec.unb64n(offer.code, 16).size == 16) { "This pairing code is damaged. Scan a fresh QR code." }
        val base = Computer(
            id = offer.computerId,
            name = offer.name.ifBlank { "Computer" },
            signKey = offer.signKey,
            boxKey = offer.boxKey,
            urls = offer.urls.filter(::isDirectUrl),
            cloud = offer.relay,
            device = deviceName,
        )
        if (base.urls.isEmpty() && base.cloud.isNullOrBlank()) {
            throw IOException("This pairing code has no direct address or Codync Cloud relay.")
        }

        var failure: Throwable? = null
        for (url in base.urls) {
            try {
                return pairDirect(base.copy(urls = listOf(url)), offer, deviceKey, deviceName)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failure = error
            }
        }
        if (!offer.relay.isNullOrBlank()) {
            try {
                return pairRelay(base, offer, deviceKey, deviceName)
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                failure = error
            }
        }
        throw IOException(failure?.message ?: "Can't reach the computer on this network", failure)
    }

    private suspend fun pairDirect(
        computer: Computer,
        offer: PairOffer,
        deviceKey: Ed25519PrivateKeyParameters,
        deviceName: String,
    ): Computer = withTimeout(30_000) {
        val listener = PairSocket()
        val socket = client.newWebSocket(Request.Builder().url(channelUrl(computer.urls.single())).build(), listener)
        pairOnSocket(computer, offer, deviceKey, deviceName, listener, socket, waitForRelayPresence = false)
    }

    private suspend fun pairRelay(
        computer: Computer,
        offer: PairOffer,
        deviceKey: Ed25519PrivateKeyParameters,
        deviceName: String,
    ): Computer = withTimeout(30_000) {
        val listener = PairSocket()
        val request = RelaySocketRequest.device(computer.id, offer.relay!!, deviceKey, offer.code)
        val socket = client.newWebSocket(request, listener)
        pairOnSocket(computer, offer, deviceKey, deviceName, listener, socket, waitForRelayPresence = true)
    }

    private suspend fun pairOnSocket(
        computer: Computer,
        offer: PairOffer,
        deviceKey: Ed25519PrivateKeyParameters,
        deviceName: String,
        listener: PairSocket,
        socket: WebSocket,
        waitForRelayPresence: Boolean,
    ): Computer {
        return try {
            listener.awaitOpen()
            if (waitForRelayPresence) {
                var online = false
                while (!online) {
                    val presence = parse(listener.receive())
                    when (presence.string("t")) {
                        "reject" -> throw IOException(presence.string("message") ?: "The relay refused pairing")
                        "presence" -> online = presence["online"]?.jsonPrimitive?.content == "true"
                    }
                }
            }
            val handshake = ChannelHandshake(computer, deviceKey)
            val hello = buildJsonObject {
                put("t", "hello")
                put("v", 1)
                put("dk", Codec.b64(handshake.devicePublicKey))
                put("ek", Codec.b64(handshake.ephemeralPublicKey))
                put("n", handshake.nonceBase64())
                put("sig", Codec.b64(Keys.ed25519Sign(deviceKey, handshake.signInput)))
                put("pair", true)
            }
            check(socket.send(hello.toString())) { "Couldn't start secure pairing" }

            var channelKeys: com.pokai.codync.crypto.ChannelKeys? = null
            while (channelKeys == null) {
                val message = parse(listener.receive())
                when (message.string("t")) {
                    "reject" -> throw IOException(message.string("message") ?: "The computer rejected pairing")
                    "welcome" -> {
                        val hostEphemeral = Codec.unb64n(message.string("ek") ?: error("Missing host key"), 32)
                        val hostSignature = Codec.unb64n(message.string("sig") ?: error("Missing host signature"), 64)
                        channelKeys = handshake.finish(hostEphemeral, hostSignature)
                    }
                }
            }
            val keys = checkNotNull(channelKeys)

            val request = buildJsonObject {
                put("id", 1)
                put("m", "pair")
                put("b", buildJsonObject {
                    put("code", offer.code)
                    put("name", deviceName)
                    put("platform", "android")
                })
            }.toString().toByteArray(Charsets.UTF_8)
            val encrypted = ChannelCrypto.sealFrame(keys.d2h, 0, true, request)
            check(socket.send(buildJsonObject {
                put("t", "f")
                put("c", 0)
                put("d", Codec.b64(encrypted))
            }.toString())) { "Couldn't send pairing confirmation" }

            val answer = receiveResponse(listener, keys.h2d)
            val errorValue = answer["err"]
            if (errorValue != null) {
                val detail = errorValue.jsonObject["message"]?.jsonPrimitive?.content
                throw IOException(detail ?: "The computer rejected this pairing code")
            }
            val result = answer["ok"]?.jsonObject ?: error("Malformed pairing response")
            val returnedId = result.string("computerId")
            require(returnedId == computer.id) { "The pairing response came from a different computer" }
            computer.copy(name = result.string("name")?.ifBlank { null } ?: computer.name)
        } finally {
            socket.close(1000, "pair complete")
        }
    }

    private suspend fun receiveResponse(listener: PairSocket, inboundKey: ByteArray): JsonObject {
        var expectedCounter = 0L
        val message = ByteArrayOutputStream()
        while (true) {
            val wire = parse(listener.receive())
            if (wire.string("t") == "reject") {
                throw IOException(wire.string("message") ?: "The computer rejected pairing")
            }
            if (wire.string("t") != "f") continue
            val counter = wire["c"]?.jsonPrimitive?.longOrNull ?: error("Invalid encrypted frame counter")
            require(counter == expectedCounter) { "Encrypted pairing frames arrived out of order" }
            val encrypted = Codec.unb64(wire.string("d") ?: error("Missing encrypted frame"))
            val (final, chunk) = ChannelCrypto.openFrame(inboundKey, counter, encrypted)
            message.write(chunk)
            require(message.size() <= 1_048_576) { "Pairing response is too large" }
            expectedCounter++
            if (final) {
                val response = parse(message.toString(Charsets.UTF_8))
                require(response["id"]?.jsonPrimitive?.longOrNull == 1L) { "Unexpected pairing response" }
                return response
            }
        }
    }

    private fun channelUrl(raw: String): String {
        val uri = URI(raw)
        require(uri.scheme == "http" || uri.scheme == "https") { "Unsupported direct computer address" }
        val scheme = if (uri.scheme == "https") "wss" else "ws"
        return buildString {
            append(scheme).append("://").append(uri.rawAuthority)
            append("/channel?v=1")
        }
    }

    private fun isDirectUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun parse(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.content

    private class PairSocket : WebSocketListener() {
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
            incoming.close(IOException("Pairing channel closed ($code): $reason"))
        }

        suspend fun awaitOpen(): WebSocket = opened.await()
        suspend fun receive(): String = incoming.receive()
    }
}
