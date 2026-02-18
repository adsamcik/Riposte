@file:Suppress("MagicNumber")

package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import kotlinx.coroutines.flow.first
import java.util.Calendar
import javax.inject.Inject

/**
 * Milestone definition with a check function against FunStatistics.
 */
internal data class MilestoneDefinition(
    val id: String,
    val icon: String,
    val check: (FunStatistics) -> Boolean,
)

/**
 * All milestone definitions ordered by expected unlock progression.
 */
internal val MILESTONES: List<MilestoneDefinition> =
    listOf(
        MilestoneDefinition(
            id = "first_steps",
            icon = "👶",
        ) { it.totalMemes >= 1 },
        MilestoneDefinition(
            id = "getting_started",
            icon = "📦",
        ) { it.totalMemes >= 10 },
        MilestoneDefinition(
            id = "half_century",
            icon = "🎯",
        ) { it.totalMemes >= 50 },
        MilestoneDefinition(
            id = "century_club",
            icon = "💯",
        ) { it.totalMemes >= 100 },
        MilestoneDefinition(
            id = "the_archivist",
            icon = "📚",
        ) { it.totalMemes >= 500 },
        MilestoneDefinition(
            id = "meme_hoarder",
            icon = "🏰",
        ) { it.totalMemes >= 1000 },
        MilestoneDefinition(
            id = "loyal_fan",
            icon = "❤️",
        ) { it.favoriteMemes >= 25 },
        MilestoneDefinition(
            id = "emoji_rainbow",
            icon = "🌈",
        ) { it.uniqueEmojiCount >= 15 },
        MilestoneDefinition(
            id = "format_diplomat",
            icon = "🤝",
        ) { it.distinctMimeTypes >= 3 },
        MilestoneDefinition(
            id = "deep_diver",
            icon = "🤿",
        ) { it.maxViewCount >= 25 },
        MilestoneDefinition(
            id = "social_butterfly",
            icon = "🦋",
        ) { it.totalUseCount >= 100 },
        MilestoneDefinition(
            id = "night_owl",
            icon = "🦉",
        ) { stats ->
            val oldest = stats.oldestImportTimestamp ?: return@MilestoneDefinition false
            val calendar = Calendar.getInstance().apply { timeInMillis = oldest }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            hour in 0..3
        },
        MilestoneDefinition(
            id = "leet",
            icon = "🏴\u200D☠️",
        ) { it.totalMemes == 1337 },
    )

/**
 * Use case that evaluates milestones against current stats and persists new unlocks.
 */
class GetMilestonesUseCase
    @Inject
    constructor(
        private val preferencesDataStore: PreferencesDataStore,
    ) {
        suspend operator fun invoke(stats: FunStatistics): List<MilestoneState> {
            val unlocked = preferencesDataStore.unlockedMilestones.first().toMutableMap()
            var changed = false

            val result =
                MILESTONES.map { milestone ->
                    val wasUnlocked = milestone.id in unlocked
                    val isNowUnlocked = wasUnlocked || milestone.check(stats)

                    if (isNowUnlocked && !wasUnlocked) {
                        unlocked[milestone.id] = System.currentTimeMillis()
                        changed = true
                    }

                    MilestoneState(
                        id = milestone.id,
                        icon = milestone.icon,
                        isUnlocked = isNowUnlocked,
                        unlockedAt = unlocked[milestone.id],
                    )
                }

            if (changed) {
                unlocked.forEach { (id, _) ->
                    preferencesDataStore.unlockMilestone(id)
                }
            }

            return result
        }
    }
