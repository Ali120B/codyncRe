package com.pokai.codync.crypto

import java.security.MessageDigest
import java.util.Base64

/** URL-safe base64 without padding — the wire encoding for all Codync binary fields. */
object Codec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun b64(bytes: ByteArray): String = encoder.encodeToString(bytes)

    fun unb64(s: String): ByteArray = decoder.decode(s)

    fun unb64n(s: String, n: Int): ByteArray {
        val out = unb64(s)
        require(out.size == n) { "expected $n bytes, got ${out.size}" }
        return out
    }

    fun sha256(vararg parts: ByteArray): ByteArray {
        val d = MessageDigest.getInstance("SHA-256")
        parts.forEach { d.update(it) }
        return d.digest()
    }

    fun computerIdRaw(signPub: ByteArray): ByteArray = sha256(signPub).copyOf(16)

    fun computerId(signPub: ByteArray): String = b64(computerIdRaw(signPub))
}
