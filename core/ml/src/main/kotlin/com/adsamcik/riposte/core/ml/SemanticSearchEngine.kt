package com.adsamcik.riposte.core.ml

import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult

/**
 * Interface for semantic search using embeddings.
 */
interface SemanticSearchEngine {

    /**
     * Finds memes similar to the given query text.
     * 
     * @param query The search query text.
     * @param candidates List of memes with their embeddings to search through.
     * @param limit Maximum number of results to return.
     * @param threshold Minimum similarity score (0.0 to 1.0) for results.
     * @return List of search results sorted by relevance.
     */
    suspend fun findSimilar(
        query: String,
        candidates: List<MemeWithEmbedding>,
        limit: Int = 20,
        threshold: Float = 0.3f
    ): List<SearchResult>

    /**
     * Finds memes similar to the given query using max-pooling across multiple embedding slots.
     * For each meme, the highest cosine similarity across all slots is used as the score.
     *
     * @param query The search query text.
     * @param candidates List of memes with their multi-slot embeddings.
     * @param limit Maximum number of results to return.
     * @param threshold Minimum similarity score (0.0 to 1.0) for results.
     * @return List of search results sorted by relevance.
     */
    suspend fun findSimilarMultiVector(
        query: String,
        candidates: List<MemeWithEmbeddings>,
        limit: Int = 20,
        threshold: Float = 0.3f,
    ): List<SearchResult>

    /**
     * Calculates the cosine similarity between two embedding vectors.
     * 
     * @param embedding1 First embedding vector.
     * @param embedding2 Second embedding vector.
     * @return Similarity score from -1.0 to 1.0.
     */
    fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float

    /**
     * Checks if the search engine is ready.
     */
    suspend fun isReady(): Boolean

    /**
     * Initializes the search engine.
     */
    suspend fun initialize()

    /**
     * Releases resources.
     */
    fun close()
}

/**
 * A meme with its precomputed embedding vector.
 */
data class MemeWithEmbedding(
    val meme: Meme,
    val embedding: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemeWithEmbedding

        if (meme != other.meme) return false
        if (!embedding.contentEquals(other.embedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = meme.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

/**
 * A meme with multiple embedding slots for multi-vector search.
 * Each slot represents a different semantic aspect (content, intent, etc.).
 * Search uses max-pooling: the highest similarity across all slots wins.
 */
data class MemeWithEmbeddings(
    val meme: Meme,
    val embeddings: Map<String, FloatArray>,
)
