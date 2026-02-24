package com.adsamcik.riposte.core.ml

import kotlin.math.sqrt

/**
 * Shared utility functions for embedding vectors.
 */
object EmbeddingUtils {
    /**
     * Computes cosine similarity between two embedding vectors.
     *
     * @param embedding1 First embedding vector.
     * @param embedding2 Second embedding vector.
     * @return Cosine similarity score between -1 and 1.
     */
    fun cosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray,
    ): Float {
        require(embedding1.size == embedding2.size) {
            "Embeddings must have the same dimension: ${embedding1.size} vs ${embedding2.size}"
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val magnitude = sqrt(norm1) * sqrt(norm2)
        return if (magnitude.isFinite() && magnitude > 0f) dotProduct / magnitude else 0f
    }

    /**
     * L2-normalizes an embedding vector, returning a new array.
     *
     * @param embedding The embedding vector to normalize.
     * @return A new normalized embedding vector with unit length.
     */
    fun normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in embedding) {
            sumSquares += value * value
        }
        val norm = sqrt(sumSquares)

        if (norm <= 0f) return embedding.copyOf()

        return FloatArray(embedding.size) { i -> embedding[i] / norm }
    }

    /**
     * Truncates an embedding to a smaller dimension using Matryoshka Representation Learning.
     * The truncated embedding is re-normalized for optimal performance.
     *
     * @param embedding The original embedding vector.
     * @param targetDimension The target dimension (128, 256, 384, or 512).
     * @return The truncated and normalized embedding.
     */
    fun truncateEmbedding(
        embedding: FloatArray,
        targetDimension: Int,
    ): FloatArray {
        require(targetDimension <= embedding.size) {
            "Target dimension ($targetDimension) must be <= embedding size (${embedding.size})"
        }
        require(targetDimension in VALID_TRUNCATION_DIMENSIONS) {
            "Target dimension must be one of: $VALID_TRUNCATION_DIMENSIONS"
        }

        val truncated = embedding.copyOfRange(0, targetDimension)
        return normalize(truncated)
    }

    /** Valid dimensions for Matryoshka Representation Learning truncation. */
    private val VALID_TRUNCATION_DIMENSIONS = listOf(128, 256, 384, 512, 768)
}
