package com.pokai.codync.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.HKDFParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ChannelKeys(val d2h: ByteArray, val h2d: ByteArray)

/** Signed-ephemeral handshake + frame AEAD (remote-relay §6). */
object ChannelCrypto {
    private const val FLAG_FINAL: Byte = 0x00
    private const val FLAG_MORE: Byte = 0x01
    const val CHUNK = 256 * 1024

    fun hs1Input(cidRaw: ByteArray, dk: ByteArray, ekD: ByteArray, n: ByteArray): ByteArray =
        "codync/hs1/v1".toByteArray() + cidRaw + dk + ekD + n

    fun transcriptHash(cidRaw: ByteArray, dk: ByteArray, ekD: ByteArray, n: ByteArray, ekH: ByteArray): ByteArray =
        Codec.sha256("codync/hs2/v1".toByteArray(), cidRaw, dk, ekD, n, ekH)

    fun channelKeys(shared: ByteArray, th: ByteArray): ChannelKeys {
        val d2h = ByteArray(32)
        val h2d = ByteArray(32)
        hkdfExpand(th, shared, "codync/d2h/v1".toByteArray(), d2h)
        hkdfExpand(th, shared, "codync/h2d/v1".toByteArray(), h2d)
        return ChannelKeys(d2h, h2d)
    }

    fun hkdfExpand(salt: ByteArray, ikm: ByteArray, info: ByteArray, out: ByteArray) {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(ikm, salt, info))
        hkdf.generateBytes(out, 0, out.size)
    }

    fun sealFrame(key: ByteArray, c: Long, last: Boolean, chunk: ByteArray): ByteArray {
        val plaintext = byteArrayOf(if (last) FLAG_FINAL else FLAG_MORE) + chunk
        return aeadSeal(key, frameNonce(c), frameAad(c), plaintext)
    }

    fun openFrame(key: ByteArray, c: Long, ciphertext: ByteArray): Pair<Boolean, ByteArray> {
        val plain = aeadOpen(key, frameNonce(c), frameAad(c), ciphertext)
        require(plain.isNotEmpty()) { "empty frame" }
        val flag = plain[0]
        val chunk = plain.copyOfRange(1, plain.size)
        return when (flag) {
            FLAG_FINAL -> true to chunk
            FLAG_MORE -> false to chunk
            else -> error("bad frame flag")
        }
    }

    private fun frameNonce(c: Long): ByteArray {
        val n = ByteArray(12)
        ByteBuffer.wrap(n, 4, 8).order(ByteOrder.BIG_ENDIAN).putLong(c)
        return n
    }

    private fun frameAad(c: Long): ByteArray {
        val counter = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(c).array()
        return "codync/frame/v1".toByteArray() + counter
    }

    private fun aeadSeal(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, n)
        return out
    }

    private fun aeadOpen(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        val m = cipher.doFinal(out, n)
        return out.copyOf(n + m)
    }
}
