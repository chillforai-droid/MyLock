package com.example.mylock.ml

/**
 * Modular interface for Speaker Embedding Extraction models.
 *
 * Speech Recognition ("what was said") is strictly separated from
 * Speaker Verification ("who spoke it").
 *
 * Pretrained Neural Model Integration Guide:
 * To upgrade from the built-in offline DSP acoustic baseline to a neural speaker
 * embedding model (such as Wespeaker ResNet34, ECAPA-TDNN, or TitaNet exported
 * to TensorFlow Lite / ONNX Runtime Mobile):
 * 1. Bundle the properly licensed `.tflite` or `.onnx` weights in `app/src/main/assets/models/`
 *    and verify its SHA-256 digest before initialization.
 * 2. Implement [SpeakerEmbeddingExtractor] wrapping Interpreter / OrtSession.
 * 3. Input tensor: 16 kHz mono float PCM `[1, N]` or 80-bin log-Mel filterbank `[1, T, 80]`.
 * 4. Output tensor: L2-normalized speaker embedding `[1, embeddingDimension]` (e.g. 192 or 256).
 * 5. Increment [modelVersion] so existing profiles trigger controlled re-enrollment.
 */
interface SpeakerEmbeddingExtractor {
    val modelId: String
    val modelVersion: String
    val embeddingDimension: Int
    val isPretrainedNeuralModel: Boolean
    val licenseAndOriginNote: String

    fun extractEmbedding(
        voicedFrames: List<FloatArray>,
        sampleRateHz: Int = 16_000
    ): FloatArray
}

/**
 * Clean documented integration stub/adapter showing how a licensed TFLite/ONNX
 * neural model plugs into My Lock without fabricating accuracy or pretending
 * a neural model binary is bundled when none is present.
 */
class ExternalNeuralModelBridge(
    override val modelId: String = "ecapa_tdnn_external_bridge",
    override val modelVersion: String = "unbundled-0.0",
    override val embeddingDimension: Int = 192
) : SpeakerEmbeddingExtractor {
    override val isPretrainedNeuralModel: Boolean = true
    override val licenseAndOriginNote: String =
        "Requires externally supplied licensed ECAPA-TDNN / Wespeaker .onnx or .tflite model file."

    fun isModelAssetAvailable(): Boolean = false

    override fun extractEmbedding(voicedFrames: List<FloatArray>, sampleRateHz: Int): FloatArray {
        throw IllegalStateException(
            "External neural speaker model binary is not bundled. Integrate a licensed ONNX/TFLite model asset or use AcousticSpectralEmbeddingEngine."
        )
    }
}
