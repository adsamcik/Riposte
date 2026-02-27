package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.database.util.FtsQuerySanitizer
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
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
        private val emojiTagDao: EmojiTagDao,
        private val searchOrchestrator: SearchOrchestrator,
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
                prioritizeFavorites(results).distinctBy { it.meme.id }
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
            val searchMode = preferencesDataStore.appPreferences.first().searchMode
            return searchOrchestrator.search(query, limit, searchMode)
        }

        override suspend fun searchHybrid(
            query: String,
            limit: Int,
        ): List<SearchResult> {
            if (query.isBlank()) return emptyList()
            val searchMode = preferencesDataStore.appPreferences.first().searchMode
            return searchOrchestrator.search(query, limit, searchMode)
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
                prioritizeFavorites(results).distinctBy { it.meme.id }
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
            val end = description.indexOf(
                ' ',
                (endOfMatch + PHRASE_CONTEXT_CHARS).coerceAtMost(description.length),
            ).let {
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


        /**
         * Prioritizes favorited memes in search resultsby moving them to the front,
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
                entities.map { it.toDomain() }.distinctBy { it.id }
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
                }.distinctBy { it.meme.id }
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
                }.distinctBy { it.meme.id }
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
            private const val FAVORITE_BOOST_THRESHOLD = 0.5f
            private const val BASE_MATCH_SCORE = 0.5f
            private const val TITLE_MATCH_BONUS = 0.3f
            private const val DESCRIPTION_MATCH_BONUS = 0.15f
            private const val EMOJI_MATCH_BONUS = 0.1f
            private const val POSITION_RELEVANCE_DECAY = 0.01f
            private const val MAX_POSITION_DECAY = 0.5f
            private const val MAX_SEARCH_SUGGESTIONS = 10
            private const val DESCRIPTION_SNIPPET_LENGTH = 50
            private const val PHRASE_CONTEXT_CHARS = 40
            private const val DEFAULT_SEMANTIC_THRESHOLD = 0.3f
        }
    }
