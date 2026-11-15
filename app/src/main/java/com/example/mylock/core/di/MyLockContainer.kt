package com.example.mylock.core.di

import android.content.Context
import com.example.mylock.antispoofing.AcousticLivenessDetector
import com.example.mylock.antispoofing.AntiSpoofingEngine
import com.example.mylock.antispoofing.ChallengePhraseManager
import com.example.mylock.antispoofing.RateLimiter
import com.example.mylock.audio.AudioCaptureSource
import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.audio.VoiceActivityDetector
import com.example.mylock.audio.VoiceCaptureManager
import com.example.mylock.authentication.BiometricFallbackManager
import com.example.mylock.authentication.VoiceAuthenticationManager
import com.example.mylock.compatibility.DeviceCompatibilityInspector
import com.example.mylock.data.SettingsRepository
import com.example.mylock.database.MyLockDatabase
import com.example.mylock.database.SecurityAuditRepository
import com.example.mylock.ml.SpeakerEmbeddingExtractor
import com.example.mylock.oem.StandardSafeCompatibilityBridge
import com.example.mylock.oem.SystemVoiceUnlockBridge
import com.example.mylock.security.AndroidKeystoreCryptoProvider
import com.example.mylock.security.CryptoProvider
import com.example.mylock.security.VoiceProfileVault
import com.example.mylock.speaker.AcousticSpectralEmbeddingEngine
import com.example.mylock.speaker.SpeakerSimilarityEngine

class MyLockContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: MyLockDatabase by lazy { MyLockDatabase.build(appContext) }
    val auditRepository: SecurityAuditRepository by lazy {
        SecurityAuditRepository(database.securityAuditLogDao())
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val systemBridge: SystemVoiceUnlockBridge by lazy { StandardSafeCompatibilityBridge(appContext) }
    val compatibilityInspector: DeviceCompatibilityInspector by lazy {
        DeviceCompatibilityInspector(appContext, systemBridge)
    }

    val cryptoProvider: CryptoProvider by lazy { AndroidKeystoreCryptoProvider(appContext) }
    val voiceProfileVault: VoiceProfileVault by lazy {
        VoiceProfileVault(appContext, cryptoProvider)
    }

    val audioCaptureSource: AudioCaptureSource by lazy { VoiceCaptureManager(appContext) }
    val audioPreprocessor: AudioPreprocessor by lazy { AudioPreprocessor() }
    val voiceActivityDetector: VoiceActivityDetector by lazy { VoiceActivityDetector() }
    val speakerEmbeddingExtractor: SpeakerEmbeddingExtractor by lazy {
        AcousticSpectralEmbeddingEngine()
    }
    val similarityEngine: SpeakerSimilarityEngine by lazy { SpeakerSimilarityEngine() }
    val challengePhraseManager: ChallengePhraseManager by lazy { ChallengePhraseManager() }
    val rateLimiter: RateLimiter by lazy { RateLimiter() }
    val antiSpoofingEngine: AntiSpoofingEngine by lazy {
        AntiSpoofingEngine(
            livenessDetector = AcousticLivenessDetector(),
            rateLimiter = rateLimiter
        )
    }

    val voiceAuthManager: VoiceAuthenticationManager by lazy {
        VoiceAuthenticationManager(
            preprocessor = audioPreprocessor,
            vad = voiceActivityDetector,
            embeddingExtractor = speakerEmbeddingExtractor,
            similarityEngine = similarityEngine,
            antiSpoofingEngine = antiSpoofingEngine,
            vault = voiceProfileVault,
            cryptoProvider = cryptoProvider,
            metadataDao = database.voiceProfileMetadataDao(),
            auditRepository = auditRepository
        )
    }

    val biometricFallbackManager: BiometricFallbackManager by lazy { BiometricFallbackManager() }
}
