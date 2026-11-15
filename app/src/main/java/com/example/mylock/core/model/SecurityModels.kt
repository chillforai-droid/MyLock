package com.example.mylock.core.model

import kotlinx.serialization.Serializable

enum class ThemePreference {
    SYSTEM, DARK, LIGHT
}

enum class SecurityLevel(
    val threshold: Float,
    val labelKey: String
) {
    LOW(threshold = 0.72f, labelKey = "low"),
    STANDARD(threshold = 0.80f, labelKey = "standard"),
    HIGH(threshold = 0.87f, labelKey = "high")
}

enum class AppLanguage(
    val code: String,
    val nativeDisplayName: String
) {
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी (Hindi)");

    companion object {
        fun fromCode(code: String): AppLanguage =
            entries.firstOrNull { it.code == code } ?: ENGLISH
    }
}

@Serializable
data class VoiceProfile(
    val profileId: String,
    val phraseHashSha256: String,
    val maskedPhraseHint: String,
    val embedding: List<Float>,
    val embeddingDimension: Int,
    val modelId: String,
    val modelVersion: String,
    val sampleCount: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val hardwareKeystoreBacked: Boolean,
    val rawAudioStored: Boolean
)

data class VoiceProfileSummary(
    val isEnrolled: Boolean,
    val profileId: String? = null,
    val maskedPhraseHint: String = "",
    val sampleCount: Int = 0,
    val modelId: String = "",
    val modelVersion: String = "",
    val createdAtMillis: Long = 0L,
    val hardwareKeystoreBacked: Boolean = false
)

data class AudioQualityReport(
    val totalDurationMs: Long,
    val activeSpeechMs: Long,
    val snrDb: Float,
    val peakAmplitude: Float,
    val rmsEnergy: Float,
    val clippingRatio: Float,
    val isAcceptable: Boolean,
    val rejectionReason: QualityRejectionReason? = null
)

enum class QualityRejectionReason {
    NO_MICROPHONE,
    PERMISSION_DENIED,
    RECORDING_TOO_SHORT,
    INSUFFICIENT_SPEECH_ACTIVITY,
    TOO_NOISY,
    AUDIO_CLIPPING,
    SILENT_INPUT
}

data class ChallengePhrase(
    val id: String,
    val phraseText: String,
    val languageCode: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val nonce: String
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis > expiresAtMillis
}

data class AntiSpoofingReport(
    val passed: Boolean,
    val challengeValid: Boolean,
    val replayHashUnique: Boolean,
    val acousticReplayRiskScore: Float, // 0.0 = natural mic, 1.0 = high synthetic/playback risk
    val harmonicToNoiseRatioDb: Float,
    val spectralFlatnessVariance: Float,
    val highFreqCutoffDetected: Boolean,
    val rateLimitRemainingAttempts: Int,
    val lockoutRemainingSeconds: Long,
    val failureReasons: List<AntiSpoofFailureReason>
)

enum class AntiSpoofFailureReason {
    CHALLENGE_EXPIRED,
    CHALLENGE_MISSING,
    EXACT_REPLAY_DETECTED,
    ACOUSTIC_PLAYBACK_SUSPECTED,
    SYNTHETIC_UNIFORMITY_SUSPECTED,
    RATE_LIMITED,
    POOR_AUDIO_QUALITY
}

enum class VerificationStatus {
    VERIFIED_APP_LEVEL,
    REJECTED_SPEAKER_MISMATCH,
    REJECTED_ANTI_SPOOFING,
    REJECTED_POOR_QUALITY,
    REJECTED_NOT_ENROLLED,
    LOCKED_OUT_RATE_LIMIT,
    ERROR_HARDWARE_OR_PERMISSION
}

data class VerificationResult(
    val status: VerificationStatus,
    val similarityScore: Float,
    val thresholdUsed: Float,
    val confidenceLabel: String,
    val qualityReport: AudioQualityReport?,
    val antiSpoofingReport: AntiSpoofingReport?,
    val modelId: String,
    val modelVersion: String,
    val evaluatedAtMillis: Long = System.currentTimeMillis(),
    val diagnosticMessage: String
)

enum class LogCategory {
    ENROLLMENT,
    VERIFICATION_SUCCESS,
    VERIFICATION_FAILED,
    ANTI_SPOOF_ALERT,
    BIOMETRIC_FALLBACK,
    PROFILE_SECURITY,
    SYSTEM_COMPATIBILITY
}

data class SecurityAuditLog(
    val id: Long = 0L,
    val timestampMillis: Long,
    val category: LogCategory,
    val title: String,
    val details: String,
    val score: Float? = null,
    val outcomeSuccess: Boolean
)

enum class IntegrationTier {
    THIRD_PARTY_STANDARD_APP,
    DEVICE_OWNER_DPC,
    PRIVILEGED_SYSTEM_APP,
    PLATFORM_SIGNED_APP,
    CUSTOM_AOSP_BUILD,
    OEM_AUTHORIZED_PARTNER
}

data class SystemIntegrationStatus(
    val currentTier: IntegrationTier,
    val systemKeyguardUnlockSupported: Boolean,
    val oemVoiceServiceInstalled: Boolean,
    val oemVoiceServiceSignatureTrusted: Boolean,
    val compatibilityModeActive: Boolean,
    val exactRestrictionReason: String,
    val supportedScopeSummary: String
)

data class DeviceCompatibilityReport(
    val apiLevel: Int,
    val androidVersionRelease: String,
    val deviceManufacturer: String,
    val deviceModel: String,
    val hasMicrophoneHardware: Boolean,
    val microphonePermissionGranted: Boolean,
    val notificationPermissionRequired: Boolean,
    val notificationPermissionGranted: Boolean,
    val isKeyguardSecure: Boolean,
    val isDeviceLocked: Boolean,
    val biometricStrongAvailable: Boolean,
    val biometricWeakAvailable: Boolean,
    val deviceCredentialAvailable: Boolean,
    val strongBoxSupported: Boolean,
    val keystoreHardwareBackedPossible: Boolean,
    val isDeviceOwner: Boolean,
    val isProfileOwner: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val foregroundServiceMicPermissionRequired: Boolean,
    val systemIntegrationStatus: SystemIntegrationStatus
)

data class ApiLevelCapabilityRow(
    val apiLevel: Int,
    val androidVersion: String,
    val codename: String,
    val supportedCapabilities: String,
    val restrictedCapabilities: String,
    val requiredPermissions: String,
    val requiredSigningLevel: String,
    val oemDependencies: String,
    val deviceLimitations: String
)

enum class AppLockState {
    UNLOCKED_COMPATIBILITY_MODE,
    LOCKED_APP_GATE
}
