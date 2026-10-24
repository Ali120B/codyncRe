package com.pokai.codync.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

object Keys {
    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    fun ed25519FromSeed(seed: ByteArray): Ed25519PrivateKeyParameters {
        require(seed.size == 32)
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    fun ed25519Generate(): Ed25519PrivateKeyParameters {
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(random))
        return gen.generateKeyPair().private as Ed25519PrivateKeyParameters
    }

    fun ed25519Public(priv: Ed25519PrivateKeyParameters): ByteArray =
        priv.generatePublicKey().encoded

    fun ed25519Sign(priv: Ed25519PrivateKeyParameters, message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, priv)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun ed25519Verify(pub: ByteArray, message: ByteArray, sig: ByteArray): Boolean {
        return try {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(pub, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(sig)
        } catch (_: Exception) {
            false
        }
    }

    fun x25519FromSeed(seed: ByteArray): X25519PrivateKeyParameters {
        require(seed.size == 32)
        return X25519PrivateKeyParameters(seed, 0)
    }

    fun x25519Generate(): X25519PrivateKeyParameters {
        val gen = X25519KeyPairGenerator()
        gen.init(X25519KeyGenerationParameters(random))
        return gen.generateKeyPair().private as X25519PrivateKeyParameters
    }

    fun x25519Public(priv: X25519PrivateKeyParameters): ByteArray =
        priv.generatePublicKey().encoded

    fun x25519(priv: X25519PrivateKeyParameters, peerPub: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(priv)
        val out = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(peerPub, 0), out, 0)
        require(!out.all { it == 0.toByte() }) { "low-order public key" }
        return out
    }
}
