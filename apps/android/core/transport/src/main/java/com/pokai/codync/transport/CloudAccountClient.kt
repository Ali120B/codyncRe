package com.pokai.codync.transport

import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.CodyncSig
import com.pokai.codync.crypto.Keys
import com.pokai.codync.crypto.PairingCrypto
import com.pokai.codync.model.CloudAccessStatus
import com.pokai.codync.model.CloudAccessTicket
import com.pokai.codync.model.CloudComputer
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Clerk-authenticated account APIs. Every device operation is also signed by this install key. */
class CloudAccountClient(
    private val baseUrl: String,
    private val deviceKey: Ed25519PrivateKeyParameters,
    private val deviceName: String,
    private val token: suspend () -> String,
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun registerDevice() {
        request("POST", "/v1/devices", buildJsonObject {
            put("name", deviceName)
            put("platform", "android")
        }, signed = true)
    }

    suspend fun computers(): List<CloudComputer> = request("GET", "/v1/computers", signed = true)
        .jsonObject["computers"]?.jsonArray
        ?.mapNotNull { value -> runCatching { json.decodeFromJsonElement<CloudComputer>(value) }.getOrNull() }
        .orEmpty()

    /** Commit the phone nonce, wait for the computer nonce, then reveal and derive the SAS. */
    suspend fun requestAccess(computer: CloudComputer): CloudAccessTicket {
        val hostSignKey = Codec.unb64n(computer.signKey, 32)
        require(Codec.computerId(hostSignKey) == computer.computerId) {
            "This computer's signing key doesn't match its ID"
        }
        val devicePublicKey = Keys.ed25519Public(deviceKey)
        val deviceNonce = Keys.randomBytes(32)
        val commit = Codec.b64(PairingCrypto.sasCommit(devicePublicKey, deviceNonce))
        val created = request("POST", "/v1/computers/${computer.computerId}/access-requests", buildJsonObject {
            put("commit", commit)
        }, signed = true).jsonObject
        val requestId = created["requestId"]?.jsonPrimitive?.content
            ?: error("Cloud didn't return an access request ID")
        val expiresAt = created["expiresAt"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: error("Cloud didn't return an access request expiry")

        var hostNonce: ByteArray? = null
        while (hostNonce == null) {
            val status = accessStatus(requestId)
            if (status.status != "pending") throw IOException("Access request ${status.status}")
            status.hostNonce?.let { encoded ->
                hostNonce = Codec.unb64n(encoded, 32)
            }
            if (hostNonce == null) {
                if (System.currentTimeMillis() >= expiresAt) throw IOException("Access request expired")
                delay(1_500)
            }
        }

        request("POST", "/v1/access-requests/$requestId/reveal", buildJsonObject {
            put("nonce", Codec.b64(deviceNonce))
        }, signed = true)
        return CloudAccessTicket(
            requestId = requestId,
            expiresAt = expiresAt,
            code = PairingCrypto.sasCode(hostSignKey, devicePublicKey, deviceNonce, hostNonce!!),
            computerId = computer.computerId,
            signKey = computer.signKey,
        )
    }

    suspend fun accessStatus(requestId: String): CloudAccessStatus = json.decodeFromJsonElement(
        request("GET", "/v1/access-requests/$requestId").jsonObject,
    )

    suspend fun cancelAccess(requestId: String) {
        request("DELETE", "/v1/access-requests/$requestId")
    }

    private suspend fun request(
        httpMethod: String,
        path: String,
        body: kotlinx.serialization.json.JsonObject? = null,
        signed: Boolean = false,
    ): JsonElement {
        val url = baseUrl.trimEnd('/') + path
        val uri = URI.create(url)
        val payload = body?.toString()?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token()}")
            .header("Accept", "application/json")
            .apply {
                if (body != null) header("Content-Type", "application/json")
                if (signed) {
                    val timestamp = System.currentTimeMillis()
                    val nonce = Codec.b64(Keys.randomBytes(16))
                    val authority = buildString {
                        append(uri.host.orEmpty())
                        if (uri.port > 0) append(':').append(uri.port)
                    }
                    header(
                        "Codync-Sig",
                        CodyncSig.header(
                            deviceKey,
                            timestamp,
                            nonce,
                            CodyncSig.canonical(httpMethod, authority, path, timestamp, nonce, payload),
                        ),
                    )
                }
                when (httpMethod) {
                    "GET" -> get()
                    "DELETE" -> delete(if (body == null) null else payload.toRequestBody(JSON))
                    else -> method(httpMethod, payload.toRequestBody(JSON))
                }
            }
            .build()
        val response = await(request)
        return runCatching { json.parseToJsonElement(response) }
            .getOrElse { throw IOException("Cloud returned invalid JSON", it) }
    }

    private suspend fun await(request: Request): String = suspendCancellableCoroutine { cont ->
        val call = http.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    val body = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        if (cont.isActive) cont.resumeWith(Result.failure(IOException("HTTP ${res.code}: ${body.take(200)}")))
                    } else if (cont.isActive) {
                        cont.resume(body)
                    }
                }
            }
        })
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private fun defaultClient() = OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
