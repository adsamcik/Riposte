package com.adsamcik.riposte.feature.settings.domain.model

/**
 * Represents the state of a milestone achievement.
 */
data class MilestoneState(
    val id: String,
    val icon: String,
    val isUnlocked: Boolean,
    val unlockedAt: Long? = null,
)

/**
 * Trend direction for meme momentum.
 */
enum class MomentumTrend {
    GROWING,
    STABLE,
    DECLINING,
}
