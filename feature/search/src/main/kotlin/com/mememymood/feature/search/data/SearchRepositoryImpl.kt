package com.mememymood.feature.search.data

import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeEmbeddingDao
import com.mememymood.core.database.dao.MemeSearchDao
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.ml.MemeWithEmbedding
import com.mememymood.core.ml.SemanticSearchEngine
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.MatchType
import com.mememymood.core.model.Meme
import com.mememymood.core.model.SearchResult
import com.mememymood.feature.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val memeDao: MemeDao,
    private val memeSearchDao: MemeSearchDao,
    private val memeEmbeddingDao: MemeEmbeddingDao,
    private val emojiTagDao: EmojiTagDao,
    private val semanticSearchEngine: SemanticSearchEngine,
    private val preferencesDataStore: PreferencesDataStore,
) : SearchRepository {

    override fun searchMemes(query: String): Flow<List<SearchResult>> {
        if (query.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        
        val ftsQuery = prepareFtsQuery(query)
        return memeSearchDao.searchMemes(ftsQuery).map { entities ->
            entities.mapIndexed { index, entity ->
                val relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f)
                val matchType = determineMatchType(entity, query)
                SearchResult(
                    meme = entity.toDomain(),
                    relevanceScore = relevanceScore,
                    matchType = matchType
                )
            }
        }
    }

    override fun searchByText(query: String): Flow<List<SearchResult>> {
        return searchMemes(query)
    }

    override suspend fun searchSemantic(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        // Use the new embedding table for better performance
        val memesWithEmbeddings = memeEmbeddingDao.getMemesWithEmbeddings()

        if (memesWithEmbeddings.isEmpty()) {
            return emptyList()
        }

        val candidates = memesWithEmbeddings.mapNotNull { data ->
            val embeddingBytes = data.embedding ?: return@mapNotNull null
            
            val meme = Meme(
                id = data.memeId,
                filePath = data.filePath,
                fileName = data.fileName,
                mimeType = "image/jpeg", // Default, could be stored
                width = 0,
                height = 0,
                fileSizeBytes = 0,
                importedAt = 0,
                emojiTags = data.emojiTagsJson
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { EmojiTag.fromEmoji(it.trim()) },
                title = data.title,
                description = data.description,
                textContent = data.textContent,
            )
            
            MemeWithEmbedding(
                meme = meme,
                embedding = decodeEmbedding(embeddingBytes)
            )
        }

        return semanticSearchEngine.findSimilar(
            query = query,
            candidates = candidates,
            limit = limit,
        )
    }

    override suspend fun searchHybrid(query: String, limit: Int): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val prefs = preferencesDataStore.appPreferences.first()

        // Perform FTS search
        val ftsResults = searchMemes(query).first()

        // Perform semantic search if enabled
        val semanticResults = if (prefs.enableSemanticSearch) {
            searchSemantic(query, limit)
        } else {
            emptyList()
        }

        return mergeResults(ftsResults, semanticResults).take(limit)
    }

    override fun searchByEmoji(emoji: String): Flow<List<SearchResult>> {
        val emojiQuery = "emojiTagsJson:$emoji"
        return memeSearchDao.searchByEmoji(emojiQuery).map { entities ->
            entities.mapIndexed { index, entity ->
                val relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f)
                SearchResult(
                    meme = entity.toDomain(),
                    relevanceScore = relevanceScore,
                    matchType = MatchType.EMOJI
                )
            }
        }
    }

    override suspend fun getSearchSuggestions(prefix: String): List<String> {
        if (prefix.isBlank()) return emptyList()
        return memeSearchDao.getSearchSuggestions(prefix)
    }

    override fun getRecentSearches(): Flow<List<String>> {
        return preferencesDataStore.recentSearches
    }

    override suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        preferencesDataStore.addRecentSearch(query.trim())
    }

    override suspend fun deleteRecentSearch(query: String) {
        preferencesDataStore.deleteRecentSearch(query)
    }

    override suspend fun clearRecentSearches() {
        preferencesDataStore.clearRecentSearches()
    }

    /**
     * Sanitize and prepare a query for FTS4 MATCH clause.
     * Removes special characters and operators to prevent injection.
     */
    private fun prepareFtsQuery(query: String): String {
        // Remove FTS special characters and operators
        val sanitized = query
            .replace(Regex("[\"*():]"), "")  // Remove special chars
            .replace(Regex("\\b(OR|AND|NOT|NEAR)\\b", RegexOption.IGNORE_CASE), "")  // Remove operators
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length >= 2 }  // Filter short terms
            .take(10)  // Limit terms to prevent DoS

        if (sanitized.isEmpty()) return ""

        // Quote each term for safety and add prefix wildcard
        return sanitized.joinToString(" OR ") { "\"$it\"*" }
    }

    /**
     * Prepare a query for title and description FTS search.
     * Uses column-specific filtering.
     */
    private fun prepareTitleDescQuery(query: String): String {
        val sanitized = query
            .replace(Regex("[\"*():]"), "")
            .replace(Regex("\\b(OR|AND|NOT|NEAR)\\b", RegexOption.IGNORE_CASE), "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.length >= 2 }
            .take(10)

        if (sanitized.isEmpty()) return ""

        return sanitized.joinToString(" OR ") { term ->
            "title:\"$term\"* OR description:\"$term\"*"
        }
    }

    private fun determineMatchType(entity: com.mememymood.core.database.entity.MemeEntity, query: String): MatchType {
        return when {
            entity.title?.contains(query, ignoreCase = true) == true -> MatchType.TEXT
            entity.description?.contains(query, ignoreCase = true) == true -> MatchType.TEXT
            entity.emojiTagsJson.contains(query, ignoreCase = true) -> MatchType.EMOJI
            entity.textContent?.contains(query, ignoreCase = true) == true -> MatchType.TEXT
            else -> MatchType.TEXT
        }
    }

    private fun decodeEmbedding(bytes: ByteArray): FloatArray {
        val floatArray = FloatArray(bytes.size / 4)
        java.nio.ByteBuffer.wrap(bytes)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()
            .get(floatArray)
        return floatArray
    }

    private fun mergeResults(
        ftsResults: List<SearchResult>,
        semanticResults: List<SearchResult>,
    ): List<SearchResult> {
        val resultMap = mutableMapOf<Long, SearchResult>()

        // Add FTS results with weight
        ftsResults.forEach { result ->
            resultMap[result.meme.id] = result.copy(
                relevanceScore = result.relevanceScore * FTS_WEIGHT
            )
        }

        // Merge semantic results
        semanticResults.forEach { result ->
            val existing = resultMap[result.meme.id]
            if (existing != null) {
                resultMap[result.meme.id] = existing.copy(
                    relevanceScore = existing.relevanceScore + (result.relevanceScore * SEMANTIC_WEIGHT),
                    matchType = MatchType.HYBRID,
                )
            } else {
                resultMap[result.meme.id] = result.copy(
                    relevanceScore = result.relevanceScore * SEMANTIC_WEIGHT
                )
            }
        }

        return resultMap.values.sortedByDescending { it.relevanceScore }
    }

    private fun com.mememymood.core.database.entity.MemeEntity.toDomain(): Meme {
        return Meme(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            title = title,
            description = description,
            emojiTags = emojiTagsJson.split(",")
                .filter { it.isNotEmpty() }
                .map { EmojiTag.fromEmoji(it.trim()) },
            textContent = textContent,
            isFavorite = isFavorite,
            createdAt = createdAt,
            useCount = useCount,
        )
    }

    override fun getEmojiCounts(): Flow<List<Pair<String, Int>>> {
        return emojiTagDao.getAllEmojisWithCounts().map { stats ->
            stats.map { it.emoji to it.count }
        }
    }

    companion object {
        private const val FTS_WEIGHT = 0.6f
        private const val SEMANTIC_WEIGHT = 0.4f
    }
}
