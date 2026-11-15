package com.example.mylock.authentication

import com.example.mylock.antispoofing.AntiSpoofingEngine
import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.audio.CapturedAudioResult
import com.example.mylock.audio.VoiceActivityDetector
import com.example.mylock.core.model.AudioQualityReport
import com.example.mylock.core.model.ChallengePhrase
import com.example.mylock.core.model.LogCategory
import com.example.mylock.core.model.QualityRejectionReason
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.core.model.VerificationResult
import com.example.mylock.core.model.VerificationStatus
import com.example.mylock.core.model.VoiceProfile
import com.example.mylock.database.SecurityAuditRepository
import com.example.mylock.database.VoiceProfileMetadataDao
import com.example.mylock.database.VoiceProfileMetadataEntity
import com.example.mylock.ml.SpeakerEmbeddingExtractor
import com.example.mylock.security.CryptoProvider
import com.example.mylock.security.PinCryptoHasher
import com.example.mylock.security.VoiceProfileVault
import com.example.mylock.speaker.SpeakerSimilarityEngine
import java.util.UUID

data class EnrollmentSampleEvaluation(
    val accepted: Boolean,
    val qualityReport: AudioQualityReport,
    val embedding: FloatArray? = null,
    val message: String
)

class VoiceAuthenticationManager(
    private val preprocessor: AudioPreprocessor,
    private val vad: VoiceActivityDetector,
    private val embeddingExtractor: SpeakerEmbeddingExtractor,
    private val similarityEngine: SpeakerSimilarityEngine,
    private val antiSpoofingEngine: AntiSpoofingEngine,
    private val vault: VoiceProfileVault,
    private val cryptoProvider: CryptoProvider,
    private val metadataDao: VoiceProfileMetadataDao,
    private val auditRepository: SecurityAuditRepository
) {

    fun evaluateEnrollmentSample(capture: CapturedAudioResult): EnrollmentSampleEvaluation {
        if (!capture.hardwareAvailable) {
            return EnrollmentSampleEvaluation(
                accepted = false,
                qualityReport = AudioQualityReport(0, 0, 0f, 0f, 0f, 0f, false, QualityRejectionReason.NO_MICROPHONE),
                message = "No microphone hardware detected on this device."
            )
        }
        if (!capture.permissionGranted) {
            return EnrollmentSampleEvaluation(
                accepted = false,
                qualityReport = AudioQualityReport(0, 0, 0f, 0f, 0f, 0f, false, QualityRejectionReason.PERMISSION_DENIED),
                message = "Microphone permission is required for voice enrollment."
            )
        }

        val preprocessed = preprocessor.process(capture.samples, capture.sampleRateHz)
        val vadResult = vad.analyze(
            rawSamples = capture.samples,
            preprocessed = preprocessed,
            hardwareAvailable = true,
            permissionGranted = true
        )

        if (!vadResult.qualityReport.isAcceptable) {
            val reasonMsg = when (vadResult.qualityReport.rejectionReason) {
                QualityRejectionReason.RECORDING_TOO_SHORT -> "Recording too short (${vadResult.qualityReport.totalDurationMs} ms). Speak for at least 1.5 seconds."
                QualityRejectionReason.INSUFFICIENT_SPEECH_ACTIVITY -> "Insufficient clear speech detected (${vadResult.qualityReport.activeSpeechMs} ms active). Please speak your phrase clearly."
                QualityRejectionReason.TOO_NOISY -> "Background noise too high (SNR ${"%.1f".format(vadResult.qualityReport.snrDb)} dB). Move to a quieter space."
                QualityRejectionReason.AUDIO_CLIPPING -> "Audio clipping detected. Hold the device slightly further away."
                QualityRejectionReason.SILENT_INPUT -> "Silent input detected. Check microphone mute toggle or hardware."
                else -> "Audio quality check failed."
            }
            return EnrollmentSampleEvaluation(
                accepted = false,
                qualityReport = vadResult.qualityReport,
                message = reasonMsg
            )
        }

        val embedding = embeddingExtractor.extractEmbedding(vadResult.voicedFrames, capture.sampleRateHz)
        return EnrollmentSampleEvaluation(
            accepted = true,
            qualityReport = vadResult.qualityReport,
            embedding = embedding,
            message = "Sample accepted (SNR ${"%.1f".format(vadResult.qualityReport.snrDb)} dB, ${vadResult.qualityReport.activeSpeechMs} ms speech)."
        )
    }

    suspend fun finalizeEnrollment(
        phrase: String,
        sampleEmbeddings: List<FloatArray>,
        rawAudioStored: Boolean,
        auditLoggingEnabled: Boolean
    ): VoiceProfile {
        require(sampleEmbeddings.isNotEmpty()) { "At least one valid sample embedding is required." }
        val fused = similarityEngine.fuseEmbeddings(sampleEmbeddings)
        val now = System.currentTimeMillis()
        val words = phrase.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val hint = if (words.size >= 2) {
            "${words.first().take(2)}*** ${words.last().take(2)}*** (${words.size} words)"
        } else {
            "${phrase.take(2)}***"
        }

        val profile = VoiceProfile(
            profileId = UUID.randomUUID().toString(),
            phraseHashSha256 = PinCryptoHasher.sha256Hex(phrase.trim().lowercase()),
            maskedPhraseHint = hint,
            embedding = fused.toList(),
            embeddingDimension = embeddingExtractor.embeddingDimension,
            modelId = embeddingExtractor.modelId,
            modelVersion = embeddingExtractor.modelVersion,
            sampleCount = sampleEmbeddings.size,
            createdAtMillis = now,
            updatedAtMillis = now,
            hardwareKeystoreBacked = cryptoProvider.isHardwareBacked(),
            rawAudioStored = rawAudioStored
        )

        vault.saveProfile(profile)
        metadataDao.upsertMetadata(
            VoiceProfileMetadataEntity(
                profileId = profile.profileId,
                maskedPhraseHint = profile.maskedPhraseHint,
                modelId = profile.modelId,
                modelVersion = profile.modelVersion,
                sampleCount = profile.sampleCount,
                createdAtMillis = profile.createdAtMillis,
                hardwareKeystoreBacked = profile.hardwareKeystoreBacked
            )
        )

        if (auditLoggingEnabled) {
            auditRepository.recordEvent(
                category = LogCategory.ENROLLMENT,
                title = "Voice Profile Enrolled",
                details = "Encrypted ${profile.sampleCount}-sample profile (${profile.modelId} v${profile.modelVersion}), HW Keystore=${profile.hardwareKeystoreBacked}.",
                outcomeSuccess = true
            )
        }
        return profile
    }

    suspend fun verifySpeaker(
        capture: CapturedAudioResult,
        challenge: ChallengePhrase?,
        securityLevel: SecurityLevel,
        auditLoggingEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): VerificationResult {
        if (antiSpoofingEngine.rateLimiter.isLockedOut(nowMillis)) {
            val remainingSec = antiSpoofingEngine.rateLimiter.remainingLockoutSeconds(nowMillis)
            val result = VerificationResult(
                status = VerificationStatus.LOCKED_OUT_RATE_LIMIT,
                similarityScore = 0f,
                thresholdUsed = securityLevel.threshold,
                confidenceLabel = "Locked Out",
                qualityReport = null,
                antiSpoofingReport = null,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "Too many failed verification attempts. Wait ${remainingSec}s or use Android Biometric / Backup PIN fallback."
            )
            if (auditLoggingEnabled) {
                auditRepository.recordEvent(
                    category = LogCategory.ANTI_SPOOF_ALERT,
                    title = "Rate-Limit Lockout Active",
                    details = result.diagnosticMessage,
                    outcomeSuccess = false
                )
            }
            return result
        }

        if (!capture.hardwareAvailable || !capture.permissionGranted) {
            return VerificationResult(
                status = VerificationStatus.ERROR_HARDWARE_OR_PERMISSION,
                similarityScore = 0f,
                thresholdUsed = securityLevel.threshold,
                confidenceLabel = "Hardware/Permission Error",
                qualityReport = null,
                antiSpoofingReport = null,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = capture.errorMessage ?: "Microphone unavailable or permission denied."
            )
        }

        val enrolledProfile = vault.loadProfile()
        if (enrolledProfile == null) {
            return VerificationResult(
                status = VerificationStatus.REJECTED_NOT_ENROLLED,
                similarityScore = 0f,
                thresholdUsed = securityLevel.threshold,
                confidenceLabel = "No Enrolled Profile",
                qualityReport = null,
                antiSpoofingReport = null,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "No encrypted voice profile found. Complete voice enrollment first."
            )
        }

        val preprocessed = preprocessor.process(capture.samples, capture.sampleRateHz)
        val vadResult = vad.analyze(
            rawSamples = capture.samples,
            preprocessed = preprocessed,
            hardwareAvailable = true,
            permissionGranted = true
        )

        val antiSpoofReport = antiSpoofingEngine.evaluate(
            rawSamples = capture.samples,
            voicedFrames = vadResult.voicedFrames,
            qualityReport = vadResult.qualityReport,
            challenge = challenge,
            sampleRateHz = capture.sampleRateHz,
            nowMillis = nowMillis
        )

        if (!vadResult.qualityReport.isAcceptable) {
            val result = VerificationResult(
                status = VerificationStatus.REJECTED_POOR_QUALITY,
                similarityScore = 0f,
                thresholdUsed = securityLevel.threshold,
                confidenceLabel = "Rejected: Audio Quality",
                qualityReport = vadResult.qualityReport,
                antiSpoofingReport = antiSpoofReport,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "Audio rejected (${vadResult.qualityReport.rejectionReason?.name}). SNR=${"%.1f".format(vadResult.qualityReport.snrDb)} dB."
            )
            if (auditLoggingEnabled) {
                auditRepository.recordEvent(
                    category = LogCategory.VERIFICATION_FAILED,
                    title = "Verification Rejected (Quality)",
                    details = result.diagnosticMessage,
                    outcomeSuccess = false
                )
            }
            return result
        }

        if (!antiSpoofReport.passed) {
            antiSpoofingEngine.rateLimiter.recordFailure(nowMillis)
            val reasons = antiSpoofReport.failureReasons.joinToString(", ") { it.name }
            val result = VerificationResult(
                status = VerificationStatus.REJECTED_ANTI_SPOOFING,
                similarityScore = 0f,
                thresholdUsed = securityLevel.threshold,
                confidenceLabel = "Blocked by Anti-Spoofing",
                qualityReport = vadResult.qualityReport,
                antiSpoofingReport = antiSpoofReport,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "Anti-spoofing rejected audio: $reasons (Risk=${"%.2f".format(antiSpoofReport.acousticReplayRiskScore)})."
            )
            if (auditLoggingEnabled) {
                auditRepository.recordEvent(
                    category = LogCategory.ANTI_SPOOF_ALERT,
                    title = "Anti-Spoofing Rejection",
                    details = result.diagnosticMessage,
                    outcomeSuccess = false
                )
            }
            return result
        }

        val probeEmbedding = embeddingExtractor.extractEmbedding(vadResult.voicedFrames, capture.sampleRateHz)
        val simEval = similarityEngine.evaluate(
            enrolledEmbedding = enrolledProfile.embedding.toFloatArray(),
            probeEmbedding = probeEmbedding,
            securityLevel = securityLevel
        )

        return if (simEval.isMatch) {
            antiSpoofingEngine.rateLimiter.recordSuccess()
            val result = VerificationResult(
                status = VerificationStatus.VERIFIED_APP_LEVEL,
                similarityScore = simEval.normalizedScore,
                thresholdUsed = simEval.threshold,
                confidenceLabel = simEval.confidenceLabel,
                qualityReport = vadResult.qualityReport,
                antiSpoofingReport = antiSpoofReport,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "App-level speaker verified (${"%.1f".format(simEval.normalizedScore * 100)}% vs ${"%.0f".format(simEval.threshold * 100)}% threshold)."
            )
            if (auditLoggingEnabled) {
                auditRepository.recordEvent(
                    category = LogCategory.VERIFICATION_SUCCESS,
                    title = "Speaker Verified (App-Level)",
                    details = result.diagnosticMessage,
                    score = simEval.normalizedScore,
                    outcomeSuccess = true
                )
            }
            result
        } else {
            antiSpoofingEngine.rateLimiter.recordFailure(nowMillis)
            val result = VerificationResult(
                status = VerificationStatus.REJECTED_SPEAKER_MISMATCH,
                similarityScore = simEval.normalizedScore,
                thresholdUsed = simEval.threshold,
                confidenceLabel = simEval.confidenceLabel,
                qualityReport = vadResult.qualityReport,
                antiSpoofingReport = antiSpoofReport,
                modelId = embeddingExtractor.modelId,
                modelVersion = embeddingExtractor.modelVersion,
                evaluatedAtMillis = nowMillis,
                diagnosticMessage = "Speaker voice did not meet threshold (${"%.1f".format(simEval.normalizedScore * 100)}% < ${"%.0f".format(simEval.threshold * 100)}%)."
            )
            if (auditLoggingEnabled) {
                auditRepository.recordEvent(
                    category = LogCategory.VERIFICATION_FAILED,
                    title = "Speaker Mismatch",
                    details = result.diagnosticMessage,
                    score = simEval.normalizedScore,
                    outcomeSuccess = false
                )
            }
            result
        }
    }

    suspend fun deleteProfile(auditLoggingEnabled: Boolean): Boolean {
        val deleted = vault.deleteAllBiometricData()
        metadataDao.deleteAllMetadata()
        antiSpoofingEngine.clearSessionCache()
        antiSpoofingEngine.rateLimiter.reset()
        if (auditLoggingEnabled) {
            auditRepository.recordEvent(
                category = LogCategory.PROFILE_SECURITY,
                title = "Voice Profile Deleted",
                details = "Encrypted voice embedding wiped and Keystore alias removed.",
                outcomeSuccess = deleted
            )
        }
        return deleted
    }
}
