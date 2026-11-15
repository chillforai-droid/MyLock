package com.example.mylock

import com.example.mylock.antispoofing.AcousticLivenessDetector
import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.audio.VoiceActivityDetector
import com.example.mylock.core.model.SecurityLevel
import com.example.mylock.speaker.AcousticSpectralEmbeddingEngine
import com.example.mylock.speaker.SpeakerSimilarityEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AcousticDspUnitTest {

    private val preprocessor = AudioPreprocessor()
    private val vad = VoiceActivityDetector()
    private val extractor = AcousticSpectralEmbeddingEngine()
    private val similarityEngine = SpeakerSimilarityEngine()
    private val livenessDetector = AcousticLivenessDetector()

    private fun synthVoice(
        f0: Double,
        highFreqAmplitude: Double = 0.08,
        durationSec: Double = 1.8
    ): FloatArray {
        val sr = 16_000
        val n = (sr * durationSec).toInt()
        return FloatArray(n) { i ->
            val t = i.toDouble() / sr
            val env = 0.6 + 0.4 * sin(2.0 * PI * 3.0 * t)
            val s = 0.5 * sin(2.0 * PI * f0 * t) +
                0.3 * sin(2.0 * PI * 2.0 * f0 * t) +
                0.2 * sin(2.0 * PI * 3.0 * f0 * t) +
                highFreqAmplitude * sin(2.0 * PI * 6200.0 * t)
            (s * 0.45 * env).toFloat().coerceIn(-1f, 1f)
        }
    }

    @Test
    fun `embedding vector is 64 dimensional and unit L2 normalized`() {
        val raw = synthVoice(135.0)
        val pre = preprocessor.process(raw, 16_000)
        val vadRes = vad.analyze(raw, pre)
        assertTrue(vadRes.qualityReport.isAcceptable)

        val emb = extractor.extractEmbedding(vadRes.voicedFrames, 16_000)
        assertEquals(64, emb.size)
        val norm = sqrt(emb.sumOf { (it * it).toDouble() })
        assertEquals(1.0, norm, 1e-3)
    }

    @Test
    fun `same speaker yields higher cosine similarity than distinct speaker`() {
        val enrollA = synthVoice(128.0)
        val probeA = synthVoice(129.0)
        val probeB = synthVoice(260.0)

        val embEnroll = extractor.extractEmbedding(vad.analyze(enrollA, preprocessor.process(enrollA)).voicedFrames)
        val embProbeA = extractor.extractEmbedding(vad.analyze(probeA, preprocessor.process(probeA)).voicedFrames)
        val embProbeB = extractor.extractEmbedding(vad.analyze(probeB, preprocessor.process(probeB)).voicedFrames)

        val simSame = similarityEngine.evaluate(embEnroll, embProbeA, SecurityLevel.STANDARD)
        val simDiff = similarityEngine.evaluate(embEnroll, embProbeB, SecurityLevel.STANDARD)

        assertTrue("Expected same-speaker similarity (${simSame.normalizedScore}) > distinct-speaker (${simDiff.normalizedScore})", simSame.normalizedScore > simDiff.normalizedScore)
        assertTrue(simSame.isMatch)
    }

    @Test
    fun `speaker playback bandlimited above 5kHz triggers high frequency cutoff warning`() {
        val bandLimited = synthVoice(135.0, highFreqAmplitude = 0.0)
        val pre = preprocessor.process(bandLimited, 16_000)
        val vadRes = vad.analyze(bandLimited, pre)
        val signals = livenessDetector.evaluateLiveness(vadRes.voicedFrames, 16_000)
        assertTrue(signals.highFreqCutoffDetected)
    }

    @Test
    fun `natural wideband signal does not trigger high frequency cutoff`() {
        val wideband = synthVoice(135.0, highFreqAmplitude = 0.10)
        val pre = preprocessor.process(wideband, 16_000)
        val vadRes = vad.analyze(wideband, pre)
        val signals = livenessDetector.evaluateLiveness(vadRes.voicedFrames, 16_000)
        assertFalse(signals.highFreqCutoffDetected)
    }
}
