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

    fun score(meme: Meme, context: SuggestionContext, now: Long): Double {
        val engagement = engagementScore(meme)
        val recency = recencyScore(meme, now)
        val scope = scopeScore(meme, context)

        return when (context.surface) {
            Surface.GALLERY ->
                engagement * 0.50 + recency * 0.35 + scope * 0.15

            Surface.SEARCH ->
                scope * 0.40 + recency * 0.35 + engagement * 0.25
        }
    }

    internal fun engagementScore(meme: Meme): Double {
        return (meme.useCount * 3.0) +
            (meme.viewCount * 0.5) +
            (if (meme.isFavorite) 5.0 else 0.0)
    }

    internal fun recencyScore(meme: Meme, now: Long): Double {
        val daysSinceView = meme.lastViewedAt?.let {
            (now - it).toDouble() / MS_PER_DAY
        } ?: 30.0
        return exp(-lambda * daysSinceView)
    }

    internal fun scopeScore(meme: Meme, context: SuggestionContext): Double {
        if (context.currentEmojiFilter != null &&
            meme.emojiTags.any { it.emoji == context.currentEmojiFilter }
        ) {
            return 3.0
        }
        if (context.recentSearches.take(5).any { search -> meme.matchesSearch(search) }) {
            return 2.0
        }
        return 1.0
    }

    companion object {
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
