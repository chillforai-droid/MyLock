package com.mylock.system.service

/**
 * Architectural contract for an OEM-signed privileged system service running in
 * `/system_ext/priv-app/` and communicating with AOSP BiometricService / TrustManagerService.
 *
 * Authentication Token Lifecycle:
 * 1. Lockscreen triggers `requestChallenge()` -> Gatekeeper/TEE generates 64-bit random challenge.
 * 2. Audio HAL captures isolated PCM into TEE/DSP secure buffer (non-readable by 3rd-party apps).
 * 3. Trusted speaker + liveness model evaluates in TEE/isolated process.
 * 4. On verified match, TEE signs a HardwareAuthToken (HAT) with HMAC-SHA256 using Keymaster's
 *    shared secret, bound to `userId`, `authenticatorId`, and `challenge`.
 * 5. `SystemVoiceUnlockService` delivers HAT to `BiometricService` / `TrustManagerService`
 *    with bounded trust tier (e.g., Class 1 Convenience or Class 2 Weak, never silently bypassing
 *    primary knowledge-factor PIN/Password on cold boot / Direct Boot).
 */
interface SystemVoiceUnlockServiceSpec {
    fun requestHardwareChallenge(userId: Int): ByteArray
    fun submitIsolatedVoiceVerification(userId: Int, challenge: ByteArray): SystemAuthTokenResult
    fun revokeVoiceTrust(userId: Int, reason: String)
}

sealed interface SystemAuthTokenResult {
    data class IssuedHardwareAuthToken(
        val tokenBytes: ByteArray,
        val authenticatorType: Int,
        val timestampNanos: Long,
        val biometricStrengthTier: String
    ) : SystemAuthTokenResult

    data class Denied(val errorCode: Int, val reason: String) : SystemAuthTokenResult
}
