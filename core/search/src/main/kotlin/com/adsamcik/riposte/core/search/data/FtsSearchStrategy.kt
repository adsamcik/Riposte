package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.dao.MemeWithRank
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.database.util.FtsQuerySanitizer
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import javax.inject.Inject

/**
 * Full-text search strategy using Room FTS4.
 *
 * Always available (no external dependencies). Provides instant results
 * based on the FTS index over title, description, textContent, and emojiTags.
 */
class FtsSearchStrategy @Inject constructor(
    private val memeSearchDao: MemeSearchDao,
) : SearchStrategy {

    override val name = "fts"
    override val priority = PRIORITY

    override fun isAvailable(): Boolean = true

    override suspend fun search(query: String, limit: Int): List<SearchResult> {
        val ftsQuery = FtsQuerySanitizer.prepareForMatch(query)
        if (ftsQuery.isBlank()) return emptyList()

        val rankedEntities = memeSearchDao.searchMemesRanked(ftsQuery)
        return rankedEntities.map { entity ->
            // FTS4 does not provide bm25 ranking; rely on field-level scoring only.
            val fieldScore = computeFieldScore(entity, query)
            SearchResult(
                meme = entity.toDomain(),
                relevanceScore = fieldScore,
                matchType = determineMatchType(entity, query),
            )
        }
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    private fun determineMatchType(
        entity: MemeWithRank,
        query: String,
    ): MatchType {
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        val titleLower = entity.title?.lowercase()
        val descLower = entity.description?.lowercase()
        return when {
            titleLower != null && queryWords.any { titleLower.contains(it) } -> MatchType.TEXT
            descLower != null && queryWords.any { descLower.contains(it) } -> MatchType.TEXT
            queryWords.any { entity.emojiTagsJson.contains(it, ignoreCase = true) } -> MatchType.EMOJI
            else -> MatchType.TEXT
        }
    }

    private fun computeFieldScore(
        entity: MemeWithRank,
        query: String,
    ): Float {
        var score = BASE_MATCH_SCORE
        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (queryWords.isEmpty()) return score

        val titleLower = entity.title?.lowercase()
        val descLower = entity.description?.lowercase()
        val emojiLower = entity.emojiTagsJson.lowercase()

        if (titleLower != null && queryWords.any { titleLower.contains(it) }) {
            score += TITLE_MATCH_BONUS
        }
        if (descLower != null && queryWords.any { descLower.contains(it) }) {
            score += DESCRIPTION_MATCH_BONUS
        }
        if (queryWords.any { emojiLower.contains(it) }) {
            score += EMOJI_MATCH_BONUS
        }
        return score.coerceAtMost(1.0f)
    }

    private fun MemeWithRank.toDomain(): Meme =
        Meme(
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
            emojiTags = MemeMapper.parseEmojiTagsJson(emojiTagsJson),
            textContent = textContent,
            isFavorite = isFavorite,
            createdAt = importedAt,
            useCount = 0,
        )

    companion object {
        const val PRIORITY = 100
        private const val BASE_MATCH_SCORE = 0.5f
        private const val TITLE_MATCH_BONUS = 0.3f
        private const val DESCRIPTION_MATCH_BONUS = 0.15f
        private const val EMOJI_MATCH_BONUS = 0.1f
    }
}
