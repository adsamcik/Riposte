package com.adsamcik.riposte.feature.settings.presentation.funstats

import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend

/**
 * UI state for the Fun Statistics screen.
 */
data class FunStatsUiState(
    val isLoading: Boolean = true,
    // Library
    val totalMemeCount: Int = 0,
    val favoriteMemeCount: Int = 0,
    // Meme-o-Meter
    val collectionTitle: String = "",
    val totalStorageBytes: Long = 0,
    val storageFunFact: String = "",
    // Vibe Check
    val topVibes: List<EmojiUsageStats> = emptyList(),
    val vibeTagline: String = "",
    // Fun Fact of the Day
    val funFactOfTheDay: String? = null,
    // Momentum
    val weeklyImportCounts: List<Int> = emptyList(),
    val momentumTrend: MomentumTrend = MomentumTrend.STABLE,
    val memesThisWeek: Int = 0,
    // Milestones
    val milestones: List<MilestoneState> = emptyList(),
    val unlockedMilestoneCount: Int = 0,
    val totalMilestoneCount: Int = 0,
)
