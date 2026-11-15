package com.example.mylock.speaker

import com.example.mylock.audio.AudioPreprocessor
import com.example.mylock.ml.SpeakerEmbeddingExtractor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Offline-first deterministic DSP speaker acoustic embedding extractor.
 *
 * Computes a 64-dimensional L2-normalized vector from voiced speech frames:
 * - Dims 0..19: Mean 20-coefficient MFCCs (capturing vocal tract spectral envelope)
 * - Dims 20..39: Std-dev of 20 MFCCs (capturing dynamic articulation variance)
 * - Dims 40..55: Mean 16-band log-Mel spectral energy profile
 * - Dims 56..63: Prosodic & spectral statistics (spectral centroid, rolloff, flux,
 *   pitch autocorrelation peak proxy, ZCR mean/var, energy dynamic range)
 *
 * HONEST LIMITATION DISCLOSURE:
 * This is a lightweight offline DSP acoustic baseline, NOT a deep neural
 * x-vector/ECAPA-TDNN model. No neural FAR/FRR accuracy figures are claimed.
 */
class AcousticSpectralEmbeddingEngine : SpeakerEmbeddingExtractor {

    override val modelId: String = "dsp-acoustic-mfcc-v1"
    override val modelVersion: String = "1.0.0"
    override val embeddingDimension: Int = 64
    override val isPretrainedNeuralModel: Boolean = false
    override val licenseAndOriginNote: String =
        "Built-in pure Kotlin DSP baseline (20 MFCC mean/std + 16 Mel bands + spectral/pitch stats). Zero external weights."

    private val numMelFilters = 26
    private val numMfcc = 20
    private val fftSize = 512

    override fun extractEmbedding(voicedFrames: List<FloatArray>, sampleRateHz: Int): FloatArray {
        if (voicedFrames.isEmpty()) {
            return FloatArray(embeddingDimension)
        }

        val filterBank = buildMelFilterBank(sampleRateHz, fftSize, numMelFilters)
        val mfccFrames = ArrayList<FloatArray>(voicedFrames.size)
        val melLogFrames = ArrayList<FloatArray>(voicedFrames.size)
        val centroids = FloatArray(voicedFrames.size)
        val rolloffs = FloatArray(voicedFrames.size)
        val zcrs = FloatArray(voicedFrames.size)
        val pitches = FloatArray(voicedFrames.size)
        val energies = FloatArray(voicedFrames.size)
        var prevMag: FloatArray? = null
        var fluxSum = 0f

        voicedFrames.forEachIndexed { idx, frame ->
            val mag = AudioPreprocessor.fftMagnitudeSpectrum(frame, fftSize)
            val power = FloatArray(mag.size) { k -> mag[k] * mag[k] }

            // Mel filterbank energies
            val melLog = FloatArray(numMelFilters) { m ->
                var energy = 1e-8f
                val weights = filterBank[m]
                for (k in weights.indices) {
                    energy += power[k] * weights[k]
                }
                ln(energy)
            }
            melLogFrames.add(melLog)

            // DCT-II -> MFCC 0..19
            val mfcc = FloatArray(numMfcc) { c ->
                var sum = 0.0
                for (m in 0 until numMelFilters) {
                    sum += melLog[m] * cos(PI * c * (m + 0.5) / numMelFilters)
                }
                (sum * sqrt(2.0 / numMelFilters)).toFloat()
            }
            mfccFrames.add(mfcc)

            // Spectral centroid & rolloff
            var weightedFreqSum = 0f
            var magSum = 1e-6f
            val binHz = sampleRateHz.toFloat() / fftSize.toFloat()
            for (k in mag.indices) {
                weightedFreqSum += k * binHz * mag[k]
                magSum += mag[k]
            }
            centroids[idx] = (weightedFreqSum / magSum) / (sampleRateHz / 2f)

            var cumulative = 0f
            val target85 = magSum * 0.85f
            var rolloffBin = mag.lastIndex
            for (k in mag.indices) {
                cumulative += mag[k]
                if (cumulative >= target85) {
                    rolloffBin = k
                    break
                }
            }
            rolloffs[idx] = rolloffBin.toFloat() / mag.size.toFloat()

            prevMag?.let { prev ->
                var fDiff = 0f
                for (k in mag.indices) {
                    val d = mag[k] - prev[k]
                    if (d > 0f) fDiff += d * d
                }
                fluxSum += sqrt(fDiff)
            }
            prevMag = mag

            zcrs[idx] = AudioPreprocessor.zeroCrossingRate(frame)
            energies[idx] = AudioPreprocessor.frameEnergy(frame)
            pitches[idx] = estimateNormalizedPitchProxy(frame, sampleRateHz)
        }

        val embedding = FloatArray(embeddingDimension)

        // 0..19 MFCC Mean
        for (c in 0 until numMfcc) {
            var sum = 0f
            for (f in mfccFrames) sum += f[c]
            embedding[c] = sum / mfccFrames.size
        }

        // 20..39 MFCC StdDev
        for (c in 0 until numMfcc) {
            val mean = embedding[c]
            var varSum = 0f
            for (f in mfccFrames) {
                val d = f[c] - mean
                varSum += d * d
            }
            embedding[20 + c] = sqrt(varSum / mfccFrames.size)
        }

        // 40..55 First 16 Mel bands mean
        for (m in 0 until 16) {
            var sum = 0f
            for (f in melLogFrames) sum += f[m]
            embedding[40 + m] = (sum / melLogFrames.size) * 0.1f
        }

        // 56..63 Spectral & Prosodic statistics
        embedding[56] = centroids.average().toFloat()
        embedding[57] = stdDev(centroids)
        embedding[58] = rolloffs.average().toFloat()
        embedding[59] = (fluxSum / voicedFrames.size.coerceAtLeast(1)) * 0.05f
        embedding[60] = pitches.average().toFloat()
        embedding[61] = stdDev(pitches)
        embedding[62] = zcrs.average().toFloat()
        embedding[63] = stdDev(energies) * 4f

        return l2Normalize(embedding)
    }

    private fun estimateNormalizedPitchProxy(frame: FloatArray, sampleRateHz: Int): Float {
        // Autocorrelation pitch search between 70 Hz and 350 Hz
        val minLag = (sampleRateHz / 350).coerceAtLeast(16)
        val maxLag = (sampleRateHz / 70).coerceAtMost(frame.size - 1)
        if (maxLag <= minLag) return 0f
        var bestLag = minLag
        var bestCorr = Float.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            var corr = 0f
            for (i in 0 until frame.size - lag) {
                corr += frame[i] * frame[i + lag]
            }
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        val f0 = sampleRateHz.toFloat() / bestLag.toFloat()
        return ((f0 - 70f) / 280f).coerceIn(0f, 1f)
    }

    private fun stdDev(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        var sum = 0f
        for (v in values) {
            val d = v - mean
            sum += d * d
        }
        return sqrt(sum / values.size)
    }

    private fun buildMelFilterBank(
        sampleRateHz: Int,
        fftSize: Int,
        numFilters: Int
    ): Array<FloatArray> {
        val lowMel = hzToMel(80f)
        val highMel = hzToMel(sampleRateHz / 2f)
        val melPoints = FloatArray(numFilters + 2) { i ->
            lowMel + (highMel - lowMel) * i / (numFilters + 1)
        }
        val hzPoints = FloatArray(melPoints.size) { i -> melToHz(melPoints[i]) }
        val bins = IntArray(hzPoints.size) { i ->
            ((fftSize + 1) * hzPoints[i] / sampleRateHz).toInt().coerceIn(0, fftSize / 2)
        }

        val numBins = fftSize / 2 + 1
        return Array(numFilters) { m ->
            FloatArray(numBins) { k ->
                when {
                    k in bins[m] until bins[m + 1] && bins[m + 1] > bins[m] ->
                        (k - bins[m]).toFloat() / (bins[m + 1] - bins[m]).toFloat()
                    k in bins[m + 1]..bins[m + 2] && bins[m + 2] > bins[m + 1] ->
                        (bins[m + 2] - k).toFloat() / (bins[m + 2] - bins[m + 1]).toFloat()
                    else -> 0f
                }
            }
        }
    }

    private fun hzToMel(hz: Float): Float = (2595.0 * log10(1.0 + hz / 700.0)).toFloat()
    private fun melToHz(mel: Float): Float = (700.0 * (10.0.pow(mel / 2595.0) - 1.0)).toFloat()

    companion object {
        fun l2Normalize(vec: FloatArray): FloatArray {
            var normSq = 0.0
            for (v in vec) normSq += v * v
            val norm = sqrt(normSq).toFloat()
            if (norm < 1e-8f) return vec
            return FloatArray(vec.size) { i -> vec[i] / norm }
        }
    }
}
