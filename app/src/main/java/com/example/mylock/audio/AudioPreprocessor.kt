package com.example.mylock.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class PreprocessedAudio(
    val samples: FloatArray,
    val frames: List<FloatArray>,
    val rawFrames: List<FloatArray>,
    val sampleRateHz: Int,
    val peakAmplitude: Float,
    val rmsEnergy: Float,
    val clippingRatio: Float
)

class AudioPreprocessor(
    private val frameSize: Int = 400, // 25ms at 16kHz
    private val hopSize: Int = 160    // 10ms at 16kHz
) {
    private val hammingWindow = FloatArray(frameSize) { n ->
        (0.54 - 0.46 * cos(2.0 * PI * n / (frameSize - 1))).toFloat()
    }

    fun process(rawSamples: FloatArray, sampleRateHz: Int = 16_000): PreprocessedAudio {
        if (rawSamples.isEmpty()) {
            return PreprocessedAudio(
                samples = FloatArray(0),
                frames = emptyList(),
                rawFrames = emptyList(),
                sampleRateHz = sampleRateHz,
                peakAmplitude = 0f,
                rmsEnergy = 0f,
                clippingRatio = 0f
            )
        }

        // 1. Measure raw clipping, peak, RMS, and DC offset
        var mean = 0.0
        var clippingCount = 0
        var rawPeak = 0f
        var sumSq = 0.0
        for (sample in rawSamples) {
            mean += sample
            val absVal = abs(sample)
            if (absVal > rawPeak) rawPeak = absVal
            if (absVal >= 0.985f) clippingCount++
            sumSq += sample * sample
        }
        mean /= rawSamples.size
        val clippingRatio = clippingCount.toFloat() / rawSamples.size.toFloat()
        val rmsEnergy = sqrt(sumSq / rawSamples.size).toFloat()

        // 2. DC offset removal and normalized un-emphasized signal (for VAD & ZCR)
        val dcRemoved = FloatArray(rawSamples.size) { i -> rawSamples[i] - mean.toFloat() }
        var maxRawDc = 1e-6f
        for (v in dcRemoved) {
            val a = abs(v)
            if (a > maxRawDc) maxRawDc = a
        }
        val normalizedRaw = if (rmsEnergy > 0.002f) {
            FloatArray(dcRemoved.size) { i -> (dcRemoved[i] / maxRawDc).coerceIn(-1f, 1f) }
        } else {
            dcRemoved
        }

        // 3. Pre-emphasis filter (y[n] = x[n] - 0.97*x[n-1]) for MFCC spectral whitening
        val emphasized = FloatArray(dcRemoved.size)
        var maxEmph = 1e-6f
        for (i in dcRemoved.indices) {
            val prev = if (i == 0) 0f else dcRemoved[i - 1]
            val v = dcRemoved[i] - 0.97f * prev
            emphasized[i] = v
            val a = abs(v)
            if (a > maxEmph) maxEmph = a
        }

        val normalizedEmph = if (rmsEnergy > 0.002f) {
            FloatArray(emphasized.size) { i -> (emphasized[i] / maxEmph).coerceIn(-1f, 1f) }
        } else {
            emphasized
        }

        // 4. Hamming windowed framing for both pre-emphasized (MFCC) and raw (VAD)
        val emphFrames = mutableListOf<FloatArray>()
        val unemphFrames = mutableListOf<FloatArray>()
        var idx = 0
        while (idx + frameSize <= normalizedEmph.size) {
            val ef = FloatArray(frameSize) { k -> normalizedEmph[idx + k] * hammingWindow[k] }
            val rf = FloatArray(frameSize) { k -> normalizedRaw[idx + k] * hammingWindow[k] }
            emphFrames.add(ef)
            unemphFrames.add(rf)
            idx += hopSize
        }

        return PreprocessedAudio(
            samples = normalizedEmph,
            frames = emphFrames,
            rawFrames = unemphFrames,
            sampleRateHz = sampleRateHz,
            peakAmplitude = rawPeak,
            rmsEnergy = rmsEnergy,
            clippingRatio = clippingRatio
        )
    }

    companion object {
        fun fftMagnitudeSpectrum(frame: FloatArray, fftSize: Int = 512): FloatArray {
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)
            val copyLen = minOf(frame.size, fftSize)
            for (i in 0 until copyLen) {
                real[i] = frame[i].toDouble()
            }

            var j = 0
            for (i in 1 until fftSize) {
                var bit = fftSize shr 1
                while (j and bit != 0) {
                    j = j xor bit
                    bit = bit shr 1
                }
                j = j xor bit
                if (i < j) {
                    val tempR = real[i]
                    real[i] = real[j]
                    real[j] = tempR
                    val tempI = imag[i]
                    imag[i] = imag[j]
                    imag[j] = tempI
                }
            }

            var len = 2
            while (len <= fftSize) {
                val angle = -2.0 * PI / len
                val wLenR = cos(angle)
                val wLenI = sin(angle)
                var i = 0
                while (i < fftSize) {
                    var wR = 1.0
                    var wI = 0.0
                    for (k in 0 until len / 2) {
                        val uR = real[i + k]
                        val uI = imag[i + k]
                        val vR = real[i + k + len / 2] * wR - imag[i + k + len / 2] * wI
                        val vI = real[i + k + len / 2] * wI + imag[i + k + len / 2] * wR
                        real[i + k] = uR + vR
                        imag[i + k] = uI + vI
                        real[i + k + len / 2] = uR - vR
                        imag[i + k + len / 2] = uI - vI
                        val nextWR = wR * wLenR - wI * wLenI
                        wI = wR * wLenI + wI * wLenR
                        wR = nextWR
                    }
                    i += len
                }
                len = len shl 1
            }

            val half = fftSize / 2 + 1
            return FloatArray(half) { k ->
                sqrt(real[k] * real[k] + imag[k] * imag[k]).toFloat()
            }
        }

        fun frameEnergy(frame: FloatArray): Float {
            if (frame.isEmpty()) return 0f
            var sum = 0.0
            for (s in frame) sum += s * s
            return sqrt(sum / frame.size).toFloat()
        }

        fun zeroCrossingRate(frame: FloatArray): Float {
            if (frame.size < 2) return 0f
            var crossings = 0
            for (i in 1 until frame.size) {
                if ((frame[i - 1] >= 0f && frame[i] < 0f) || (frame[i - 1] < 0f && frame[i] >= 0f)) {
                    crossings++
                }
            }
            return crossings.toFloat() / (frame.size - 1).toFloat()
        }

        fun maxAbs(a: Float, b: Float): Float = max(abs(a), abs(b))
    }
}
