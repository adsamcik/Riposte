package com.adsamcik.riposte.core.common.suggestion

import com.adsamcik.riposte.core.model.Meme

/**
 * The StickerHand suggestion engine. Orchestrates the full pipeline:
 *
 * 1. Cold-start check (too few stickers? no interaction history?)
 * 2. Tri-signal scoring for all stickers
 * 3. Drift detection for trending emoji categories
 * 4. Slot filling (keepers + new + diverse + forgotten + explore + wildcard)
 * 5. Backfill any empty slots
 * 6. Positional ordering (golden triangle + diversification + curiosity anchor)
 *
 * Pure Kotlin — no Android dependencies, fully deterministic with injected `now`.
 */
class SuggestionEngine(
    private val config: SuggestionConfig = SuggestionConfig(),
    private val scorer: TriSignalScorer = TriSignalScorer(config),
    private val driftDetector: DriftDetector = DriftDetector(config),
    private val slotFiller: SlotFiller = SlotFiller(config),
    private val orderer: PositionalOrderer = PositionalOrderer(),
) {

    /**
     * Compute suggestions for the given context.
     *
     * @param allMemes full meme library
     * @param context surface, search state, and staleness info
     * @param now current timestamp in millis
     * @return ordered list of suggested memes (up to [SuggestionConfig.handSize])
     */
    fun suggest(
        allMemes: List<Meme>,
        context: SuggestionContext,
        now: Long,
    ): List<Meme> {
        // Too few stickers — don't show suggestions
        if (allMemes.size < config.minLibrarySize) return emptyList()

        // Small library — just show all sorted by import recency
        if (allMemes.size < config.smallLibraryThreshold) {
            return allMemes
                .sortedByDescending { it.importedAt }
                .take(config.handSize)
        }

        // Cold start — not enough interaction history
        val totalInteractions = allMemes.sumOf { it.viewCount + it.useCount }
        if (totalInteractions < config.coldStartInteractionThreshold) {
            return coldStartSuggestions(allMemes)
        }

        // Full algorithm
        return fullSuggestions(allMemes, context, now)
    }

    internal fun coldStartSuggestions(allMemes: List<Meme>): List<Meme> {
        // Recent imports (8 slots)
        val recent = allMemes
            .sortedByDescending { it.importedAt }
            .take(8)
        val recentIds = recent.map { it.id }.toSet()

        // Diverse emoji spread (4 slots)
        val diverse = allMemes
            .filter { it.id !in recentIds && it.emojiTags.isNotEmpty() }
            .groupBy { it.emojiTags.first().emoji }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.importedAt } }
            .filter { it.id !in recentIds }
            .take(4)

        return (recent + diverse)
            .distinctBy { it.id }
            .take(config.handSize)
    }

    internal fun fullSuggestions(
        allMemes: List<Meme>,
        context: SuggestionContext,
        now: Long,
    ): List<Meme> {
        // Step 1: Score all memes
        val scoredMemes = allMemes
            .map { it to scorer.score(it, context, now) }
            .sortedByDescending { it.second }

        // Step 2: Detect emoji drift
        val drift = driftDetector.detectDrift(allMemes, now)

        // Step 3: Fill slot buckets
        val hand = slotFiller.fill(scoredMemes, allMemes, context, drift, now)

        // Step 4: Backfill empty slots
        val filled = slotFiller.backfill(hand, scoredMemes)

        // Step 5: Build score map for ordering
        val scoreMap = scoredMemes.associate { it.first.id to it.second }

        // Step 6: Positional ordering
        return orderer.order(filled.take(config.handSize), scoreMap)
    }
}
