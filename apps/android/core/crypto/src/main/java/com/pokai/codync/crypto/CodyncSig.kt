package com.pokai.codync.crypto

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

/** HTTP `Codync-Sig` request authentication (remote-relay §5). */
object CodyncSig {
    fun bodySha256B64(body: ByteArray): String = Codec.b64(Codec.sha256(body))

    fun canonical(
        method: String,
        authority: String,
        pathAndQuery: String,
        ts: Long,
        nonce: String,
        body: ByteArray,
    ): String {
        val bodyHash = bodySha256B64(body)
        return buildString {
            append("codync-sig-v1\n")
            append(method.uppercase())
            append('\n')
            append(authority.lowercase())
            append('\n')
            append(pathAndQuery)
            append('\n')
            append(ts)
            append('\n')
            append(nonce)
            append('\n')
            append(bodyHash)
        }
    }

    fun header(
        privateKey: Ed25519PrivateKeyParameters,
        ts: Long,
        nonce: String,
        canonical: String,
    ): String {
        val kid = Codec.b64(Keys.ed25519Public(privateKey))
        val sig = Codec.b64(Keys.ed25519Sign(privateKey, canonical.toByteArray(Charsets.UTF_8)))
        return "v=1,kid=$kid,ts=$ts,nonce=$nonce,sig=$sig"
    }

    fun headerLine(
        privateKey: Ed25519PrivateKeyParameters,
        method: String,
        authority: String,
        pathAndQuery: String,
        ts: Long,
        nonce: String,
        body: ByteArray = ByteArray(0),
    ): String {
        val input = canonical(method, authority, pathAndQuery, ts, nonce, body)
        return "Codync-Sig: ${header(privateKey, ts, nonce, input)}"
    }
}
