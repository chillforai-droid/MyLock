package com.example.mylock.audio

import com.example.mylock.core.model.AudioQualityReport
import com.example.mylock.core.model.QualityRejectionReason
import kotlin.math.log10

data class VadResult(
    val voicedFrames: List<FloatArray>,
    val qualityReport: AudioQualityReport
)

class VoiceActivityDetector(
    private val minTotalDurationMs: Long = 1200L,
    private val minActiveSpeechMs: Long = 700L,
    private val minSnrDb: Float = 8.0f,
    private val maxClippingRatio: Float = 0.08f
) {
    fun analyze(
        rawSamples: FloatArray,
        preprocessed: PreprocessedAudio,
        hardwareAvailable: Boolean = true,
        permissionGranted: Boolean = true
    ): VadResult {
        if (!hardwareAvailable) {
            return VadResult(
                voicedFrames = emptyList(),
                qualityReport = AudioQualityReport(
                    totalDurationMs = 0L,
                    activeSpeechMs = 0L,
                    snrDb = 0f,
                    peakAmplitude = 0f,
                    rmsEnergy = 0f,
                    clippingRatio = 0f,
                    isAcceptable = false,
                    rejectionReason = QualityRejectionReason.NO_MICROPHONE
                )
            )
        }
        if (!permissionGranted) {
            return VadResult(
                voicedFrames = emptyList(),
                qualityReport = AudioQualityReport(
                    totalDurationMs = 0L,
                    activeSpeechMs = 0L,
                    snrDb = 0f,
                    peakAmplitude = 0f,
                    rmsEnergy = 0f,
                    clippingRatio = 0f,
                    isAcceptable = false,
                    rejectionReason = QualityRejectionReason.PERMISSION_DENIED
                )
            )
        }

        val totalDurationMs = if (preprocessed.sampleRateHz > 0) {
            (rawSamples.size * 1000L) / preprocessed.sampleRateHz
        } else {
            0L
        }

        if (totalDurationMs < minTotalDurationMs || preprocessed.frames.isEmpty()) {
            return VadResult(
                voicedFrames = emptyList(),
                qualityReport = AudioQualityReport(
                    totalDurationMs = totalDurationMs,
                    activeSpeechMs = 0L,
                    snrDb = 0f,
                    peakAmplitude = preprocessed.peakAmplitude,
                    rmsEnergy = preprocessed.rmsEnergy,
                    clippingRatio = preprocessed.clippingRatio,
                    isAcceptable = false,
                    rejectionReason = QualityRejectionReason.RECORDING_TOO_SHORT
                )
            )
        }

        if (preprocessed.rmsEnergy < 0.003f || preprocessed.peakAmplitude < 0.015f) {
            return VadResult(
                voicedFrames = emptyList(),
                qualityReport = AudioQualityReport(
                    totalDurationMs = totalDurationMs,
                    activeSpeechMs = 0L,
                    snrDb = 0f,
                    peakAmplitude = preprocessed.peakAmplitude,
                    rmsEnergy = preprocessed.rmsEnergy,
                    clippingRatio = preprocessed.clippingRatio,
                    isAcceptable = false,
                    rejectionReason = QualityRejectionReason.SILENT_INPUT
                )
            )
        }

        if (preprocessed.clippingRatio > maxClippingRatio) {
            return VadResult(
                voicedFrames = emptyList(),
                qualityReport = AudioQualityReport(
                    totalDurationMs = totalDurationMs,
                    activeSpeechMs = 0L,
                    snrDb = 0f,
                    peakAmplitude = preprocessed.peakAmplitude,
                    rmsEnergy = preprocessed.rmsEnergy,
                    clippingRatio = preprocessed.clippingRatio,
                    isAcceptable = false,
                    rejectionReason = QualityRejectionReason.AUDIO_CLIPPING
                )
            )
        }

        val vadSourceFrames = if (preprocessed.rawFrames.size == preprocessed.frames.size) {
            preprocessed.rawFrames
        } else {
            preprocessed.frames
        }
        val energies = vadSourceFrames.map { AudioPreprocessor.frameEnergy(it) }
        val zcrs = vadSourceFrames.map { AudioPreprocessor.zeroCrossingRate(it) }
        val sortedEnergies = energies.sorted()

        val p10 = sortedEnergies[(sortedEnergies.size * 0.10).toInt().coerceIn(0, sortedEnergies.lastIndex)]
        val p90 = sortedEnergies[(sortedEnergies.size * 0.90).toInt().coerceIn(0, sortedEnergies.lastIndex)]
        val meanZcr = zcrs.average().toFloat()

        // Pure wideband / white noise shows high ZCR (> 0.40) and flat temporal envelope
        val isStationaryNoise = meanZcr > 0.40f && (p90 / p10.coerceAtLeast(1e-5f)) < 2.2f

        val silentFrames = energies.filter { it < 0.035f }
        val noiseFloor = when {
            isStationaryNoise -> p90.coerceAtLeast(1e-5f)
            silentFrames.size >= (energies.size * 0.15f).toInt() ->
                silentFrames.average().toFloat().coerceAtLeast(1e-5f)
            else ->
                (p10 * 0.30f).coerceAtLeast(1e-5f)
        }

        val energyThreshold = (noiseFloor * 2.0f).coerceIn(0.025f, 0.25f)

        val voicedFrames = mutableListOf<FloatArray>()
        val voicedEnergies = mutableListOf<Float>()

        preprocessed.frames.forEachIndexed { idx, frame ->
            val energy = energies[idx]
            val zcr = zcrs[idx]
            if (energy >= energyThreshold && zcr in 0.005f..0.40f) {
                voicedFrames.add(frame)
                voicedEnergies.add(energy)
            }
        }

        val activeSpeechMs = voicedFrames.size * 10L // 10ms hop size
        val speechRms = if (voicedEnergies.isNotEmpty()) {
            voicedEnergies.average().toFloat()
        } else {
            noiseFloor
        }
        val snrDb = if (isStationaryNoise) {
            2.0f
        } else {
            (20f * log10((speechRms / noiseFloor).coerceAtLeast(1f))).coerceIn(0f, 60f)
        }

        val rejection = when {
            isStationaryNoise || snrDb < minSnrDb -> QualityRejectionReason.TOO_NOISY
            activeSpeechMs < minActiveSpeechMs -> QualityRejectionReason.INSUFFICIENT_SPEECH_ACTIVITY
            else -> null
        }

        return VadResult(
            voicedFrames = voicedFrames,
            qualityReport = AudioQualityReport(
                totalDurationMs = totalDurationMs,
                activeSpeechMs = activeSpeechMs,
                snrDb = snrDb,
                peakAmplitude = preprocessed.peakAmplitude,
                rmsEnergy = preprocessed.rmsEnergy,
                clippingRatio = preprocessed.clippingRatio,
                isAcceptable = rejection == null,
                rejectionReason = rejection
            )
        )
    }
}
