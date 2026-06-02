package com.adsamcik.riposte.core.ml.search

import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.ml.DeviceTierDetector
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import com.adsamcik.riposte.core.ml.EmbeddingUtils
import com.adsamcik.riposte.core.ml.MemeWithEmbeddings
import com.adsamcik.riposte.core.ml.RustVectorIndex
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Search strategy that uses AI embeddings for semantic similarity search.
 *
 * When the Rust native library is available, uses a two-stage Matryoshka retrieval:
 * 1. **First pass**: ANN search with 256d truncated embeddings (fast, USearch HNSW)
 * 2. **Rerank**: Full 768d cosine similarity on top-100 candidates (accurate)
 *
 * Falls back to brute-force scan when the native library or ANN index is unavailable.
 */
class SemanticSearchStrategy @Inject constructor(
    private val semanticSearchEngine: SemanticSearchEngine,
    private val memeEmbeddingDao: MemeEmbeddingDao,
    private val deviceTierDetector: DeviceTierDetector,
    private val embeddingGenerator: EmbeddingGenerator,
) : SearchStrategy {

    override val name = "semantic"
    override val priority = PRIORITY

    /** The ANN index for fast first-pass retrieval (256d truncated embeddings). */
    private var annIndex: RustVectorIndex? = null

    /** Whether we've attempted to build the ANN index. */
    private var indexBuildAttempted = false

    /** Cached decoded candidates to avoid re-querying Room on every search. */
    private var cachedCandidates: List<MemeWithEmbeddings>? = null

    override fun isAvailable(): Boolean = true

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        if (!semanticSearchEngine.isReady()) {
            Timber.d("Semantic search engine not ready, skipping")
            return emptyList()
        }

        val candidates = getCachedOrLoadCandidates()
        if (candidates.isEmpty()) {
            Timber.d("No valid candidates")
            return emptyList()
        }

        Timber.d("Semantic search: %d candidates", candidates.size)

        // Try ANN two-stage retrieval, fall back to brute-force
        val results = tryAnnSearch(query, candidates, limit)
            ?: semanticSearchEngine.findSimilarMultiVector(
                query = query,
                candidates = candidates,
                limit = limit,
            )

        Timber.d("Semantic search returned %d results for query: %s", results.size, query)
        return results
    }

    /**
     * Attempts two-stage Matryoshka retrieval:
     * 1. Generate query embedding, truncate to ANN dimension
     * 2. ANN search top-K with truncated index
     * 3. Rerank with full 768d brute-force on the K candidates
     *
     * @return Results if ANN index is available, null to fall back to brute-force.
     */
    private suspend fun tryAnnSearch(
        query: String,
        allCandidates: List<MemeWithEmbeddings>,
        limit: Int,
    ): List<SearchResult>? {
        val profile = deviceTierDetector.resolveProfile()
        return if (!profile.useAnn) {
            null
        } else {
            val index = getOrBuildIndex(allCandidates)?.takeIf { it.size() > 0 }
            val queryEmbedding = generateAnnQueryEmbedding(query)
            val annKeys = if (index != null && queryEmbedding != null) {
                val truncatedQuery = EmbeddingUtils.truncateEmbedding(
                    queryEmbedding,
                    profile.annIndexDimension,
                )
                searchAnnKeys(index, truncatedQuery, profile.annFirstPassK)
            } else {
                null
            }
            annKeys
                ?.takeIf { it.isNotEmpty() }
                ?.let { rerankAnnCandidates(query, allCandidates, it, limit) }
        }
    }

    private suspend fun generateAnnQueryEmbedding(query: String): FloatArray? =
        try {
            embeddingGenerator.generateFromQuery(query)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            Timber.w(e, "Failed to generate query embedding for ANN search")
            null
        }

    private fun searchAnnKeys(
        index: RustVectorIndex,
        truncatedQuery: FloatArray,
        firstPassCount: Int,
    ): LongArray? =
        try {
            index.search(truncatedQuery, firstPassCount).first
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            Timber.w(e, "ANN search failed, falling back to brute-force")
            null
        }

    private suspend fun rerankAnnCandidates(
        query: String,
        allCandidates: List<MemeWithEmbeddings>,
        annKeys: LongArray,
        limit: Int,
    ): List<SearchResult> {
        val annMemeIds = annKeys.toSet()
        val narrowedCandidates = allCandidates.filter { candidate ->
            val key = candidate.meme.id.hashCode().toLong() and KEY_MASK
            key in annMemeIds
        }

        Timber.d(
            "ANN search: %d/%d candidates selected, reranking with full embeddings",
            narrowedCandidates.size,
            allCandidates.size,
        )

        return semanticSearchEngine.findSimilarMultiVector(
            query = query,
            candidates = narrowedCandidates,
            limit = limit,
        )
    }

    /** Invalidate cached candidates when embeddings change. */
    fun invalidateCandidateCache() {
        cachedCandidates = null
        annIndex = null
        indexBuildAttempted = false
    }

    override fun invalidateCache() {
        invalidateCandidateCache()
    }

    private suspend fun getCachedOrLoadCandidates(): List<MemeWithEmbeddings> =
        cachedCandidates ?: memeEmbeddingDao.getMemesWithEmbeddings()
            .takeIf { it.isNotEmpty() }
            ?.let { rows ->
                buildCandidates(rows).also { cachedCandidates = it }
            }
            .orEmpty()

    /**
     * Gets or lazily builds the ANN index from current embeddings.
     */
    private fun getOrBuildIndex(
        candidates: List<MemeWithEmbeddings>,
    ): RustVectorIndex? =
        annIndex ?: when {
            indexBuildAttempted -> null
            !RustVectorIndex.isAvailable() -> {
                indexBuildAttempted = true
                Timber.d("Rust native library not available, skipping ANN index")
                null
            }
            else -> buildAnnIndex(candidates)
        }

    @Suppress("TooGenericExceptionCaught")
    private fun buildAnnIndex(candidates: List<MemeWithEmbeddings>): RustVectorIndex? {
        indexBuildAttempted = true
        return try {
            val dimensions = deviceTierDetector.resolveProfile().annIndexDimension
            val index = RustVectorIndex.create(dimensions)
            index.reserve(candidates.size)

            var added = 0
            for (candidate in candidates) {
                val fullEmbedding = candidate.embeddings["content"]
                if (fullEmbedding != null && fullEmbedding.size >= dimensions) {
                    val truncated = EmbeddingUtils.truncateEmbedding(fullEmbedding, dimensions)
                    val key = candidate.meme.id.hashCode().toLong() and KEY_MASK
                    index.add(key, truncated)
                    added++
                }
            }

            Timber.i("Built ANN index: %d vectors at %dd", added, dimensions)
            annIndex = index
            index
        } catch (e: Exception) {
            Timber.w(e, "Failed to build ANN index, using brute-force fallback")
            null
        }
    }

    @Suppress("NestedBlockDepth")
    private fun buildCandidates(
        rows: List<com.adsamcik.riposte.core.database.entity.MemeWithEmbeddingData>,
    ): List<MemeWithEmbeddings> {
        val groupedByMeme = rows
            .filter { it.embedding != null }
            .groupBy { it.memeId }

        return groupedByMeme.map { (_, memeRows) ->
            val first = memeRows.first()
            val meme = Meme(
                id = first.memeId,
                filePath = first.filePath,
                fileName = first.fileName,
                mimeType = "image/jpeg",
                width = 0,
                height = 0,
                fileSizeBytes = 0,
                importedAt = 0,
                emojiTags = MemeMapper.parseEmojiTagsJson(first.emojiTagsJson),
                title = first.title,
                description = first.description,
                textContent = first.textContent,
                useCount = first.useCount,
                viewCount = first.viewCount,
                isFavorite = first.isFavorite,
            )

            val embeddingsByType = memeRows
                .filter { it.embedding != null && it.embeddingType != null }
                .mapNotNull { row ->
                    val embedding = row.embedding ?: return@mapNotNull null
                    val decoded = decodeEmbedding(embedding)
                    if (decoded.size < 2) {
                        Timber.w("Skipping embedding with invalid dimensions: %d", decoded.size)
                        return@mapNotNull null
                    }
                    (row.embeddingType ?: "content") to decoded
                }
                .toMap()

            MemeWithEmbeddings(meme = meme, embeddings = embeddingsByType)
        }
    }

    private fun decodeEmbedding(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / BYTES_PER_FLOAT)
        ByteBuffer.wrap(bytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(floatArray)
        return floatArray
    }

    companion object {
        const val PRIORITY = 200
        private const val BYTES_PER_FLOAT = 4

        /** Mask to ensure positive key values for USearch. */
        private const val KEY_MASK = 0x7FFFFFFFFFFFFFFFL
    }
}
