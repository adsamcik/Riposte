package com.adsamcik.riposte.core.search.data

import android.util.Log
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.database.util.FtsQuerySanitizer
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.MemeWithEmbeddings
import com.adsamcik.riposte.core.ml.SemanticSearchEngine
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.search.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SearchRepositoryImpl
    @Inject
    constructor(
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
                val results =
                    entities.mapIndexed { index, entity ->
                        val relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f)
                        val matchType = determineMatchType(entity, query)
                        SearchResult(
                            meme = entity.toDomain(),
                            relevanceScore = relevanceScore,
                            matchType = matchType,
                        )
                    }
                prioritizeFavorites(results)
            }
        }

        override fun searchByText(query: String): Flow<List<SearchResult>> {
            return searchMemes(query)
        }

        override suspend fun searchSemantic(
            query: String,
            limit: Int,
        ): List<SearchResult> {
            if (query.isBlank()) return emptyList()

            val memesWithEmbeddings = memeEmbeddingDao.getMemesWithEmbeddings()

            if (memesWithEmbeddings.isEmpty()) {
                return emptyList()
            }

            // Group embedding rows by memeId for multi-vector max-pooling
            val groupedByMeme =
                memesWithEmbeddings
                    .filter { it.embedding != null }
                    .groupBy { it.memeId }

            val candidates =
                groupedByMeme.map { (_, rows) ->
                    val first = rows.first()
                    val meme =
                        Meme(
                            id = first.memeId,
                            filePath = first.filePath,
                            fileName = first.fileName,
                            mimeType = "image/jpeg",
                            width = 0,
                            height = 0,
                            fileSizeBytes = 0,
                            importedAt = 0,
                            emojiTags =
                                MemeMapper.parseEmojiTagsJson(first.emojiTagsJson),
                            title = first.title,
                            description = first.description,
                            textContent = first.textContent,
                        )

                    val embeddingsByType =
                        rows
                            .filter { it.embedding != null && it.embeddingType != null }
                            .mapNotNull { row ->
                                val decoded = decodeEmbedding(row.embedding!!)
                                if (decoded.size < 2) {
                                    Log.w(TAG, "Skipping embedding with invalid dimensions: ${decoded.size}")
                                    return@mapNotNull null
                                }
                                val type = row.embeddingType ?: "content"
                                type to decoded
                            }
                            .toMap()

                    MemeWithEmbeddings(meme = meme, embeddings = embeddingsByType)
                }

            return semanticSearchEngine.findSimilarMultiVector(
                query = query,
                candidates = candidates,
                limit = limit,
            )
        }

        override suspend fun searchHybrid(
            query: String,
            limit: Int,
        ): List<SearchResult> {
            if (query.isBlank()) return emptyList()

            val prefs = preferencesDataStore.appPreferences.first()

            val ftsResults = searchMemes(query).first()

            val semanticResults =
                if (prefs.enableSemanticSearch) {
                    searchSemantic(query, limit)
                } else {
                    emptyList()
                }

            return mergeResults(ftsResults, semanticResults).take(limit)
        }

        override fun searchByEmoji(emoji: String): Flow<List<SearchResult>> {
            val emojiQuery = FtsQuerySanitizer.prepareEmojiQuery(emoji)
            if (emojiQuery.isBlank()) return flowOf(emptyList())
            return memeSearchDao.searchByEmoji(emojiQuery).map { entities ->
                val results =
                    entities.mapIndexed { index, entity ->
                        val relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f)
                        SearchResult(
                            meme = entity.toDomain(),
                            relevanceScore = relevanceScore,
                            matchType = MatchType.EMOJI,
                        )
                    }
                prioritizeFavorites(results)
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
            return FtsQuerySanitizer.prepareForMatch(query)
        }

        private fun determineMatchType(
            entity: com.adsamcik.riposte.core.database.entity.MemeEntity,
            query: String,
        ): MatchType {
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

            ftsResults.forEach { result ->
                resultMap[result.meme.id] =
                    result.copy(
                        relevanceScore = result.relevanceScore * FTS_WEIGHT,
                    )
            }

            semanticResults.forEach { result ->
                val existing = resultMap[result.meme.id]
                if (existing != null) {
                    resultMap[result.meme.id] =
                        existing.copy(
                            relevanceScore = existing.relevanceScore + (result.relevanceScore * SEMANTIC_WEIGHT),
                            matchType = MatchType.HYBRID,
                        )
                } else {
                    resultMap[result.meme.id] =
                        result.copy(
                            relevanceScore = result.relevanceScore * SEMANTIC_WEIGHT,
                        )
                }
            }

            return prioritizeFavorites(resultMap.values.sortedByDescending { it.relevanceScore })
        }

        /**
         * Prioritizes favorited memes in search results by moving them to the front,
         * provided their relevance score meets the minimum threshold.
         * Preserves relative ordering within both favorite and non-favorite groups.
         */
        private fun prioritizeFavorites(results: List<SearchResult>): List<SearchResult> {
            val (favorites, rest) = results.partition {
                it.meme.isFavorite && it.relevanceScore >= FAVORITE_BOOST_THRESHOLD
            }
            return favorites + rest
        }

        private fun com.adsamcik.riposte.core.database.entity.MemeEntity.toDomain(): Meme {
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
                emojiTags =
                    MemeMapper.parseEmojiTagsJson(emojiTagsJson),
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

        override fun getAllMemes(): Flow<List<Meme>> {
            return memeDao.getAllMemes().map { entities ->
                entities.map { it.toDomain() }
            }
        }

        override fun getFavoriteMemes(): Flow<List<SearchResult>> {
            return memeDao.getFavoriteMemes().map { entities ->
                entities.mapIndexed { index, entity ->
                    SearchResult(
                        meme = entity.toDomain(),
                        relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f),
                        matchType = MatchType.TEXT,
                    )
                }
            }
        }

        override fun getRecentMemes(): Flow<List<SearchResult>> {
            return memeDao.getRecentlyViewedMemes().map { entities ->
                entities.mapIndexed { index, entity ->
                    SearchResult(
                        meme = entity.toDomain(),
                        relevanceScore = 1.0f - (index * 0.01f).coerceAtMost(0.5f),
                        matchType = MatchType.TEXT,
                    )
                }
            }
        }

        companion object {
            private const val TAG = "SearchRepositoryImpl"
            private const val FTS_WEIGHT = 0.6f
            private const val SEMANTIC_WEIGHT = 0.4f
            private const val FAVORITE_BOOST_THRESHOLD = 0.5f
        }
    }
