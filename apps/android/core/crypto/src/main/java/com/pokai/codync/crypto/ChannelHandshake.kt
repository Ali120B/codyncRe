package com.pokai.codync.crypto

import com.pokai.codync.model.Computer
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters

/** Device half of the signed ephemeral channel handshake used by direct hosts and the relay. */
class ChannelHandshake(
    computer: Computer,
    private val deviceKey: Ed25519PrivateKeyParameters,
    private val ephemeralKey: X25519PrivateKeyParameters = Keys.x25519Generate(),
    private val nonce: ByteArray = Keys.randomBytes(32),
) {
    private val hostSignKey = Codec.unb64n(computer.signKey, 32)
    private val computerIdRaw = Codec.computerIdRaw(hostSignKey)
    val devicePublicKey: ByteArray = Keys.ed25519Public(deviceKey)
    val ephemeralPublicKey: ByteArray = Keys.x25519Public(ephemeralKey)
    val signInput: ByteArray = ChannelCrypto.hs1Input(computerIdRaw, devicePublicKey, ephemeralPublicKey, nonce)

    init {
        require(computer.id == Codec.b64(computerIdRaw)) { "Pinned computer ID doesn't match its host key" }
        require(nonce.size == 32)
    }

    fun nonceBase64(): String = Codec.b64(nonce)

    fun finish(hostEphemeralPublicKey: ByteArray, hostSignature: ByteArray): ChannelKeys {
        require(hostEphemeralPublicKey.size == 32) { "Invalid host ephemeral key" }
        val transcript = ChannelCrypto.transcriptHash(
            computerIdRaw,
            devicePublicKey,
            ephemeralPublicKey,
            nonce,
            hostEphemeralPublicKey,
        )
        require(Keys.ed25519Verify(hostSignKey, transcript, hostSignature)) { "Host identity signature is invalid" }
        val shared = Keys.x25519(ephemeralKey, hostEphemeralPublicKey)
        require(shared.any { it != 0.toByte() }) { "Invalid channel shared secret" }
        return ChannelCrypto.channelKeys(shared, transcript)
    }
}
