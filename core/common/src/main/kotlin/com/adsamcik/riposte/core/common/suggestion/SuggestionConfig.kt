package com.adsamcik.riposte.core.common.suggestion

/**
 * Tunable parameters for the StickerHand suggestion algorithm.
 */
data class SuggestionConfig(
    val handSize: Int = 12,
    val keeperSlots: Int = 6,
    val newSlots: Int = 2,
    val diverseSlots: Int = 1,
    val forgottenSlots: Int = 1,
    val exploreSlots: Int = 1,
    val wildcardSlots: Int = 1,
    val halfLifeDays: Double = 14.0,
    val noveltyWindowMs: Long = NOVELTY_WINDOW_HOURS * MS_PER_HOUR,
    val forgottenThresholdDays: Int = 14,
    val stalenessPenalty: Double = 0.80,
    val driftWindowDays: Int = 7,
    val minLibrarySize: Int = 5,
    val smallLibraryThreshold: Int = 20,
    val coldStartInteractionThreshold: Int = 10,
) {
    companion object {
        const val MS_PER_DAY: Long = 86_400_000L
        private const val NOVELTY_WINDOW_HOURS = 48L
        private const val MS_PER_HOUR = 3_600_000L
    }
}
