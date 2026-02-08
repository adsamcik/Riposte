package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.common.suggestion.SuggestionConfig.Companion.MS_PER_DAY
import com.adsamcik.riposte.core.model.Meme
import kotlin.math.ln

/**
 * Fills the 12-slot "hand" using fixed-purpose buckets:
 *
 * [K][K][K][K][K][K] [N][N] [D] [F] [E] [W]
 *  ── Keepers ─────  ─New─  Div Fgt Exp Wild
 *
 * Each bucket serves a different user motivation (reactive, novelty, discovery, etc.)
 */
class SlotFiller(
    private val config: SuggestionConfig = SuggestionConfig(),
) {

    data class FilledHand(
        val keepers: List<Meme>,
        val new: List<Meme>,
        val diverse: List<Meme>,
        val forgotten: List<Meme>,
        val explore: List<Meme>,
        val wildcard: List<Meme>,
    ) {
        fun all(): List<Meme> = keepers + new + diverse + forgotten + explore + wildcard
    }

    /**
     * Fill all buckets from scored memes.
     *
     * @param scoredMemes memes sorted by base score descending
     * @param allMemes full library for bucket-specific queries
     * @param context suggestion context with staleness info
     * @param drift map of emoji→drift from DriftDetector
     * @param now current timestamp
     */
    fun fill(
        scoredMemes: List<Pair<Meme, Double>>,
        allMemes: List<Meme>,
        context: SuggestionContext,
        drift: Map<String, Double>,
        now: Long,
    ): FilledHand {
        val selectedIds = mutableSetOf<Long>()
        fun isAvailable(meme: Meme) = meme.id !in selectedIds
        fun markSelected(memes: List<Meme>) { selectedIds += memes.map { it.id } }

        // KEEPERS: top stickers by score with staleness penalty
        val keepers = fillKeepers(scoredMemes, context.lastSessionSuggestionIds, selectedIds)
        markSelected(keepers)

        // NEW: recently imported stickers (48h novelty window)
        val new = fillNew(allMemes, now, selectedIds)
        markSelected(new)

        // DIVERSE: stickers from under-represented emoji categories
        val diverse = fillDiverse(allMemes, keepers + new, selectedIds)
        markSelected(diverse)

        // FORGOTTEN: long-absent former favorites
        val forgotten = fillForgotten(allMemes, now, selectedIds)
        markSelected(forgotten)

        // EXPLORE: stickers from never-used emoji categories
        val explore = fillExplore(allMemes, now, selectedIds)
        markSelected(explore)

        // WILDCARD: drift-trending stickers
        val wildcard = fillWildcard(allMemes, drift, selectedIds)
        markSelected(wildcard)

        return FilledHand(
            keepers = keepers,
            new = new,
            diverse = diverse,
            forgotten = forgotten,
            explore = explore,
            wildcard = wildcard,
        )
    }

    /**
     * Backfill empty slots from the top scored memes.
     */
    fun backfill(
        hand: FilledHand,
        scoredMemes: List<Pair<Meme, Double>>,
    ): List<Meme> {
        val current = hand.all()
        val currentIds = current.map { it.id }.toSet()
        val remaining = config.handSize - current.size
        if (remaining <= 0) return current

        val backfilled = scoredMemes
            .map { it.first }
            .filter { it.id !in currentIds }
            .take(remaining)

        return current + backfilled
    }

    internal fun fillKeepers(
        scoredMemes: List<Pair<Meme, Double>>,
        lastSessionIds: Set<Long>,
        excludeIds: Set<Long>,
    ): List<Meme> {
        return scoredMemes
            .map { (meme, score) ->
                val penalty = if (meme.id in lastSessionIds) config.stalenessPenalty else 1.0
                meme to score * penalty
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .filter { it.id !in excludeIds }
            .take(config.keeperSlots)
    }

    internal fun fillNew(
        allMemes: List<Meme>,
        now: Long,
        excludeIds: Set<Long>,
    ): List<Meme> {
        return allMemes
            .filter { it.id !in excludeIds && (now - it.importedAt) < config.noveltyWindowMs }
            .sortedByDescending { it.importedAt }
            .take(config.newSlots)
    }

    internal fun fillDiverse(
        allMemes: List<Meme>,
        alreadySelected: List<Meme>,
        excludeIds: Set<Long>,
    ): List<Meme> {
        val coveredEmojis = alreadySelected
            .flatMap { it.emojiTags.map { t -> t.emoji } }
            .toSet()

        return allMemes
            .filter { it.id !in excludeIds && it.emojiTags.any { t -> t.emoji !in coveredEmojis } }
            .sortedByDescending { it.useCount + it.viewCount }
            .take(config.diverseSlots)
    }

    internal fun fillForgotten(
        allMemes: List<Meme>,
        now: Long,
        excludeIds: Set<Long>,
    ): List<Meme> {
        val thresholdMs = config.forgottenThresholdDays.toLong() * MS_PER_DAY

        return allMemes
            .filter { meme ->
                meme.id !in excludeIds &&
                    meme.useCount >= 1 &&
                    (meme.lastViewedAt?.let { (now - it) > thresholdMs } ?: true)
            }
            .sortedByDescending { meme ->
                val daysSinceView = meme.lastViewedAt?.let {
                    (now - it).toDouble() / MS_PER_DAY
                } ?: 60.0
                ln(1.0 + meme.useCount) * ln(1.0 + daysSinceView)
            }
            .take(config.forgottenSlots)
    }

    internal fun fillExplore(
        allMemes: List<Meme>,
        now: Long,
        excludeIds: Set<Long>,
    ): List<Meme> {
        // Find emoji categories the user has never interacted with
        val usedEmojis = allMemes
            .filter { it.viewCount > 0 || it.useCount > 0 }
            .flatMap { it.emojiTags.map { t -> t.emoji } }
            .toSet()

        val candidates = allMemes
            .filter { it.id !in excludeIds && it.emojiTags.any { t -> t.emoji !in usedEmojis } }

        // Pick randomly for serendipity, but deterministic via import order as tiebreaker
        return candidates
            .sortedByDescending { it.importedAt }
            .take(config.exploreSlots)
    }

    internal fun fillWildcard(
        allMemes: List<Meme>,
        drift: Map<String, Double>,
        excludeIds: Set<Long>,
    ): List<Meme> {
        val rising = drift.filter { it.value > 0 }.keys
        if (rising.isEmpty()) return emptyList()

        return allMemes
            .filter { it.id !in excludeIds && it.emojiTags.any { t -> t.emoji in rising } }
            .sortedByDescending { meme ->
                meme.emojiTags.sumOf { drift[it.emoji] ?: 0.0 }
            }
            .take(config.wildcardSlots)
    }
}
