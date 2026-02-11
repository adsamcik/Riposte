package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.common.suggestion.SuggestionConfig.Companion.MS_PER_DAY
import com.adsamcik.riposte.core.model.Meme

/**
 * Detects shifting emoji preferences by comparing recent (7-day) emoji usage
 * frequencies against all-time usage. Positive drift = trending up.
 *
 * Inspired by TikTok's interest shift detection.
 */
class DriftDetector(
    private val config: SuggestionConfig = SuggestionConfig(),
) {
    /**
     * Returns a map of emoji → drift value.
     * Positive values indicate the emoji is trending up in recent usage.
     */
    fun detectDrift(
        memes: List<Meme>,
        now: Long,
    ): Map<String, Double> {
        val recentWindowMs = config.driftWindowDays.toLong() * MS_PER_DAY

        val recentCounts = mutableMapOf<String, Int>()
        val totalCounts = mutableMapOf<String, Int>()

        for (meme in memes) {
            val isRecent = meme.lastViewedAt?.let { (now - it) < recentWindowMs } == true
            val weight = meme.viewCount + meme.useCount
            for (tag in meme.emojiTags) {
                totalCounts[tag.emoji] = (totalCounts[tag.emoji] ?: 0) + weight
                if (isRecent) {
                    recentCounts[tag.emoji] = (recentCounts[tag.emoji] ?: 0) + weight
                }
            }
        }

        val totalSum = totalCounts.values.sum().coerceAtLeast(1).toDouble()
        val recentSum = recentCounts.values.sum().coerceAtLeast(1).toDouble()

        val drift = mutableMapOf<String, Double>()
        for (emoji in totalCounts.keys) {
            val historicalPct = (totalCounts[emoji] ?: 0) / totalSum
            val recentPct = (recentCounts[emoji] ?: 0) / recentSum
            drift[emoji] = recentPct - historicalPct
        }
        return drift
    }

    fun risingEmojis(
        memes: List<Meme>,
        now: Long,
    ): Set<String> {
        return detectDrift(memes, now).filter { it.value > 0 }.keys
    }
}
