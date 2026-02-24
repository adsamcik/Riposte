package com.adsamcik.riposte.core.search.data

import com.adsamcik.riposte.core.database.dao.MemeSearchDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.database.util.FtsQuerySanitizer
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.SearchStrategy
import kotlinx.coroutines.flow.first
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

        val entities = memeSearchDao.searchMemes(ftsQuery).first()
        return entities.map { entity ->
            SearchResult(
                meme = entity.toDomain(),
                relevanceScore = computeFieldScore(entity, query),
                matchType = determineMatchType(entity, query),
            )
        }
            .sortedByDescending { it.relevanceScore }
            .take(limit)
    }

    private fun determineMatchType(
        entity: com.adsamcik.riposte.core.database.entity.MemeEntity,
        query: String,
    ): MatchType =
        when {
            entity.title?.contains(query, ignoreCase = true) == true -> MatchType.TEXT
            entity.description?.contains(query, ignoreCase = true) == true -> MatchType.TEXT
            entity.emojiTagsJson.contains(query, ignoreCase = true) -> MatchType.EMOJI
            else -> MatchType.TEXT
        }

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

    private fun com.adsamcik.riposte.core.database.entity.MemeEntity.toDomain(): Meme =
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
            createdAt = createdAt,
            useCount = useCount,
        )

    companion object {
        const val PRIORITY = 100
        private const val BASE_MATCH_SCORE = 0.5f
        private const val TITLE_MATCH_BONUS = 0.3f
        private const val DESCRIPTION_MATCH_BONUS = 0.15f
        private const val EMOJI_MATCH_BONUS = 0.1f
    }
}
