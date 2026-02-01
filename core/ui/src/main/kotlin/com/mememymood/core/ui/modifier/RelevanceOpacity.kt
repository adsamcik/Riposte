package com.mememymood.core.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * Applies opacity based on relevance ranking in search results.
 *
 * Per VISUAL_DESIGN_SPEC.md Section 5, creates visual hierarchy
 * by making top results more prominent:
 * - Top 20%: 100% opacity
 * - 21-50%: 90% opacity
 * - 51-100%: 80% opacity
 *
 * @param rank The 0-based position of this item in the results list.
 * @param total The total number of results.
 * @return Modifier with the appropriate alpha applied.
 */
fun Modifier.relevanceOpacity(rank: Int, total: Int): Modifier {
    if (total <= 0) return this

    val percentile = rank.toFloat() / total
    val alpha = when {
        percentile <= 0.2f -> 1.0f
        percentile <= 0.5f -> 0.9f
        else -> 0.8f
    }
    return this.alpha(alpha)
}
