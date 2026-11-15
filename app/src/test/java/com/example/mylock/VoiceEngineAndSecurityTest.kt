package com.example.mylock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mylock.antispoofing.AcousticLivenessDetector
import com.example.mylock.antispoofing.AntiSpoofingEngine
import com.example.mylock.antispoofing.ChallengePhraseManager
import com.example.mylock.antispoofing.RateLimiter
import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.audio.CapturedAudioResult
import com.example.mylock.audio.VoiceActivityDetector
import com.example.mylock.authentication.VoiceAuthenticationManager
import com.example.mylock.core.model.AntiSpoofFailureReason
import com.example.mylock.core.model.AppLanguage
import com.example.mylock.core.model.ChallengePhrase
import com.example.mylock.core.model.QualityRejectionReason
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.core.model.VerificationStatus
import com.example.mylock.database.MyLockDatabase
import com.example.mylock.database.SecurityAuditRepository
import com.example.mylock.security.CryptoProvider
import com.example.mylock.security.EncryptedPayload
import com.example.mylock.security.PinCryptoHasher
import com.example.mylock.security.VoiceProfileVault
import com.example.mylock.speaker.AcousticSpectralEmbeddingEngine
import com.example.mylock.speaker.SpeakerSimilarityEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private class TestAesGcmCryptoProvider : CryptoProvider {
    private var key: SecretKey? = generateKey()

    private fun generateKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        val secretKey = key ?: generateKey().also { key = it }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return EncryptedPayload(
            iv = cipher.iv,
            ciphertext = cipher.doFinal(plaintext),
            hardwareBacked = false
        )
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        val secretKey = key ?: throw IllegalStateException("Key deleted")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, payload.iv))
        return cipher.doFinal(payload.ciphertext)
    }

    override fun isHardwareBacked(): Boolean = false

    override fun deleteKey() {
        key = null
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VoiceEngineAndSecurityTest {

    private lateinit var context: Context
    private lateinit var db: MyLockDatabase
    private lateinit var cryptoProvider: TestAesGcmCryptoProvider
    private lateinit var vault: VoiceProfileVault
    private lateinit var rateLimiter: RateLimiter
    private lateinit var antiSpoofing: AntiSpoofingEngine
    private lateinit var authManager: VoiceAuthenticationManager
    private lateinit var challengeManager: ChallengePhraseManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MyLockDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoProvider = TestAesGcmCryptoProvider()
        vault = VoiceProfileVault(context, cryptoProvider)
        rateLimiter = RateLimiter(maxFailedAttempts = 5, lockoutDurationMs = 60_000L)
        antiSpoofing = AntiSpoofingEngine(AcousticLivenessDetector(), rateLimiter)
        authManager = VoiceAuthenticationManager(
            preprocessor = AudioPreprocessor(),
            vad = VoiceActivityDetector(),
            embeddingExtractor = AcousticSpectralEmbeddingEngine(),
            similarityEngine = SpeakerSimilarityEngine(),
            antiSpoofingEngine = antiSpoofing,
            vault = vault,
            cryptoProvider = cryptoProvider,
            metadataDao = db.voiceProfileMetadataDao(),
            auditRepository = SecurityAuditRepository(db.securityAuditLogDao())
        )
        challengeManager = ChallengePhraseManager(ttlMillis = 30_000L)
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun synthesizeRichSpeech(
        baseF0Hz: Double,
        durationMs: Int = 2000,
        amplitude: Double = 0.45,
        noiseStd: Double = 0.005,
        includeHighHarmonics: Boolean = true,
        seed: Int = 42
    ): FloatArray {
        val sr = 16_000
        val count = (sr * durationMs) / 1000
        val rng = Random(seed)
        return FloatArray(count) { i ->
            val t = i.toDouble() / sr
            // Modulate pitch and amplitude naturally like human speech syllables
            val syllableEnv = (0.55 + 0.45 * sin(2.0 * PI * 3.2 * t)).coerceIn(0.15, 1.0)
            val consonantBurst = ((sin(2.0 * PI * 1.4 * t) + 1.0) * 0.5).let { it * it }
            val f0 = baseF0Hz * (1.0 + 0.06 * sin(2.0 * PI * 2.1 * t))
            var sample = 0.50 * sin(2.0 * PI * f0 * t) +
                (0.25 + 0.15 * consonantBurst) * sin(2.0 * PI * (f0 * 2.0) * t) +
                0.18 * sin(2.0 * PI * (f0 * 3.0) * t) +
                0.12 * sin(2.0 * PI * (f0 * 5.0) * t)
            if (includeHighHarmonics) {
                // Natural consonant / fricative energy around 5.8-6.8 kHz varying by syllable
                sample += (0.04 + 0.12 * consonantBurst) * sin(2.0 * PI * 6100.0 * t) * syllableEnv
            }
            val noise = (rng.nextDouble() - 0.5) * 2.0 * noiseStd
            ((sample * amplitude * syllableEnv) + noise).toFloat().coerceIn(-1f, 1f)
        }
    }

    @Test
    fun `microphone permission denied is rejected cleanly`() = runTest {
        val capture = CapturedAudioResult(
            samples = FloatArray(0),
            sampleRateHz = 16_000,
            durationMs = 0L,
            hardwareAvailable = true,
            permissionGranted = false,
            errorMessage = "RECORD_AUDIO permission denied."
        )
        val eval = authManager.evaluateEnrollmentSample(capture)
        assertFalse(eval.accepted)
        assertEquals(QualityRejectionReason.PERMISSION_DENIED, eval.qualityReport.rejectionReason)

        val verify = authManager.verifySpeaker(
            capture,
            challengeManager.issueChallenge(AppLanguage.ENGLISH),
            SecurityLevel.STANDARD,
            auditLoggingEnabled = true
        )
        assertEquals(VerificationStatus.ERROR_HARDWARE_OR_PERMISSION, verify.status)
    }

    @Test
    fun `no microphone available is detected and rejected`() = runTest {
        val capture = CapturedAudioResult(
            samples = FloatArray(0),
            sampleRateHz = 16_000,
            durationMs = 0L,
            hardwareAvailable = false,
            permissionGranted = true,
            errorMessage = "No microphone hardware detected."
        )
        val eval = authManager.evaluateEnrollmentSample(capture)
        assertFalse(eval.accepted)
        assertEquals(QualityRejectionReason.NO_MICROPHONE, eval.qualityReport.rejectionReason)
    }

    @Test
    fun `poor recording quality too short silent and noisy are rejected`() {
        val shortSamples = synthesizeRichSpeech(135.0, durationMs = 400)
        val shortEval = authManager.evaluateEnrollmentSample(
            CapturedAudioResult(shortSamples, 16_000, 400L, true, true)
        )
        assertFalse(shortEval.accepted)
        assertEquals(QualityRejectionReason.RECORDING_TOO_SHORT, shortEval.qualityReport.rejectionReason)

        val silentSamples = FloatArray(32_000) { 0.0001f }
        val silentEval = authManager.evaluateEnrollmentSample(
            CapturedAudioResult(silentSamples, 16_000, 2000L, true, true)
        )
        assertFalse(silentEval.accepted)
        assertEquals(QualityRejectionReason.SILENT_INPUT, silentEval.qualityReport.rejectionReason)

        val rng = Random(99)
        val pureNoise = FloatArray(32_000) { ((rng.nextDouble() - 0.5) * 0.4).toFloat() }
        val noiseEval = authManager.evaluateEnrollmentSample(
            CapturedAudioResult(pureNoise, 16_000, 2000L, true, true)
        )
        assertFalse(noiseEval.accepted)
    }

    @Test
    fun `enrolled speaker verifies while distinct speaker is rejected`() = runTest {
        val s1 = synthesizeRichSpeech(baseF0Hz = 125.0, seed = 1)
        val s2 = synthesizeRichSpeech(baseF0Hz = 126.0, seed = 2)
        val s3 = synthesizeRichSpeech(baseF0Hz = 124.0, seed = 3)

        val e1 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s1, 16_000, 2000L, true, true))
        val e2 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s2, 16_000, 2000L, true, true))
        val e3 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s3, 16_000, 2000L, true, true))
        assertTrue(e1.accepted && e2.accepted && e3.accepted)

        authManager.finalizeEnrollment(
            phrase = "My Lock Verify Voice",
            sampleEmbeddings = listOf(e1.embedding!!, e2.embedding!!, e3.embedding!!),
            rawAudioStored = false,
            auditLoggingEnabled = true
        )

        // Same speaker fresh utterance
        val probeSame = synthesizeRichSpeech(baseF0Hz = 125.5, seed = 10)
        val sameRes = authManager.verifySpeaker(
            capture = CapturedAudioResult(probeSame, 16_000, 2000L, true, true),
            challenge = challengeManager.issueChallenge(AppLanguage.ENGLISH),
            securityLevel = SecurityLevel.STANDARD,
            auditLoggingEnabled = true
        )
        assertEquals(VerificationStatus.VERIFIED_APP_LEVEL, sameRes.status)
        assertTrue(sameRes.similarityScore >= SecurityLevel.STANDARD.threshold)

        // Different speaker with distinct vocal pitch & spectral distribution
        val probeOther = synthesizeRichSpeech(baseF0Hz = 275.0, seed = 77)
        val otherRes = authManager.verifySpeaker(
            capture = CapturedAudioResult(probeOther, 16_000, 2000L, true, true),
            challenge = challengeManager.issueChallenge(AppLanguage.ENGLISH),
            securityLevel = SecurityLevel.HIGH,
            auditLoggingEnabled = true
        )
        assertEquals(VerificationStatus.REJECTED_SPEAKER_MISMATCH, otherRes.status)
    }

    @Test
    fun `exact replay attempt and expired challenge are blocked by anti spoofing`() = runTest {
        val s1 = synthesizeRichSpeech(baseF0Hz = 130.0, seed = 11)
        val e1 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s1, 16_000, 2000L, true, true))
        assertTrue(e1.accepted)
        authManager.finalizeEnrollment("Alpha Vault Key", listOf(e1.embedding!!), false, true)

        val now = 1_000_000L
        val probe = synthesizeRichSpeech(baseF0Hz = 130.0, seed = 12)
        val c1 = challengeManager.issueChallenge(AppLanguage.ENGLISH, nowMillis = now)

        val first = authManager.verifySpeaker(
            capture = CapturedAudioResult(probe, 16_000, 2000L, true, true),
            challenge = c1,
            securityLevel = SecurityLevel.STANDARD,
            auditLoggingEnabled = true,
            nowMillis = now + 2000L
        )
        assertEquals(VerificationStatus.VERIFIED_APP_LEVEL, first.status)

        // Replay exact same audio bytes with a new challenge -> blocked by SHA-256 replay detector
        val c2 = challengeManager.issueChallenge(AppLanguage.ENGLISH, nowMillis = now + 3000L)
        val replay = authManager.verifySpeaker(
            capture = CapturedAudioResult(probe, 16_000, 2000L, true, true),
            challenge = c2,
            securityLevel = SecurityLevel.STANDARD,
            auditLoggingEnabled = true,
            nowMillis = now + 4000L
        )
        assertEquals(VerificationStatus.REJECTED_ANTI_SPOOFING, replay.status)
        assertTrue(
            replay.antiSpoofingReport?.failureReasons?.contains(AntiSpoofFailureReason.EXACT_REPLAY_DETECTED) == true
        )

        // Expired challenge timeout -> blocked
        val expiredChallenge = ChallengePhrase("id", "Phrase", "en", now, now + 30_000L, "nonce-exp")
        val freshProbe = synthesizeRichSpeech(baseF0Hz = 130.0, seed = 13)
        val expiredRes = authManager.verifySpeaker(
            capture = CapturedAudioResult(freshProbe, 16_000, 2000L, true, true),
            challenge = expiredChallenge,
            securityLevel = SecurityLevel.STANDARD,
            auditLoggingEnabled = true,
            nowMillis = now + 35_000L
        )
        assertEquals(VerificationStatus.REJECTED_ANTI_SPOOFING, expiredRes.status)
        assertTrue(
            expiredRes.antiSpoofingReport?.failureReasons?.contains(AntiSpoofFailureReason.CHALLENGE_EXPIRED) == true
        )
    }

    @Test
    fun `rate limiter enforces lockout after 5 failed attempts`() = runTest {
        val s1 = synthesizeRichSpeech(baseF0Hz = 130.0, seed = 21)
        val e1 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s1, 16_000, 2000L, true, true))
        authManager.finalizeEnrollment("Cobalt Shield Phrase", listOf(e1.embedding!!), false, true)

        var now = 2_000_000L
        repeat(5) { idx ->
            val other = synthesizeRichSpeech(baseF0Hz = 285.0, seed = 100 + idx)
            authManager.verifySpeaker(
                capture = CapturedAudioResult(other, 16_000, 2000L, true, true),
                challenge = challengeManager.issueChallenge(AppLanguage.ENGLISH, now),
                securityLevel = SecurityLevel.HIGH,
                auditLoggingEnabled = true,
                nowMillis = now + 1000L
            )
            now += 2000L
        }

        val lockedOutRes = authManager.verifySpeaker(
            capture = CapturedAudioResult(synthesizeRichSpeech(130.0, seed = 22), 16_000, 2000L, true, true),
            challenge = challengeManager.issueChallenge(AppLanguage.ENGLISH, now),
            securityLevel = SecurityLevel.STANDARD,
            auditLoggingEnabled = true,
            nowMillis = now + 1000L
        )
        assertEquals(VerificationStatus.LOCKED_OUT_RATE_LIMIT, lockedOutRes.status)
    }

    @Test
    fun `secure data deletion wipes encrypted vault metadata and backup pin hash verifies`() = runTest {
        val s1 = synthesizeRichSpeech(baseF0Hz = 140.0, seed = 31)
        val e1 = authManager.evaluateEnrollmentSample(CapturedAudioResult(s1, 16_000, 2000L, true, true))
        authManager.finalizeEnrollment("Secure Delete Test", listOf(e1.embedding!!), false, true)

        assertTrue(vault.hasEncryptedProfileFile())
        assertNotNull(vault.loadProfile())

        val deleted = authManager.deleteProfile(auditLoggingEnabled = true)
        assertTrue(deleted)
        assertFalse(vault.hasEncryptedProfileFile())
        assertNull(vault.loadProfile())
        assertNull(db.voiceProfileMetadataDao().observeActiveMetadata().first())

        val pinHash = PinCryptoHasher.hashPin("749201")
        assertTrue(PinCryptoHasher.verifyPin("749201", pinHash))
        assertFalse(PinCryptoHasher.verifyPin("000000", pinHash))
    }
}
