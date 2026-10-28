package com.pokai.codync.transport

import com.pokai.codync.crypto.CodyncSig
import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.Keys
import okhttp3.Request
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.net.URI

/** Authenticated Cloudflare device socket request, including the QR offer proof when pairing. */
internal object RelaySocketRequest {
    fun device(
        computerId: String,
        cloud: String,
        deviceKey: Ed25519PrivateKeyParameters,
        pairCode: String? = null,
    ): Request {
        val base = URI(cloud.trimEnd('/'))
        require(base.scheme == "https" || base.scheme == "http") { "Unsupported Codync cloud address" }
        require(!base.host.isNullOrBlank()) { "Invalid Codync cloud address" }
        val query = buildString {
            append("v=1")
            if (pairCode != null) {
                val rawCode = Codec.unb64n(pairCode, 16)
                val offerId = Codec.b64(Codec.sha256("codync/offer/v1".toByteArray(), rawCode).copyOf(16))
                append("&pair=").append(offerId)
            }
        }
        val pathAndQuery = "/v1/relay/device/$computerId?$query"
        val authority = buildString {
            append(base.host.lowercase())
            val defaultPort = if (base.scheme == "https") 443 else 80
            if (base.port >= 0 && base.port != defaultPort) append(':').append(base.port)
        }
        // Codync-Sig timestamps are Unix milliseconds (the cloud's signature window is in ms).
        val timestamp = System.currentTimeMillis()
        val nonce = Codec.b64(Keys.randomBytes(16))
        val signature = CodyncSig.header(
            deviceKey,
            timestamp,
            nonce,
            CodyncSig.canonical("GET", authority, pathAndQuery, timestamp, nonce, ByteArray(0)),
        )
        val socketScheme = if (base.scheme == "https") "wss" else "ws"
        val socketUrl = "$socketScheme://$authority$pathAndQuery"
        return Request.Builder()
            .url(socketUrl)
            .header("Codync-Sig", signature)
            .build()
    }
}
