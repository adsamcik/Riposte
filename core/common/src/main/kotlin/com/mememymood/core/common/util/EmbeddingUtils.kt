package com.mememymood.core.common.util

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt

/**
 * Utility functions for embedding operations.
 *
 * This object provides shared implementations for common embedding operations
 * to ensure consistency across the codebase and avoid code duplication.
 *
 * Usage:
 * ```kotlin
 * // Encode embedding to bytes for storage
 * val bytes = EmbeddingUtils.encodeEmbedding(floatArray)
 *
 * // Decode bytes back to embedding
 * val embedding = EmbeddingUtils.decodeEmbedding(bytes)
 *
 * // Calculate similarity between embeddings
 * val similarity = EmbeddingUtils.cosineSimilarity(embedding1, embedding2)
 *
 * // Generate hash for text
 * val hash = EmbeddingUtils.generateTextHash(text)
 * ```
 */
object EmbeddingUtils {

    /**
     * Encodes a float array embedding to a byte array for storage.
     *
     * Uses LITTLE_ENDIAN byte order for consistent cross-platform compatibility.
     *
     * @param embedding The embedding vector to encode.
     * @return The encoded byte array.
     */
    fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    /**
     * Decodes a byte array back to a float array embedding.
     *
     * @param bytes The byte array to decode.
     * @return The decoded embedding vector.
     */
    fun decodeEmbedding(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(floatArray)
        return floatArray
    }

    /**
     * Computes cosine similarity between two embeddings.
     *
     * Cosine similarity measures the cosine of the angle between two vectors,
     * resulting in a value between -1 (opposite) and 1 (identical).
     *
     * @param embedding1 First embedding vector.
     * @param embedding2 Second embedding vector.
     * @return Cosine similarity score between -1 and 1.
     * @throws IllegalArgumentException if embeddings have different dimensions.
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        require(embedding1.size == embedding2.size) {
            "Embedding dimensions must match: ${embedding1.size} vs ${embedding2.size}"
        }

        var dotProduct = 0f
        var norm1 = 0f
        var norm2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            norm1 += embedding1[i] * embedding1[i]
            norm2 += embedding2[i] * embedding2[i]
        }

        val denominator = sqrt(norm1) * sqrt(norm2)
        return if (denominator > 0f) dotProduct / denominator else 0f
    }

    /**
     * Generates a SHA-256 hash for text, truncated to 32 hex characters.
     *
     * This is used for tracking source text changes to detect when embeddings
     * need regeneration.
     *
     * @param text The text to hash.
     * @return A 32-character hex string (128 bits of the SHA-256 hash).
     */
    fun generateTextHash(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        // Truncate to 32 chars (128 bits) for storage efficiency while maintaining uniqueness
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }
}
