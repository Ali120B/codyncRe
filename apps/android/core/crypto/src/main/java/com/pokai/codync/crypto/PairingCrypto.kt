package com.pokai.codync.crypto

object PairingCrypto {
    /** ACL `offers[].id` / relay `pair=` for a 16-byte pairing code. */
    fun offerId(code: ByteArray): String {
        require(code.size == 16)
        return Codec.b64(Codec.sha256("codync/offer/v1".toByteArray(), code).copyOf(16))
    }

    fun sasCommit(dk: ByteArray, deviceNonce: ByteArray): ByteArray =
        Codec.sha256("codync/sascommit/v1".toByteArray(), dk, deviceNonce)

    fun sasCode(
        hostSignPub: ByteArray,
        dk: ByteArray,
        deviceNonce: ByteArray,
        hostNonce: ByteArray,
    ): String {
        val h = Codec.sha256("codync/sas/v2".toByteArray(), hostSignPub, dk, deviceNonce, hostNonce)
        val n = ((h[0].toInt() and 0xff) shl 24) or
            ((h[1].toInt() and 0xff) shl 16) or
            ((h[2].toInt() and 0xff) shl 8) or
            (h[3].toInt() and 0xff)
        val code = (n.toLong() and 0xffffffffL) % 1_000_000L
        return code.toString().padStart(6, '0')
    }

    fun claimInput(claimId: String, nonce: String, userId: String, computerId: String, boxKey: String): String =
        "codync/claim/v1\n$claimId\n$nonce\n$userId\n$computerId\n$boxKey"
}
