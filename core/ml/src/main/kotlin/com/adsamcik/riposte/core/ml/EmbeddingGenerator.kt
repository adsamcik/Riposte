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
     * Checks if the embedding model is ready.
     */
    suspend fun isReady(): Boolean

    /**
     * Initializes the embedding model.
     */
    suspend fun initialize()

    /**
     * Releases resources.
     */
    fun close()
}
