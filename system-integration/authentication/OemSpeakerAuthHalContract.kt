package com.mylock.system.authentication

/**
 * OEM Hardware Abstraction Layer (HAL) Contract for Speaker + Anti-Spoofing Execution.
 *
 * Designed to mirror Android HIDL/AIDL `android.hardware.biometrics` patterns:
 * - Enrollment & verification templates never leave TEE/StrongBox unencrypted.
 * - Acoustic anti-spoofing (AASIST/RawNet2 + SoundTrigger ultrasonic/liveness checks)
 *   must execute before emitting an authentication success signal.
 * - Must enforce 72-hour primary credential fallback (CDD Section 7.3.10) and
 *   5-attempt rate limiting in hardware/TEE persistent counter.
 */
interface OemSpeakerAuthHalContract {
    fun getHalInterfaceVersion(): String
    fun getBiometricStrengthClass(): Int // Must comply with Android CDD Class 1 / 2 / 3 tests
    fun enrollTemplateInTee(userId: Int, hatBytes: ByteArray): HalOperationStatus
    fun verifySpeakerInTee(userId: Int, challenge: Long): HalOperationStatus
    fun removeTemplateFromTee(userId: Int, templateId: Long): HalOperationStatus
}

data class HalOperationStatus(
    val success: Boolean,
    val errorCode: Int,
    val message: String
)
