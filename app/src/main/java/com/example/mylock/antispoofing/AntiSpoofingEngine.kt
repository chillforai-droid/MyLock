package com.example.mylock.antispoofing

import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.core.model.AntiSpoofFailureReason
import com.example.mylock.core.model.AntiSpoofingReport
import com.example.mylock.core.model.AppLanguage
import com.example.mylock.core.model.AudioQualityReport
import com.example.mylock.core.model.ChallengePhrase
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sqrt

interface LivenessDetectionInterface {
    fun evaluateLiveness(
        voicedFrames: List<FloatArray>,
        sampleRateHz: Int
    ): LivenessSignals
}

interface AntiSpoofingModelInterface {
    val modelId: String
    val isNeuralAntiSpoofModel: Boolean
    fun scoreSpoofRisk(voicedFrames: List<FloatArray>, sampleRateHz: Int): Float
}

data class LivenessSignals(
    val harmonicToNoiseRatioDb: Float,
    val spectralFlatnessVariance: Float,
    val highFreqCutoffDetected: Boolean,
    val syntheticUniformityDetected: Boolean,
    val acousticReplayRiskScore: Float
)

class ChallengePhraseManager(
    private val ttlMillis: Long = 30_000L
) {
    private val random = SecureRandom()

    private val englishChallenges = listOf(
        "Blue Shield Seven Four",
        "Verify Vault Alpha Nine",
        "Cobalt River Three Eight",
        "Arctic Beacon Five Two",
        "Secure Echo Six One",
        "Cipher Harbor Nine Zero"
    )

    private val hindiChallenges = listOf(
        "नीला कवच सात चार",
        "सुरक्षा द्वार तीन आठ",
        "शीतल किरण पाँच दो",
        "विश्वास सेतु छह एक",
        "ध्वनि रक्षक नौ शून्य"
    )

    fun issueChallenge(language: AppLanguage, nowMillis: Long = System.currentTimeMillis()): ChallengePhrase {
        val pool = when (language) {
            AppLanguage.HINDI -> hindiChallenges
            AppLanguage.ENGLISH -> englishChallenges
        }
        val phrase = pool[random.nextInt(pool.size)]
        val nonceBytes = ByteArray(8)
        random.nextBytes(nonceBytes)
        val nonce = nonceBytes.joinToString("") { "%02x".format(it) }
        return ChallengePhrase(
            id = UUID.randomUUID().toString(),
            phraseText = phrase,
            languageCode = language.code,
            issuedAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis,
            nonce = nonce
        )
    }
}

class RateLimiter(
    private val maxFailedAttempts: Int = 5,
    private val lockoutDurationMs: Long = 60_000L
) {
    private var consecutiveFailures = 0
    private var lockedUntilMillis = 0L

    fun isLockedOut(nowMillis: Long = System.currentTimeMillis()): Boolean =
        nowMillis < lockedUntilMillis

    fun remainingLockoutSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        if (nowMillis >= lockedUntilMillis) return 0L
        return ((lockedUntilMillis - nowMillis) + 999L) / 1000L
    }

    fun remainingAttempts(): Int = (maxFailedAttempts - consecutiveFailures).coerceAtLeast(0)

    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (nowMillis < lockedUntilMillis) return true
        consecutiveFailures++
        return if (consecutiveFailures >= maxFailedAttempts) {
            lockedUntilMillis = nowMillis + lockoutDurationMs
            true
        } else {
            false
        }
    }

    fun recordSuccess() {
        consecutiveFailures = 0
        lockedUntilMillis = 0L
    }

    fun reset() {
        consecutiveFailures = 0
        lockedUntilMillis = 0L
    }
}

class AcousticLivenessDetector : LivenessDetectionInterface, AntiSpoofingModelInterface {

    override val modelId: String = "acoustic-replay-heuristics-v1"
    override val isNeuralAntiSpoofModel: Boolean = false

    override fun scoreSpoofRisk(voicedFrames: List<FloatArray>, sampleRateHz: Int): Float =
        evaluateLiveness(voicedFrames, sampleRateHz).acousticReplayRiskScore

    override fun evaluateLiveness(
        voicedFrames: List<FloatArray>,
        sampleRateHz: Int
    ): LivenessSignals {
        if (voicedFrames.isEmpty()) {
            return LivenessSignals(
                harmonicToNoiseRatioDb = 0f,
                spectralFlatnessVariance = 0f,
                highFreqCutoffDetected = true,
                syntheticUniformityDetected = true,
                acousticReplayRiskScore = 1.0f
            )
        }

        val fftSize = 512
        val binHz = sampleRateHz.toFloat() / fftSize.toFloat()
        var lowBandEnergy = 0.0
        var highBandEnergy = 0.0
        val flatnessValues = FloatArray(voicedFrames.size)
        val hnrValues = FloatArray(voicedFrames.size)

        voicedFrames.forEachIndexed { idx, frame ->
            val mag = AudioPreprocessor.fftMagnitudeSpectrum(frame, fftSize)
            var geoMeanLogSum = 0.0
            var arithMeanSum = 0.0
            var maxBinVal = 1e-6f
            var totalBinVal = 1e-6f

            for (k in 1 until mag.size) {
                val freq = k * binHz
                val p = mag[k] * mag[k]
                if (freq in 300f..3400f) lowBandEnergy += p
                if (freq in 5200f..7800f) highBandEnergy += p

                val valEps = mag[k].toDouble().coerceAtLeast(1e-7)
                geoMeanLogSum += ln(valEps)
                arithMeanSum += valEps
                if (mag[k] > maxBinVal) maxBinVal = mag[k]
                totalBinVal += mag[k]
            }

            val n = (mag.size - 1).coerceAtLeast(1)
            val geoMean = exp(geoMeanLogSum / n)
            val arithMean = arithMeanSum / n
            flatnessValues[idx] = (geoMean / arithMean.coerceAtLeast(1e-8)).toFloat().coerceIn(0f, 1f)

            // Peak-to-average harmonic prominence proxy (in dB)
            val avgOther = ((totalBinVal - maxBinVal) / (n - 1).coerceAtLeast(1)).coerceAtLeast(1e-6f)
            hnrValues[idx] = (20f * log10((maxBinVal / avgOther).coerceAtLeast(1f))).coerceIn(0f, 45f)
        }

        val highToLowRatio = (highBandEnergy / lowBandEnergy.coerceAtLeast(1e-8)).toFloat()
        // Small mobile/laptop playback speakers often exhibit severe attenuation above 5.2 kHz
        val highFreqCutoff = highToLowRatio < 0.00018f

        val meanFlatness = flatnessValues.average().toFloat()
        var flatnessVarSum = 0f
        for (v in flatnessValues) {
            val d = v - meanFlatness
            flatnessVarSum += d * d
        }
        val flatnessVariance = flatnessVarSum / flatnessValues.size.coerceAtLeast(1)
        // Robotic/concatenative TTS or looped audio shows abnormally rigid spectral flatness variance
        val syntheticUniformity = flatnessVariance < 0.000035f

        val meanHnrDb = hnrValues.average().toFloat()

        var risk = 0.05f
        if (highFreqCutoff) risk += 0.42f
        if (syntheticUniformity) risk += 0.38f
        if (meanHnrDb < 7.5f) risk += 0.25f

        return LivenessSignals(
            harmonicToNoiseRatioDb = meanHnrDb,
            spectralFlatnessVariance = flatnessVariance,
            highFreqCutoffDetected = highFreqCutoff,
            syntheticUniformityDetected = syntheticUniformity,
            acousticReplayRiskScore = risk.coerceIn(0f, 1f)
        )
    }
}

class AntiSpoofingEngine(
    private val livenessDetector: LivenessDetectionInterface = AcousticLivenessDetector(),
    val rateLimiter: RateLimiter = RateLimiter()
) {
    private val recentAudioHashes = LinkedHashSet<String>()
    private val consumedNonces = HashSet<String>()

    fun evaluate(
        rawSamples: FloatArray,
        voicedFrames: List<FloatArray>,
        qualityReport: AudioQualityReport,
        challenge: ChallengePhrase?,
        sampleRateHz: Int = 16_000,
        nowMillis: Long = System.currentTimeMillis()
    ): AntiSpoofingReport {
        val failures = mutableListOf<AntiSpoofFailureReason>()

        if (rateLimiter.isLockedOut(nowMillis)) {
            failures.add(AntiSpoofFailureReason.RATE_LIMITED)
        }

        val challengeValid = challenge != null &&
            !challenge.isExpired(nowMillis) &&
            !consumedNonces.contains(challenge.nonce)

        if (challenge == null) {
            failures.add(AntiSpoofFailureReason.CHALLENGE_MISSING)
        } else if (challenge.isExpired(nowMillis) || consumedNonces.contains(challenge.nonce)) {
            failures.add(AntiSpoofFailureReason.CHALLENGE_EXPIRED)
        }

        if (!qualityReport.isAcceptable) {
            failures.add(AntiSpoofFailureReason.POOR_AUDIO_QUALITY)
        }

        val audioDigest = computeAudioSha256(rawSamples)
        val isUniqueAudio = !recentAudioHashes.contains(audioDigest)
        if (!isUniqueAudio && rawSamples.isNotEmpty()) {
            failures.add(AntiSpoofFailureReason.EXACT_REPLAY_DETECTED)
        }

        val liveness = livenessDetector.evaluateLiveness(voicedFrames, sampleRateHz)
        if (liveness.highFreqCutoffDetected && liveness.acousticReplayRiskScore >= 0.55f) {
            failures.add(AntiSpoofFailureReason.ACOUSTIC_PLAYBACK_SUSPECTED)
        }
        if (liveness.syntheticUniformityDetected) {
            failures.add(AntiSpoofFailureReason.SYNTHETIC_UNIFORMITY_SUSPECTED)
        }

        if (rawSamples.isNotEmpty() && isUniqueAudio) {
            if (recentAudioHashes.size >= 32) {
                recentAudioHashes.iterator().let {
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }
            recentAudioHashes.add(audioDigest)
        }

        challenge?.let { consumedNonces.add(it.nonce) }

        return AntiSpoofingReport(
            passed = failures.isEmpty(),
            challengeValid = challengeValid,
            replayHashUnique = isUniqueAudio,
            acousticReplayRiskScore = liveness.acousticReplayRiskScore,
            harmonicToNoiseRatioDb = liveness.harmonicToNoiseRatioDb,
            spectralFlatnessVariance = liveness.spectralFlatnessVariance,
            highFreqCutoffDetected = liveness.highFreqCutoffDetected,
            rateLimitRemainingAttempts = rateLimiter.remainingAttempts(),
            lockoutRemainingSeconds = rateLimiter.remainingLockoutSeconds(nowMillis),
            failureReasons = failures
        )
    }

    fun clearSessionCache() {
        recentAudioHashes.clear()
        consumedNonces.clear()
    }

    private fun computeAudioSha256(samples: FloatArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(4)
        for (s in samples) {
            val bits = java.lang.Float.floatToIntBits(s)
            bytes[0] = (bits shr 24).toByte()
            bytes[1] = (bits shr 16).toByte()
            bytes[2] = (bits shr 8).toByte()
            bytes[3] = bits.toByte()
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
