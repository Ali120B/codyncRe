package com.pokai.codync.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter

/** Opens the host-sealed push alert format shared with the iOS notification service. */
object PushCrypto {
    fun open(sealed: String, computerId: String, pushPrivate: ByteArray): ByteArray {
        val raw = Codec.unb64(sealed)
        require(raw.size > 32 + 16) { "Malformed push payload" }
        require(pushPrivate.size == 32) { "Invalid push key" }
        val cid = Codec.unb64n(computerId, 16)
        val epk = raw.copyOfRange(0, 32)
        val ciphertext = raw.copyOfRange(32, raw.size)
        val pushPublic = Keys.x25519Public(Keys.x25519FromSeed(pushPrivate))
        val shared = Keys.x25519(Keys.x25519FromSeed(pushPrivate), epk)
        val salt = "codync/push/v1".toByteArray() + cid + pushPublic + epk
        val key = ByteArray(32)
        HKDFBytesGenerator(SHA256Digest()).apply {
            init(HKDFParameters(shared, salt, "codync/push-key/v1".toByteArray()))
            generateBytes(key, 0, key.size)
        }
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, ByteArray(12), cid))
        val plain = ByteArray(cipher.getOutputSize(ciphertext.size))
        val written = cipher.processBytes(ciphertext, 0, ciphertext.size, plain, 0)
        val final = cipher.doFinal(plain, written)
        return plain.copyOf(written + final)
    }
}
