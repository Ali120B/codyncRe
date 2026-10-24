package com.pokai.codync.android

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.os.Build
import android.util.Base64
import com.pokai.codync.crypto.Codec
import com.pokai.codync.crypto.Keys
import com.pokai.codync.transport.DirectChannelTransport
import com.pokai.codync.transport.HostTransport
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persisted Ed25519 device identity for Codync-Sig (one device — no multi-account). */
class DeviceIdentity(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lock = Any()

    val seed: ByteArray
        get() = synchronized(lock) {
            val ciphertext = prefs.getString(KEY_SEED_CIPHER, null)
            val iv = prefs.getString(KEY_SEED_IV, null)
            if (ciphertext != null && iv != null) {
                val decrypted = runCatching {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
                    cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP))
                }.getOrNull()?.takeIf { it.size == 32 }
                if (decrypted != null) return@synchronized decrypted
            }

            // Migrate a prior debug build's raw preference once, then erase the plaintext copy.
            val legacy = prefs.getString(KEY_LEGACY_SEED, null)
                ?.let { runCatching { Codec.unb64(it) }.getOrNull() }
                ?.takeIf { it.size == 32 }
            val bytes = legacy ?: Keys.randomBytes(32)
            persistEncrypted(bytes)
            bytes
        }

    val privateKey: Ed25519PrivateKeyParameters
        get() = Keys.ed25519FromSeed(seed)

    val deviceName: String
        get() = prefs.getString(KEY_NAME, null)
            ?: "Android · ${Build.MODEL}".also { prefs.edit().putString(KEY_NAME, it).apply() }

    fun transport(): HostTransport = DirectChannelTransport(privateKey, deviceName)

    private fun persistEncrypted(seed: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(seed)
        prefs.edit()
            .putString(KEY_SEED_CIPHER, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_SEED_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .remove(KEY_LEGACY_SEED)
            .apply()
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "codync_device"
        private const val KEY_LEGACY_SEED = "ed25519_seed"
        private const val KEY_SEED_CIPHER = "ed25519_seed_ciphertext"
        private const val KEY_SEED_IV = "ed25519_seed_iv"
        private const val KEY_NAME = "device_name"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "codync.device.identity.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
