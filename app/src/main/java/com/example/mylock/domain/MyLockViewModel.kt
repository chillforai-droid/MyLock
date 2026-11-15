package com.example.mylock.domain

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.mylock.authentication.BiometricFallbackOutcome
import com.example.mylock.core.di.MyLockContainer
import com.example.mylock.core.model.AppLanguage
import com.example.mylock.core.model.AppLockState
import com.example.mylock.core.model.LogCategory
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.core.model.ThemePreference
import com.example.mylock.core.model.VerificationStatus
import com.example.mylock.core.model.VoiceProfileSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MyLockViewModel(
    private val container: MyLockContainer
) : ViewModel() {

    private val enrollmentSamplesBuffer = mutableListOf<FloatArray>()
    private val enrollmentState = MutableStateFlow(EnrollmentUiState())
    private val verificationState = MutableStateFlow(VerificationUiState())
    private val compatReportState = MutableStateFlow(container.compatibilityInspector.inspect())
    private val appLockState = MutableStateFlow(AppLockState.UNLOCKED_COMPATIBILITY_MODE)
    private val biometricStatusMessage = MutableStateFlow<String?>(null)

    private var challengeCountdownJob: Job? = null
    private var initialAppLockEvaluated = false

    val uiState: StateFlow<MyLockUiState> = combine(
        container.settingsRepository.settingsFlow,
        container.database.voiceProfileMetadataDao().observeActiveMetadata(),
        container.auditRepository.logs,
        enrollmentState,
        verificationState,
        compatReportState,
        container.audioCaptureSource.liveAmplitude,
        appLockState,
        biometricStatusMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val settings = values[0] as com.example.mylock.data.MyLockSettings
        val profileMeta = values[1] as com.example.mylock.database.VoiceProfileMetadataEntity?
        @Suppress("UNCHECKED_CAST")
        val logs = values[2] as List<com.example.mylock.core.model.SecurityAuditLog>
        val enroll = values[3] as EnrollmentUiState
        val verify = values[4] as VerificationUiState
        val compat = values[5] as com.example.mylock.core.model.DeviceCompatibilityReport
        val amp = values[6] as Float
        val lockState = values[7] as AppLockState
        val bioMsg = values[8] as String?

        if (!initialAppLockEvaluated) {
            initialAppLockEvaluated = true
            if (settings.appLockEnabled) {
                appLockState.value = AppLockState.LOCKED_APP_GATE
            }
        }

        val summary = if (profileMeta != null && container.voiceProfileVault.hasEncryptedProfileFile()) {
            VoiceProfileSummary(
                isEnrolled = true,
                profileId = profileMeta.profileId,
                maskedPhraseHint = profileMeta.maskedPhraseHint,
                sampleCount = profileMeta.sampleCount,
                modelId = profileMeta.modelId,
                modelVersion = profileMeta.modelVersion,
                createdAtMillis = profileMeta.createdAtMillis,
                hardwareKeystoreBacked = profileMeta.hardwareKeystoreBacked
            )
        } else {
            VoiceProfileSummary(isEnrolled = false)
        }

        MyLockUiState(
            settings = settings,
            profileSummary = summary,
            compatibilityReport = compat,
            apiMatrix = com.example.mylock.compatibility.AndroidVersionCompatibilityMatrix.getFullMatrix(),
            auditLogs = logs,
            enrollment = enroll.copy(
                customPhrase = if (enroll.customPhrase.isBlank()) settings.customVoicePhrase else enroll.customPhrase
            ),
            verification = verify.copy(
                remainingAttempts = container.rateLimiter.remainingAttempts(),
                lockoutRemainingSeconds = container.rateLimiter.remainingLockoutSeconds()
            ),
            liveMicAmplitude = amp,
            appLockState = lockState,
            biometricFallbackStatus = bioMsg,
            modelInfoLabel = "${container.speakerEmbeddingExtractor.modelId} v${container.speakerEmbeddingExtractor.modelVersion}"
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MyLockUiState(
            compatibilityReport = compatReportState.value,
            apiMatrix = com.example.mylock.compatibility.AndroidVersionCompatibilityMatrix.getFullMatrix(),
            modelInfoLabel = "${container.speakerEmbeddingExtractor.modelId} v${container.speakerEmbeddingExtractor.modelVersion}"
        )
    )

    init {
        refreshCompatibilityAndChallenge()
    }

    fun refreshCompatibilityAndChallenge() {
        compatReportState.value = container.compatibilityInspector.inspect()
        issueNewVerificationChallenge()
    }

    fun refreshCompatibility() {
        val report = container.compatibilityInspector.inspect()
        compatReportState.value = report
        viewModelScope.launch {
            if (uiState.value.settings.auditLoggingEnabled) {
                container.auditRepository.recordEvent(
                    category = LogCategory.SYSTEM_COMPATIBILITY,
                    title = "Device Compatibility Audited",
                    details = "API ${report.apiLevel} (${report.androidVersionRelease}), BiometricStrong=${report.biometricStrongAvailable}, KeyguardSecure=${report.isKeyguardSecure}, SystemUnlock=${report.systemIntegrationStatus.systemKeyguardUnlockSupported}.",
                    outcomeSuccess = true
                )
            }
        }
    }

    // --- Enrollment Workflow ---

    fun startFreshEnrollment(reEnroll: Boolean = false) {
        enrollmentSamplesBuffer.clear()
        val hasMicPerm = container.audioCaptureSource.hasRecordPermission()
        val currentPhrase = uiState.value.settings.customVoicePhrase
        enrollmentState.value = EnrollmentUiState(
            step = if (reEnroll && hasMicPerm) EnrollmentStep.PHRASE_AND_RECORDING else EnrollmentStep.PRIVACY_DISCLOSURE,
            privacyAccepted = reEnroll,
            customPhrase = currentPhrase,
            targetSamples = 3,
            acceptedEmbeddingsCount = 0
        )
    }

    fun acceptEnrollmentPrivacy() {
        val hasMicPerm = container.audioCaptureSource.hasRecordPermission()
        enrollmentState.update {
            it.copy(
                privacyAccepted = true,
                step = if (hasMicPerm) EnrollmentStep.PHRASE_AND_RECORDING else EnrollmentStep.MICROPHONE_PERMISSION,
                errorMessage = null
            )
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        compatReportState.value = container.compatibilityInspector.inspect()
        enrollmentState.update {
            if (granted) {
                it.copy(
                    step = EnrollmentStep.PHRASE_AND_RECORDING,
                    errorMessage = null
                )
            } else {
                it.copy(
                    errorMessage = "Microphone permission was denied. Grant RECORD_AUDIO in Android Settings or try again."
                )
            }
        }
    }

    fun updateEnrollmentPhrase(phrase: String) {
        enrollmentState.update { it.copy(customPhrase = phrase, errorMessage = null) }
    }

    fun startEnrollmentRecording() {
        val current = enrollmentState.value
        val words = current.customPhrase.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size < 2 || current.customPhrase.trim().length < 6) {
            enrollmentState.update {
                it.copy(errorMessage = "Please enter a custom phrase of at least 2 words (6+ characters).")
            }
            return
        }
        viewModelScope.launch {
            val started = container.audioCaptureSource.startCapture(uiState.value.settings.micSensitivity)
            if (started) {
                enrollmentState.update {
                    it.copy(isRecording = true, errorMessage = null, statusMessage = "Listening... Speak your phrase clearly.")
                }
            } else {
                val report = container.compatibilityInspector.inspect()
                compatReportState.value = report
                val err = if (!report.hasMicrophoneHardware) {
                    "No microphone hardware detected on this device."
                } else if (!report.microphonePermissionGranted) {
                    "Microphone permission not granted."
                } else {
                    "Unable to initialize AudioRecord hardware."
                }
                enrollmentState.update { it.copy(isRecording = false, errorMessage = err) }
            }
        }
    }

    fun stopEnrollmentRecording() {
        viewModelScope.launch {
            enrollmentState.update { it.copy(isRecording = false, isProcessing = true) }
            val capture = container.audioCaptureSource.stopCapture()
            val eval = container.voiceAuthManager.evaluateEnrollmentSample(capture)

            if (!eval.accepted || eval.embedding == null) {
                enrollmentState.update {
                    it.copy(
                        isProcessing = false,
                        lastQualityReport = eval.qualityReport,
                        errorMessage = eval.message,
                        statusMessage = null
                    )
                }
                return@launch
            }

            enrollmentSamplesBuffer.add(eval.embedding)
            val count = enrollmentSamplesBuffer.size
            val target = enrollmentState.value.targetSamples

            if (count >= target) {
                val phrase = enrollmentState.value.customPhrase.trim()
                container.settingsRepository.setCustomVoicePhrase(phrase)
                container.voiceAuthManager.finalizeEnrollment(
                    phrase = phrase,
                    sampleEmbeddings = enrollmentSamplesBuffer.toList(),
                    rawAudioStored = uiState.value.settings.storeRawAudioSamples,
                    auditLoggingEnabled = uiState.value.settings.auditLoggingEnabled
                )
                enrollmentSamplesBuffer.clear()
                enrollmentState.update {
                    it.copy(
                        isProcessing = false,
                        acceptedEmbeddingsCount = count,
                        lastQualityReport = eval.qualityReport,
                        step = EnrollmentStep.COMPLETED,
                        statusMessage = eval.message,
                        errorMessage = null
                    )
                }
            } else {
                enrollmentState.update {
                    it.copy(
                        isProcessing = false,
                        acceptedEmbeddingsCount = count,
                        lastQualityReport = eval.qualityReport,
                        statusMessage = "${eval.message} (${count}/$target completed)",
                        errorMessage = null
                    )
                }
            }
        }
    }

    // --- Verification & Anti-Spoofing ---

    fun issueNewVerificationChallenge() {
        val lang = uiState.value.settings.language
        val challenge = container.challengePhraseManager.issueChallenge(lang)
        verificationState.update {
            it.copy(
                activeChallenge = challenge,
                challengeRemainingSeconds = 30L,
                fallbackMessage = null
            )
        }
        challengeCountdownJob?.cancel()
        challengeCountdownJob = viewModelScope.launch {
            while (isActive) {
                val active = verificationState.value.activeChallenge ?: break
                val remMs = (active.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                val remSec = (remMs + 999L) / 1000L
                val lockoutSec = container.rateLimiter.remainingLockoutSeconds()
                verificationState.update {
                    it.copy(
                        challengeRemainingSeconds = remSec,
                        lockoutRemainingSeconds = lockoutSec,
                        remainingAttempts = container.rateLimiter.remainingAttempts()
                    )
                }
                if (remMs == 0L && lockoutSec == 0L) break
                delay(500L)
            }
        }
    }

    fun startVerificationCapture() {
        val current = verificationState.value
        val activeChallenge = current.activeChallenge
        if (activeChallenge == null || activeChallenge.isExpired()) {
            issueNewVerificationChallenge()
        }
        viewModelScope.launch {
            val started = container.audioCaptureSource.startCapture(uiState.value.settings.micSensitivity)
            if (started) {
                verificationState.update {
                    it.copy(isRecording = true, fallbackMessage = null)
                }
            } else {
                compatReportState.value = container.compatibilityInspector.inspect()
                verificationState.update {
                    it.copy(
                        isRecording = false,
                        fallbackMessage = "Microphone unavailable or RECORD_AUDIO permission not granted."
                    )
                }
            }
        }
    }

    fun stopAndVerifySpeaker() {
        viewModelScope.launch {
            verificationState.update { it.copy(isRecording = false, isAnalyzing = true) }
            val capture = container.audioCaptureSource.stopCapture()
            val result = container.voiceAuthManager.verifySpeaker(
                capture = capture,
                challenge = verificationState.value.activeChallenge,
                securityLevel = uiState.value.settings.securityLevel,
                auditLoggingEnabled = uiState.value.settings.auditLoggingEnabled
            )
            verificationState.update {
                it.copy(
                    isAnalyzing = false,
                    lastResult = result,
                    remainingAttempts = container.rateLimiter.remainingAttempts(),
                    lockoutRemainingSeconds = container.rateLimiter.remainingLockoutSeconds()
                )
            }
            if (result.status == VerificationStatus.VERIFIED_APP_LEVEL &&
                appLockState.value == AppLockState.LOCKED_APP_GATE
            ) {
                appLockState.value = AppLockState.UNLOCKED_COMPATIBILITY_MODE
            }
        }
    }

    // --- Official Android Biometric Fallback & App-Level Gate ---

    fun launchSystemBiometricFallback(activity: FragmentActivity) {
        container.biometricFallbackManager.authenticate(
            activity = activity,
            title = "My Lock Safe Compatibility Auth",
            subtitle = "Authenticate using Android OS Biometric / Device Credential",
            negativeButtonText = "Cancel"
        ) { outcome ->
            viewModelScope.launch {
                val msg = when (outcome) {
                    BiometricFallbackOutcome.Authenticated -> {
                        appLockState.value = AppLockState.UNLOCKED_COMPATIBILITY_MODE
                        if (uiState.value.settings.auditLoggingEnabled) {
                            container.auditRepository.recordEvent(
                                category = LogCategory.BIOMETRIC_FALLBACK,
                                title = "System Biometric / Credential Passed",
                                details = "User authenticated via official Android BiometricPrompt.",
                                outcomeSuccess = true
                            )
                        }
                        "Authenticated via official Android BiometricPrompt (App-Level)."
                    }
                    BiometricFallbackOutcome.Cancelled -> "Biometric prompt cancelled by user."
                    is BiometricFallbackOutcome.Unavailable -> {
                        if (uiState.value.settings.auditLoggingEnabled) {
                            container.auditRepository.recordEvent(
                                category = LogCategory.BIOMETRIC_FALLBACK,
                                title = "System Biometric Unavailable",
                                details = outcome.reason,
                                outcomeSuccess = false
                            )
                        }
                        outcome.reason
                    }
                    is BiometricFallbackOutcome.Failed -> "Biometric authentication failed (${outcome.errorCode}): ${outcome.message}"
                }
                biometricStatusMessage.value = msg
                verificationState.update { it.copy(fallbackMessage = msg) }
            }
        }
    }

    fun verifyBackupPinForUnlock(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val valid = container.settingsRepository.verifyBackupPin(pin)
            if (valid) {
                appLockState.value = AppLockState.UNLOCKED_COMPATIBILITY_MODE
                if (uiState.value.settings.auditLoggingEnabled) {
                    container.auditRepository.recordEvent(
                        category = LogCategory.BIOMETRIC_FALLBACK,
                        title = "App Backup PIN Verified",
                        details = "App-level compatibility vault unlocked via PBKDF2 backup PIN.",
                        outcomeSuccess = true
                    )
                }
            } else if (uiState.value.settings.auditLoggingEnabled) {
                container.auditRepository.recordEvent(
                    category = LogCategory.BIOMETRIC_FALLBACK,
                    title = "App Backup PIN Rejected",
                    details = "Invalid backup PIN attempt.",
                    outcomeSuccess = false
                )
            }
            onResult(valid)
        }
    }

    fun lockAppGate() {
        appLockState.value = AppLockState.LOCKED_APP_GATE
        biometricStatusMessage.value = null
        issueNewVerificationChallenge()
    }

    // --- Settings Actions ---

    fun setVoiceAuthEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setVoiceAuthEnabled(enabled) }
    }

    fun setCustomPhrase(phrase: String) {
        viewModelScope.launch { container.settingsRepository.setCustomVoicePhrase(phrase) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.settingsRepository.setLanguage(language)
            issueNewVerificationChallenge()
        }
    }

    fun setThemePreference(theme: ThemePreference) {
        viewModelScope.launch { container.settingsRepository.setThemePreference(theme) }
    }

    fun setMicSensitivity(sensitivity: Float) {
        viewModelScope.launch { container.settingsRepository.setMicSensitivity(sensitivity) }
    }

    fun setSecurityLevel(level: SecurityLevel) {
        viewModelScope.launch { container.settingsRepository.setSecurityLevel(level) }
    }

    fun setOfflineModeOnly(offline: Boolean) {
        viewModelScope.launch { container.settingsRepository.setOfflineModeOnly(offline) }
    }

    fun setStoreRawAudio(store: Boolean) {
        viewModelScope.launch { container.settingsRepository.setStoreRawAudioSamples(store) }
    }

    fun setAuditLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAuditLoggingEnabled(enabled) }
    }

    fun setAppLockEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setAppLockEnabled(enabled) }
    }

    fun saveBackupPin(pin: String) {
        viewModelScope.launch {
            container.settingsRepository.setBackupPin(pin)
            if (uiState.value.settings.auditLoggingEnabled) {
                container.auditRepository.recordEvent(
                    category = LogCategory.PROFILE_SECURITY,
                    title = "App Backup PIN Updated",
                    details = "PBKDF2WithHmacSHA256 backup PIN configured.",
                    outcomeSuccess = true
                )
            }
        }
    }

    fun deleteVoiceProfile() {
        viewModelScope.launch {
            container.voiceAuthManager.deleteProfile(uiState.value.settings.auditLoggingEnabled)
            enrollmentSamplesBuffer.clear()
            enrollmentState.value = EnrollmentUiState()
        }
    }

    fun clearSecurityLogs() {
        viewModelScope.launch { container.auditRepository.clearAll() }
    }

    override fun onCleared() {
        super.onCleared()
        container.audioCaptureSource.cancelCapture()
        challengeCountdownJob?.cancel()
    }

    class Factory(private val container: MyLockContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MyLockViewModel::class.java)) {
                return MyLockViewModel(container) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
