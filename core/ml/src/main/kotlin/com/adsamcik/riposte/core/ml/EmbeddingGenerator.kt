package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.net.Uri

/**
 * Interface for generating embeddings for semantic search.
 */
interface EmbeddingGenerator {
    /**
     * Generates an embedding vector from text.
     *
     * @param text The text to generate an embedding for.
     * @return A float array representing the embedding vector.
     */
    suspend fun generateFromText(text: String): FloatArray

    /**
     * Generates an embedding vector from text with an optional title for context.
     * Models that support structured prompts (e.g. EmbeddingGemma) will format
     * the title and text into the model's document prompt format.
     *
     * @param text The text content to generate an embedding for.
     * @param title Optional title providing additional context.
     * @return A float array representing the embedding vector.
     */
    suspend fun generateFromText(
        text: String,
        title: String?,
    ): FloatArray = generateFromText(text)

    /**
     * Generates an embedding vector from an image.
     * This uses image labeling to extract features, then generates text embeddings.
     *
     * @param bitmap The image bitmap to generate an embedding for.
     * @return A float array representing the embedding vector.
     */
    suspend fun generateFromImage(bitmap: Bitmap): FloatArray

    /**
     * Generates an embedding vector from an image URI.
     *
     * @param uri The URI of the image.
     * @return A float array representing the embedding vector.
     */
    suspend fun generateFromUri(uri: Uri): FloatArray

    /**
     * Returns the dimension of embedding vectors.
     */
    val embeddingDimension: Int

    /**
     * Returns a user-facing error message if model initialization failed, null otherwise.
     */
    val initializationError: String?
        get() = null

    /**
     * Checks if the embedding model is ready.
     */
    suspend fun isReady(): Boolean

    /**
     * Initializes the embedding model.
     */
    suspend fun initialize()

    /**
     * Generates an embedding vector from a search query.
     * Models that support asymmetric prompts (e.g. EmbeddingGemma) will format the
     * query with a task-specific prompt prefix for better retrieval accuracy.
     *
     * @param query The search query text.
     * @return A float array representing the embedding vector.
     */
    suspend fun generateFromQuery(query: String): FloatArray = generateFromText(query)

    /**
     * Releases resources.
     */
    fun close()
}
