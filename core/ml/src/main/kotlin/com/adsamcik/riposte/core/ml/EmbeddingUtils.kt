package com.adsamcik.riposte.core.ml

import java.nio.ByteBuffer
import java.nio.ByteOrder
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

        if (!norm.isFinite() || norm <= 0f) return embedding.copyOf()

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

    /**
     * Quantizes a float32 embedding to int8 representation.
     * Uses symmetric min-max quantization: value → round((value / scale) * 127)
     * where scale = max(abs(min), abs(max)) of the embedding.
     *
     * Returns a Pair of (int8 ByteArray, scale Float) needed for dequantization.
     *
     * This provides ~4x storage reduction compared to float32.
     */
    fun quantizeToInt8(embedding: FloatArray): Pair<ByteArray, Float> {
        if (embedding.isEmpty()) return Pair(ByteArray(0), 1f)

        var maxAbs = 0f
        for (value in embedding) {
            val abs = if (value < 0) -value else value
            if (abs > maxAbs) maxAbs = abs
        }

        val scale = if (maxAbs > 0f) maxAbs else 1f
        val bytes = ByteArray(embedding.size)

        for (i in embedding.indices) {
            val quantized = (embedding[i] / scale * 127f).toInt().coerceIn(-128, 127)
            bytes[i] = quantized.toByte()
        }

        return Pair(bytes, scale)
    }

    /**
     * Dequantizes an int8 embedding back to float32.
     *
     * @param bytes The quantized int8 values.
     * @param scale The scale factor from quantization.
     * @return The reconstructed float32 embedding.
     */
    fun dequantizeFromInt8(bytes: ByteArray, scale: Float): FloatArray {
        val embedding = FloatArray(bytes.size)
        for (i in bytes.indices) {
            embedding[i] = (bytes[i].toFloat() / 127f) * scale
        }
        return embedding
    }

    /**
     * Encodes a float32 embedding to little-endian ByteArray for Room storage.
     */
    fun encodeFloat32(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        for (value in embedding) {
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    /**
     * Decodes a little-endian float32 ByteArray back to FloatArray.
     */
    fun decodeFloat32(bytes: ByteArray): FloatArray {
        val count = bytes.size / 4
        val array = FloatArray(count)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(array)
        return array
    }

    /** Valid dimensions for Matryoshka Representation Learning truncation. */
    private val VALID_TRUNCATION_DIMENSIONS = listOf(128, 256, 384, 512, 768)
}
