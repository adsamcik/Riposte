package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.model.Meme

/**
 * Applies positional ordering to the assembled hand:
 * - Slots 0-1: Highest confidence picks ("golden triangle")
 * - Middle slots: Diversified — no two adjacent stickers share the same primary emoji
 * - Last slot: Curiosity anchor (explore/wildcard)
 *
 * Inspired by restaurant menu design (Session 2).
 */
class PositionalOrderer {

    fun order(
        hand: List<Meme>,
        scores: Map<Long, Double>,
    ): List<Meme> {
        if (hand.size <= 2) return hand.sortedByDescending { scores[it.id] ?: 0.0 }

        val sorted = hand.sortedByDescending { scores[it.id] ?: 0.0 }

        // Golden triangle: first 2 slots get highest confidence
        val goldenPicks = sorted.take(2)
        val remaining = sorted.drop(2).toMutableList()

        // Separate curiosity anchor: last item in remaining (lowest score = most novel)
        val curiosityAnchor = remaining.removeLastOrNull()

        // Greedy diversification: no adjacent stickers share the same primary emoji
        val middle = greedyDiversify(remaining)

        return buildList {
            addAll(goldenPicks)
            addAll(middle)
            curiosityAnchor?.let { add(it) }
        }
    }

    /**
     * Orders memes so no two adjacent items share the same primary emoji tag.
     * Uses a greedy approach — picks the next highest-scored meme that doesn't
     * duplicate the previous item's primary emoji.
     */
    internal fun greedyDiversify(memes: List<Meme>): List<Meme> {
        if (memes.size <= 1) return memes

        val pool = memes.toMutableList()
        val result = mutableListOf<Meme>()

        while (pool.isNotEmpty()) {
            val lastEmoji = result.lastOrNull()?.emojiTags?.firstOrNull()?.emoji
            val nextIdx = if (lastEmoji != null) {
                // Prefer a meme with a different primary emoji
                pool.indexOfFirst { it.emojiTags.firstOrNull()?.emoji != lastEmoji }
                    .takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            result.add(pool.removeAt(nextIdx))
        }
        return result
    }
}
