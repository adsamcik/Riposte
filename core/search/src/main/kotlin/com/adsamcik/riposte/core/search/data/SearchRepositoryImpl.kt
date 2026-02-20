package com.adsamcik.riposte.core.search.data

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
import timber.log.Timber
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
                return flowOf(emptyList())
            }

            val ftsQuery = prepareFtsQuery(query)
            return memeSearchDao.searchMemes(ftsQuery).map { entities ->
                val results =
                    entities.map { entity ->
                        val relevanceScore = computeFieldScore(entity, query)
                        val matchType = determineMatchType(entity, query)
                        SearchResult(
                            meme = entity.toDomain(),
                            relevanceScore = relevanceScore,
                            matchType = matchType,
                        )
                    }.sortedByDescending { it.relevanceScore }
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
                                    Timber.w("Skipping embedding with invalid dimensions: ${decoded.size}")
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
                    try {
                        searchSemantic(query, limit)
                    } catch (e: UnsatisfiedLinkError) {
                        Timber.w(e, "Semantic search unavailable, returning text-only results")
                        emptyList()
                    } catch (e: ExceptionInInitializerError) {
                        Timber.w(e, "Semantic search init failed, returning text-only results")
                        emptyList()
                    }
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
                        val relevanceScore =
                            1.0f - (index * POSITION_RELEVANCE_DECAY).coerceAtMost(MAX_POSITION_DECAY)
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
            val titleSuggestions = memeSearchDao.getSearchSuggestions(prefix)
            val descriptionSuggestions =
                memeSearchDao.getDescriptionSuggestions(prefix)
                    .map { desc -> extractRelevantPhrase(desc, prefix) }
                    .filter { it.isNotBlank() }
            return (titleSuggestions + descriptionSuggestions)
                .distinct()
                .take(MAX_SEARCH_SUGGESTIONS)
        }

        private fun extractRelevantPhrase(description: String, prefix: String): String {
            val lowerDesc = description.lowercase()
            val lowerPrefix = prefix.lowercase()
            val index = lowerDesc.indexOf(lowerPrefix)
            if (index < 0) return description.take(DESCRIPTION_SNIPPET_LENGTH)

            // Find word boundaries around the match
            val start = description.lastIndexOf(' ', (index - 1).coerceAtLeast(0)).let {
                if (it < 0) 0 else it + 1
            }
            val endOfMatch = index + prefix.length
            // Take a few more words after the match (up to ~40 more chars)
            val end = description.indexOf(' ', (endOfMatch + PHRASE_CONTEXT_CHARS).coerceAtMost(description.length)).let {
                if (it < 0) description.length else it
            }
            return description.substring(start, end).trim()
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
            val floatArray = FloatArray(bytes.size / BYTES_PER_FLOAT)
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
            return emojiTagDao.getEmojisOrderedByUsage().map { stats ->
                stats.map { it.emoji to it.totalUsage }
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
                        relevanceScore =
                            1.0f - (index * POSITION_RELEVANCE_DECAY).coerceAtMost(MAX_POSITION_DECAY),
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
                        relevanceScore =
                            1.0f - (index * POSITION_RELEVANCE_DECAY).coerceAtMost(MAX_POSITION_DECAY),
                        matchType = MatchType.TEXT,
                    )
                }
            }
        }

        /**
         * Compute a relevance score based on which fields match the query.
         * Title match scores highest, followed by description, then other fields.
         */
        private fun computeFieldScore(
            entity: com.adsamcik.riposte.core.database.entity.MemeEntity,
            query: String,
        ): Float {
            var score = BASE_MATCH_SCORE
            val lowerQuery = query.lowercase()
            if (entity.title?.lowercase()?.contains(lowerQuery) == true) {
                score += TITLE_MATCH_BONUS
            }
            if (entity.description?.lowercase()?.contains(lowerQuery) == true) {
                score += DESCRIPTION_MATCH_BONUS
            }
            if (entity.emojiTagsJson.lowercase().contains(lowerQuery)) {
                score += EMOJI_MATCH_BONUS
            }
            return score.coerceAtMost(1.0f)
        }

        companion object {
            private const val FTS_WEIGHT = 0.6f
            private const val SEMANTIC_WEIGHT = 0.4f
            private const val FAVORITE_BOOST_THRESHOLD = 0.5f
            private const val BASE_MATCH_SCORE = 0.5f
            private const val TITLE_MATCH_BONUS = 0.3f
            private const val DESCRIPTION_MATCH_BONUS = 0.15f
            private const val EMOJI_MATCH_BONUS = 0.1f
            private const val POSITION_RELEVANCE_DECAY = 0.01f
            private const val MAX_POSITION_DECAY = 0.5f
            private const val BYTES_PER_FLOAT = 4
            private const val MAX_SEARCH_SUGGESTIONS = 10
            private const val DESCRIPTION_SNIPPET_LENGTH = 50
            private const val PHRASE_CONTEXT_CHARS = 40
        }
    }
