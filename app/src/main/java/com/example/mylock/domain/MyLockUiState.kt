package com.example.mylock.domain

import com.example.mylock.core.model.ApiLevelCapabilityRow
import com.example.mylock.core.model.AppLockState
import com.example.mylock.core.model.AudioQualityReport
import com.example.mylock.core.model.ChallengePhrase
import com.example.mylock.core.model.DeviceCompatibilityReport
import com.example.mylock.core.model.SecurityAuditLog
import com.example.mylock.core.model.VerificationResult
import com.example.mylock.core.model.VoiceProfileSummary
import com.example.mylock.data.MyLockSettings

enum class EnrollmentStep {
    PRIVACY_DISCLOSURE,
    MICROPHONE_PERMISSION,
    PHRASE_AND_RECORDING,
    COMPLETED
}

data class EnrollmentUiState(
    val step: EnrollmentStep = EnrollmentStep.PRIVACY_DISCLOSURE,
    val privacyAccepted: Boolean = false,
    val customPhrase: String = "My Lock Verify Voice",
    val targetSamples: Int = 3,
    val acceptedEmbeddingsCount: Int = 0,
    val isRecording: Boolean = false,
    val isProcessing: Boolean = false,
    val lastQualityReport: AudioQualityReport? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

data class VerificationUiState(
    val activeChallenge: ChallengePhrase? = null,
    val challengeRemainingSeconds: Long = 0L,
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val lastResult: VerificationResult? = null,
    val fallbackMessage: String? = null,
    val remainingAttempts: Int = 5,
    val lockoutRemainingSeconds: Long = 0L
)

data class MyLockUiState(
    val settings: MyLockSettings = MyLockSettings(),
    val profileSummary: VoiceProfileSummary = VoiceProfileSummary(isEnrolled = false),
    val compatibilityReport: DeviceCompatibilityReport? = null,
    val apiMatrix: List<ApiLevelCapabilityRow> = emptyList(),
    val auditLogs: List<SecurityAuditLog> = emptyList(),
    val enrollment: EnrollmentUiState = EnrollmentUiState(),
    val verification: VerificationUiState = VerificationUiState(),
    val liveMicAmplitude: Float = 0f,
    val appLockState: AppLockState = AppLockState.UNLOCKED_COMPATIBILITY_MODE,
    val biometricFallbackStatus: String? = null,
    val modelInfoLabel: String = ""
)
