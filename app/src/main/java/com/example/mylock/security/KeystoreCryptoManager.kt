package com.example.mylock.security

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import android.content.Context

interface CryptoProvider {
    fun encrypt(plaintext: ByteArray): EncryptedPayload
    fun decrypt(payload: EncryptedPayload): ByteArray
    fun isHardwareBacked(): Boolean
    fun deleteKey()
}

data class EncryptedPayload(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val hardwareBacked: Boolean
)

class AndroidKeystoreCryptoProvider(
    private val context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) : CryptoProvider {

    companion object {
        const val DEFAULT_KEY_ALIAS = "mylock_voice_vault_aes256_gcm"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val hasStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (hasStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                keyGen.init(builder.build())
                return keyGen.generateKey()
            } catch (_: Exception) {
                // StrongBox may be unavailable on some builds even if feature flag exists; fall back to TEE Keystore
                builder.setIsStrongBoxBacked(false)
            }
        }

        keyGen.init(builder.build())
        return keyGen.generateKey()
    }

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encrypted = cipher.doFinal(plaintext)
        return EncryptedPayload(
            iv = cipher.iv,
            ciphertext = encrypted,
            hardwareBacked = isHardwareBackedInternal(secretKey)
        )
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        val secretKey = getOrCreateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_BITS, payload.iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(payload.ciphertext)
    }

    override fun isHardwareBacked(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val key = (keyStore.getKey(keyAlias, null) as? SecretKey) ?: getOrCreateKey()
            isHardwareBackedInternal(key)
        } catch (_: Exception) {
            false
        }
    }

    private fun isHardwareBackedInternal(secretKey: SecretKey): Boolean {
        return try {
            val factory = SecretKeyFactory.getInstance(secretKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(secretKey, KeyInfo::class.java) as KeyInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT ||
                    keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
            } else {
                @Suppress("DEPRECATION")
                keyInfo.isInsideSecureHardware
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun deleteKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        } catch (_: Exception) {
        }
    }
}

object PinCryptoHasher {
    private const val ITERATIONS = 12_000
    private const val KEY_LENGTH_BITS = 256
    private val random = SecureRandom()

    fun hashPin(pin: String): String {
        val salt = ByteArray(16)
        random.nextBytes(salt)
        val hash = pbkdf2(pin.toCharArray(), salt)
        return "${salt.toHex()}:${hash.toHex()}"
    }

    fun verifyPin(pin: String, storedSaltAndHash: String?): Boolean {
        if (storedSaltAndHash.isNullOrBlank()) return false
        val parts = storedSaltAndHash.split(":")
        if (parts.size != 2) return false
        val salt = parts[0].hexToBytes() ?: return false
        val expectedHash = parts[1].hexToBytes() ?: return false
        val actualHash = pbkdf2(pin.toCharArray(), salt)
        return MessageDigest.isEqual(actualHash, expectedHash)
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun pbkdf2(chars: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(chars, salt, ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return try {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
