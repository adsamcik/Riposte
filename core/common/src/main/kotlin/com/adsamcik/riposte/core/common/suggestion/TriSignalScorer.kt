package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.common.suggestion.SuggestionConfig.Companion.MS_PER_DAY
import com.adsamcik.riposte.core.model.Meme
import kotlin.math.exp
import kotlin.math.ln

/**
 * Computes a base score per sticker combining three signals:
 * 1. Engagement — how much the user relies on this sticker (useCount, viewCount, isFavorite)
 * 2. Recency — how fresh is this sticker in the user's mind (lastViewedAt decay)
 * 3. Scope — contextual relevance to current search/filter (search screen only)
 *
 * Weights differ per surface: Gallery favors engagement, Search favors scope.
 */
class TriSignalScorer(
    private val config: SuggestionConfig = SuggestionConfig(),
) {
    private val lambda: Double = ln(2.0) / config.halfLifeDays

    fun score(
        meme: Meme,
        context: SuggestionContext,
        now: Long,
    ): Double {
        val engagement = engagementScore(meme)
        val recency = recencyScore(meme, now)
        val scope = scopeScore(meme, context)

        return when (context.surface) {
            Surface.GALLERY ->
                engagement * GALLERY_ENGAGEMENT_WEIGHT +
                    recency * GALLERY_RECENCY_WEIGHT +
                    scope * GALLERY_SCOPE_WEIGHT

            Surface.SEARCH ->
                scope * SEARCH_SCOPE_WEIGHT +
                    recency * SEARCH_RECENCY_WEIGHT +
                    engagement * SEARCH_ENGAGEMENT_WEIGHT
        }
    }

    internal fun engagementScore(meme: Meme): Double {
        return (meme.useCount * USE_COUNT_MULTIPLIER) +
            (meme.viewCount * VIEW_COUNT_MULTIPLIER) +
            (if (meme.isFavorite) FAVORITE_BONUS else 0.0)
    }

    internal fun recencyScore(
        meme: Meme,
        now: Long,
    ): Double {
        val daysSinceView =
            meme.lastViewedAt?.let {
                (now - it).toDouble() / MS_PER_DAY
            } ?: DEFAULT_DAYS_SINCE_VIEW
        return exp(-lambda * daysSinceView)
    }

    internal fun scopeScore(
        meme: Meme,
        context: SuggestionContext,
    ): Double {
        if (context.currentEmojiFilter != null &&
            meme.emojiTags.any { it.emoji == context.currentEmojiFilter }
        ) {
            return EMOJI_FILTER_MATCH_SCORE
        }
        if (context.recentSearches.take(RECENT_SEARCHES_LIMIT).any { search -> meme.matchesSearch(search) }) {
            return 2.0
        }
        return 1.0
    }

    companion object {
        private const val GALLERY_ENGAGEMENT_WEIGHT = 0.50
        private const val GALLERY_RECENCY_WEIGHT = 0.35
        private const val GALLERY_SCOPE_WEIGHT = 0.15
        private const val SEARCH_SCOPE_WEIGHT = 0.40
        private const val SEARCH_RECENCY_WEIGHT = 0.35
        private const val SEARCH_ENGAGEMENT_WEIGHT = 0.25
        private const val USE_COUNT_MULTIPLIER = 3.0
        private const val VIEW_COUNT_MULTIPLIER = 0.5
        private const val FAVORITE_BONUS = 5.0
        private const val DEFAULT_DAYS_SINCE_VIEW = 30.0
        private const val EMOJI_FILTER_MATCH_SCORE = 3.0
        private const val RECENT_SEARCHES_LIMIT = 5

        internal fun Meme.matchesSearch(query: String): Boolean {
            val q = query.lowercase()
            return title?.lowercase()?.contains(q) == true ||
                description?.lowercase()?.contains(q) == true ||
                textContent?.lowercase()?.contains(q) == true ||
                emojiTags.any { tag ->
                    tag.name.lowercase().contains(q) ||
                        tag.keywords.any { it.lowercase().contains(q) }
                }
        }
    }
}
