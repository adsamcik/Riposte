package com.adsamcik.riposte.feature.settings.presentation

import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.core.database.dao.WeeklyCount
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FunStatsComputationTest {

    // region Collection Title

    @Test
    fun `computeStorageFunFact returns empty for zero bytes`() {
        val result = computeStorageFunFact(0)
        assertThat(result).isEmpty()
    }

    @Test
    fun `computeStorageFunFact returns equivalence for non-zero bytes`() {
        val result = computeStorageFunFact(10_000_000)
        assertThat(result).startsWith("≈")
        assertThat(result).endsWith("of pure culture")
    }

    @Test
    fun `computeStorageFunFact includes floppy disks for large sizes`() {
        val result = computeStorageFunFact(50_000_000)
        assertThat(result).contains("of pure culture")
    }

    // endregion

    // region Vibe Tagline

    @Test
    fun `computeVibeTagline returns empty for empty list`() {
        val result = computeVibeTagline(emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `computeVibeTagline returns getting started for single emoji`() {
        val result = computeVibeTagline(listOf(EmojiUsageStats("😂", "face with tears of joy", 10)))
        assertThat(result).isEqualTo("Just getting started!")
    }

    @Test
    fun `computeVibeTagline returns unhinged for laugh and skull combo`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("😂", "face with tears of joy", 100),
                EmojiUsageStats("💀", "skull", 50),
            ),
        )
        assertThat(result).contains("unhinged humor")
        assertThat(result).contains("Chronically online")
    }

    @Test
    fun `computeVibeTagline returns wholesome for heart emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("❤️", "red heart", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("wholesome")
    }

    @Test
    fun `computeVibeTagline returns enigma for moai`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🗿", "moai", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("enigma")
    }

    @Test
    fun `computeVibeTagline includes percentage`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🔥", "fire", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("80%")
    }

    // endregion

    // region Weekly Data

    @Test
    fun `computeWeeklyData returns 4-element list`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 10),
                WeeklyCount(weekAgo = 1, count = 5),
                WeeklyCount(weekAgo = 3, count = 2),
            ),
        )
        val result = computeWeeklyData(stats)
        assertThat(result).hasSize(4)
    }

    @Test
    fun `computeWeeklyData puts current week at end`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 15),
                WeeklyCount(weekAgo = 3, count = 3),
            ),
        )
        val result = computeWeeklyData(stats)
        assertThat(result.last()).isEqualTo(15)
        assertThat(result.first()).isEqualTo(3)
    }

    @Test
    fun `computeWeeklyData fills missing weeks with zero`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 5),
            ),
        )
        val result = computeWeeklyData(stats)
        assertThat(result).isEqualTo(listOf(0, 0, 0, 5))
    }

    // endregion

    // region Momentum Trend

    @Test
    fun `computeMomentumTrend returns GROWING when recent weeks have more`() {
        val result = computeMomentumTrend(listOf(1, 2, 8, 12))
        assertThat(result).isEqualTo(MomentumTrend.GROWING)
    }

    @Test
    fun `computeMomentumTrend returns DECLINING when older weeks have more`() {
        val result = computeMomentumTrend(listOf(15, 12, 2, 1))
        assertThat(result).isEqualTo(MomentumTrend.DECLINING)
    }

    @Test
    fun `computeMomentumTrend returns STABLE when roughly equal`() {
        val result = computeMomentumTrend(listOf(5, 5, 5, 5))
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    @Test
    fun `computeMomentumTrend returns STABLE for empty data`() {
        val result = computeMomentumTrend(emptyList())
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    // endregion
}
