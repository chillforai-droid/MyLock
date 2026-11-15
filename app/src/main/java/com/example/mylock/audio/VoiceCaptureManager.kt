package com.example.mylock.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

data class CapturedAudioResult(
    val samples: FloatArray,
    val sampleRateHz: Int,
    val durationMs: Long,
    val hardwareAvailable: Boolean,
    val permissionGranted: Boolean,
    val errorMessage: String? = null
)

interface AudioCaptureSource {
    val sampleRateHz: Int
    val liveAmplitude: StateFlow<Float>
    fun isMicrophoneAvailable(): Boolean
    fun hasRecordPermission(): Boolean
    suspend fun startCapture(gainMultiplier: Float = 1.0f): Boolean
    suspend fun stopCapture(): CapturedAudioResult
    fun cancelCapture()
}

class VoiceCaptureManager(
    private val context: Context,
    override val sampleRateHz: Int = 16_000
) : AudioCaptureSource {

    private val _liveAmplitude = MutableStateFlow(0f)
    override val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val capturedChunks = mutableListOf<FloatArray>()
    private var captureStartTimeMs: Long = 0L

    override fun isMicrophoneAvailable(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)

    override fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun startCapture(gainMultiplier: Float): Boolean {
        if (!isMicrophoneAvailable() || !hasRecordPermission()) {
            return false
        }
        cancelCapture()

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            return false
        }

        val bufferSize = (minBufferSize * 2).coerceAtLeast(sampleRateHz / 2)
        return try {
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRateHz,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                return false
            }

            synchronized(capturedChunks) {
                capturedChunks.clear()
            }
            audioRecord = recorder
            captureStartTimeMs = System.currentTimeMillis()
            recorder.startRecording()

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val shortBuffer = ShortArray(1024)
                while (isActive && recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val readCount = recorder.read(shortBuffer, 0, shortBuffer.size)
                    if (readCount > 0) {
                        val floatChunk = FloatArray(readCount)
                        var sumSquares = 0.0
                        for (i in 0 until readCount) {
                            val normalized = ((shortBuffer[i] / 32768f) * gainMultiplier)
                                .coerceIn(-1f, 1f)
                            floatChunk[i] = normalized
                            sumSquares += normalized * normalized
                        }
                        synchronized(capturedChunks) {
                            capturedChunks.add(floatChunk)
                        }
                        val rms = sqrt(sumSquares / readCount).toFloat().coerceIn(0f, 1f)
                        _liveAmplitude.value = (rms * 4.5f).coerceIn(0f, 1f)
                    }
                }
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    override suspend fun stopCapture(): CapturedAudioResult {
        val hasHardware = isMicrophoneAvailable()
        val hasPerm = hasRecordPermission()
        if (!hasHardware || !hasPerm) {
            cancelCapture()
            return CapturedAudioResult(
                samples = FloatArray(0),
                sampleRateHz = sampleRateHz,
                durationMs = 0L,
                hardwareAvailable = hasHardware,
                permissionGranted = hasPerm,
                errorMessage = if (!hasHardware) "No microphone hardware detected." else "RECORD_AUDIO permission denied."
            )
        }

        val job = recordingJob
        recordingJob = null
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (_: IllegalStateException) {
        } finally {
            audioRecord = null
        }

        try {
            job?.cancel()
            job?.join()
        } catch (_: CancellationException) {
        }
        _liveAmplitude.value = 0f

        val combined = synchronized(capturedChunks) {
            val totalSize = capturedChunks.sumOf { it.size }
            val result = FloatArray(totalSize)
            var offset = 0
            for (chunk in capturedChunks) {
                chunk.copyInto(result, offset)
                offset += chunk.size
            }
            capturedChunks.clear()
            result
        }

        val durationMs = if (sampleRateHz > 0) {
            (combined.size * 1000L) / sampleRateHz
        } else {
            0L
        }

        return CapturedAudioResult(
            samples = combined,
            sampleRateHz = sampleRateHz,
            durationMs = durationMs,
            hardwareAvailable = true,
            permissionGranted = true
        )
    }

    override fun cancelCapture() {
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.let { rec ->
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
                rec.release()
            }
        } catch (_: IllegalStateException) {
        }
        audioRecord = null
        synchronized(capturedChunks) {
            capturedChunks.clear()
        }
        _liveAmplitude.value = 0f
    }
}
