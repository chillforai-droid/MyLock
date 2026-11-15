package com.example.mylock.security

import android.content.Context
import com.example.mylock.core.model.VoiceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

class VoiceProfileVault(
    private val context: Context,
    private val cryptoProvider: CryptoProvider
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val vaultDir: File
        get() = File(context.filesDir, "secure_vault").apply {
            if (!exists()) mkdirs()
        }

    private val profileFile: File
        get() = File(vaultDir, "voice_profile_v1.enc")

    suspend fun saveProfile(profile: VoiceProfile): Boolean = withContext(Dispatchers.IO) {
        val serialized = json.encodeToString(profile).toByteArray(Charsets.UTF_8)
        val encrypted = cryptoProvider.encrypt(serialized)
        serialized.fill(0)

        val outBytes = ByteArrayOutputStream().use { baos ->
            DataOutputStream(baos).use { dos ->
                dos.writeInt(1) // binary format version
                dos.writeBoolean(encrypted.hardwareBacked)
                dos.writeInt(encrypted.iv.size)
                dos.write(encrypted.iv)
                dos.writeInt(encrypted.ciphertext.size)
                dos.write(encrypted.ciphertext)
            }
            baos.toByteArray()
        }
        profileFile.writeBytes(outBytes)
        true
    }

    suspend fun loadProfile(): VoiceProfile? = withContext(Dispatchers.IO) {
        if (!profileFile.exists()) return@withContext null
        return@withContext try {
            val raw = profileFile.readBytes()
            val payload = DataInputStream(ByteArrayInputStream(raw)).use { dis ->
                val version = dis.readInt()
                if (version != 1) return@withContext null
                val hwBacked = dis.readBoolean()
                val ivLen = dis.readInt()
                val iv = ByteArray(ivLen).also { dis.readFully(it) }
                val ctLen = dis.readInt()
                val ct = ByteArray(ctLen).also { dis.readFully(it) }
                EncryptedPayload(iv = iv, ciphertext = ct, hardwareBacked = hwBacked)
            }
            val decrypted = cryptoProvider.decrypt(payload)
            val profile = json.decodeFromString<VoiceProfile>(decrypted.toString(Charsets.UTF_8))
            decrypted.fill(0)
            profile
        } catch (_: Exception) {
            null
        }
    }

    suspend fun deleteAllBiometricData(): Boolean = withContext(Dispatchers.IO) {
        if (profileFile.exists()) {
            val len = profileFile.length().toInt().coerceAtLeast(0)
            if (len > 0) {
                profileFile.writeBytes(ByteArray(len)) // zero-wipe before unlink
            }
            profileFile.delete()
        }
        cryptoProvider.deleteKey()
        !profileFile.exists()
    }

    fun hasEncryptedProfileFile(): Boolean = profileFile.exists() && profileFile.length() > 0L
}
