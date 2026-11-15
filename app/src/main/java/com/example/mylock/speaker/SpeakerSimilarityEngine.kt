package com.example.mylock.speaker

import com.example.mylock.core.model.SecurityLevel
import kotlin.math.sqrt

data class SimilarityEvaluation(
    val cosineSimilarity: Float,
    val normalizedScore: Float,
    val threshold: Float,
    val isMatch: Boolean,
    val confidenceLabel: String
)

class SpeakerSimilarityEngine {

    fun fuseEmbeddings(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Cannot fuse empty embeddings list" }
        val dim = embeddings.first().size
        val sum = FloatArray(dim)
        for (emb in embeddings) {
            require(emb.size == dim) { "Embedding dimension mismatch" }
            for (i in 0 until dim) {
                sum[i] += emb[i]
            }
        }
        val avg = FloatArray(dim) { i -> sum[i] / embeddings.size }
        return AcousticSpectralEmbeddingEngine.l2Normalize(avg)
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        if (denom < 1e-8) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    fun evaluate(
        enrolledEmbedding: FloatArray,
        probeEmbedding: FloatArray,
        securityLevel: SecurityLevel
    ): SimilarityEvaluation {
        val cosine = cosineSimilarity(enrolledEmbedding, probeEmbedding)
        // Map cosine [-1, 1] -> [0, 1] while keeping positive cosine intuitive
        val score = cosine.coerceIn(0f, 1f)
        val threshold = securityLevel.threshold
        val isMatch = score >= threshold
        val label = when {
            score >= threshold + 0.08f -> "Strong Acoustic Match"
            score >= threshold -> "Threshold Match"
            score >= threshold - 0.08f -> "Borderline / Rejected"
            else -> "Distinct Speaker / Rejected"
        }
        return SimilarityEvaluation(
            cosineSimilarity = cosine,
            normalizedScore = score,
            threshold = threshold,
            isMatch = isMatch,
            confidenceLabel = label
        )
    }
}
