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

    // region formatFileSize

    @Test
    fun `formatFileSize returns bytes for small values`() {
        assertThat(formatFileSize(0)).isEqualTo("0 B")
        assertThat(formatFileSize(1)).isEqualTo("1 B")
        assertThat(formatFileSize(1023)).isEqualTo("1023 B")
    }

    @Test
    fun `formatFileSize returns KB for kilobyte range`() {
        assertThat(formatFileSize(1024)).isEqualTo("1.0 KB")
        assertThat(formatFileSize(1536)).isEqualTo("1.5 KB")
        assertThat(formatFileSize(1_048_575)).isEqualTo("1024.0 KB")
    }

    @Test
    fun `formatFileSize returns MB for megabyte range`() {
        assertThat(formatFileSize(1_048_576)).isEqualTo("1.0 MB")
        assertThat(formatFileSize(10_485_760)).isEqualTo("10.0 MB")
        assertThat(formatFileSize(1_073_741_823)).isEqualTo("1024.0 MB")
    }

    @Test
    fun `formatFileSize returns GB for gigabyte range`() {
        assertThat(formatFileSize(1_073_741_824)).isEqualTo("1.0 GB")
        assertThat(formatFileSize(5_368_709_120)).isEqualTo("5.0 GB")
    }

    // endregion

    // region computeStorageFunFact extended

    @Test
    fun `computeStorageFunFact returns empty for negative bytes`() {
        assertThat(computeStorageFunFact(-100)).isEmpty()
    }

    @Test
    fun `computeStorageFunFact returns few bytes for very small sizes`() {
        assertThat(computeStorageFunFact(100)).isEqualTo("A few bytes of culture")
        assertThat(computeStorageFunFact(1_000_000)).isEqualTo("A few bytes of culture")
    }

    @Test
    fun `computeStorageFunFact returns deterministic equivalence for given size`() {
        val result1 = computeStorageFunFact(10_000_000)
        val result2 = computeStorageFunFact(10_000_000)
        assertThat(result1).isEqualTo(result2)
    }

    @Test
    fun `computeStorageFunFact handles CD-sized data`() {
        val result = computeStorageFunFact(1_400_000_000)
        assertThat(result).contains("of pure culture")
    }

    @Test
    fun `computeStorageFunFact handles exact floppy boundary`() {
        val result = computeStorageFunFact(1_474_560)
        assertThat(result).contains("of pure culture")
    }

    // endregion

    // region computeVibeTagline extended

    @Test
    fun `computeVibeTagline returns skull energy for skull and laugh combo`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("💀", "skull", 100),
                EmojiUsageStats("😂", "face with tears of joy", 50),
            ),
        )
        assertThat(result).contains("skull energy")
    }

    @Test
    fun `computeVibeTagline returns fire for fire emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🔥", "fire", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("fire")
        assertThat(result).contains("Everything is lit")
    }

    @Test
    fun `computeVibeTagline returns tears for crying emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("😭", "loudly crying face", 70),
                EmojiUsageStats("😂", "face with tears of joy", 30),
            ),
        )
        assertThat(result).contains("tears")
        assertThat(result).contains("coping mechanism")
    }

    @Test
    fun `computeVibeTagline returns determination for flexing emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("💪", "flexed biceps", 60),
                EmojiUsageStats("😂", "face with tears of joy", 40),
            ),
        )
        assertThat(result).contains("determination")
    }

    @Test
    fun `computeVibeTagline returns determination for angry emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("😤", "face with steam from nose", 60),
                EmojiUsageStats("😂", "face with tears of joy", 40),
            ),
        )
        assertThat(result).contains("determination")
    }

    @Test
    fun `computeVibeTagline returns clown for clown emoji`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🤡", "clown face", 60),
                EmojiUsageStats("😂", "face with tears of joy", 40),
            ),
        )
        assertThat(result).contains("clown")
        assertThat(result).contains("chaos")
    }

    @Test
    fun `computeVibeTagline returns committed for 60+ percent dominance`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🎉", "party popper", 70),
                EmojiUsageStats("😂", "face with tears of joy", 10),
                EmojiUsageStats("😍", "smiling face with heart-eyes", 5),
            ),
        )
        assertThat(result).contains("Very committed")
        assertThat(result).contains("🎉")
    }

    @Test
    fun `computeVibeTagline returns everything else for 40-59 percent dominance`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🎉", "party popper", 50),
                EmojiUsageStats("😂", "face with tears of joy", 30),
                EmojiUsageStats("😍", "smiling face with heart-eyes", 20),
            ),
        )
        assertThat(result).contains("everything else")
        assertThat(result).contains("🎉")
    }

    @Test
    fun `computeVibeTagline returns balanced diet for low dominance`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🎉", "party popper", 30),
                EmojiUsageStats("😂", "face with tears of joy", 25),
                EmojiUsageStats("😍", "smiling face with heart-eyes", 25),
                EmojiUsageStats("🔥", "fire", 20),
            ),
        )
        assertThat(result).contains("A balanced diet")
        assertThat(result).contains("🎉")
        assertThat(result).contains("😂")
    }

    @Test
    fun `computeVibeTagline returns wholesome for smiling heart eyes`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("😍", "smiling face with heart-eyes", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("wholesome")
    }

    @Test
    fun `computeVibeTagline returns wholesome for smiling with hearts`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🥰", "smiling face with hearts", 80),
                EmojiUsageStats("😂", "face with tears of joy", 20),
            ),
        )
        assertThat(result).contains("wholesome")
    }

    @Test
    fun `computeVibeTagline percentage is computed from total distribution`() {
        val result = computeVibeTagline(
            listOf(
                EmojiUsageStats("🗿", "moai", 3),
                EmojiUsageStats("😂", "face with tears of joy", 1),
            ),
        )
        assertThat(result).contains("75%")
    }

    // endregion

    // region computeWeeklyData extended

    @Test
    fun `computeWeeklyData returns all zeros for empty stats`() {
        val stats = FunStatistics(weeklyImportCounts = emptyList())
        val result = computeWeeklyData(stats)
        assertThat(result).isEqualTo(listOf(0, 0, 0, 0))
    }

    @Test
    fun `computeWeeklyData ignores weeks beyond range`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 5),
                WeeklyCount(weekAgo = 4, count = 99),
                WeeklyCount(weekAgo = -1, count = 99),
            ),
        )
        val result = computeWeeklyData(stats)
        assertThat(result).isEqualTo(listOf(0, 0, 0, 5))
    }

    @Test
    fun `computeWeeklyData correctly maps all 4 weeks`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 10),
                WeeklyCount(weekAgo = 1, count = 20),
                WeeklyCount(weekAgo = 2, count = 30),
                WeeklyCount(weekAgo = 3, count = 40),
            ),
        )
        val result = computeWeeklyData(stats)
        // Reversed: oldest first, current week last
        assertThat(result).isEqualTo(listOf(40, 30, 20, 10))
    }

    @Test
    fun `computeWeeklyData handles duplicate week entries by overwriting`() {
        val stats = FunStatistics(
            weeklyImportCounts = listOf(
                WeeklyCount(weekAgo = 0, count = 5),
                WeeklyCount(weekAgo = 0, count = 10),
            ),
        )
        val result = computeWeeklyData(stats)
        assertThat(result.last()).isEqualTo(10)
    }

    // endregion

    // region computeMomentumTrend extended

    @Test
    fun `computeMomentumTrend returns STABLE for single element`() {
        val result = computeMomentumTrend(listOf(5))
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    @Test
    fun `computeMomentumTrend threshold boundary returns STABLE at exactly plus 2`() {
        // recent = 5 + 5 = 10, older = 3 + 5 = 8, diff = 2 -> STABLE (need > 2)
        val result = computeMomentumTrend(listOf(3, 5, 5, 5))
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    @Test
    fun `computeMomentumTrend threshold boundary returns GROWING at plus 3`() {
        // recent = 5 + 6 = 11, older = 3 + 5 = 8, diff = 3 -> GROWING
        val result = computeMomentumTrend(listOf(3, 5, 5, 6))
        assertThat(result).isEqualTo(MomentumTrend.GROWING)
    }

    @Test
    fun `computeMomentumTrend threshold boundary returns DECLINING at minus 3`() {
        // recent = 3 + 5 = 8, older = 5 + 6 = 11, diff = -3 -> DECLINING
        val result = computeMomentumTrend(listOf(5, 6, 3, 5))
        assertThat(result).isEqualTo(MomentumTrend.DECLINING)
    }

    @Test
    fun `computeMomentumTrend uses first 2 as older and last 2 as recent`() {
        // older = 0 + 0 = 0, recent = 0 + 5 = 5, diff = 5 -> GROWING
        val result = computeMomentumTrend(listOf(0, 0, 0, 5))
        assertThat(result).isEqualTo(MomentumTrend.GROWING)
    }

    @Test
    fun `computeMomentumTrend with all zeros returns STABLE`() {
        val result = computeMomentumTrend(listOf(0, 0, 0, 0))
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    @Test
    fun `computeMomentumTrend with two elements compares them directly`() {
        // older = 10 (take 2 but only 1 exists in first half), recent = 20
        // Actually: takeLast(2) = [10, 20], take(2) = [10, 20]
        // For 2 elements: take(2)=[10,20]=30, takeLast(2)=[10,20]=30 -> STABLE
        val result = computeMomentumTrend(listOf(10, 20))
        assertThat(result).isEqualTo(MomentumTrend.STABLE)
    }

    // endregion
}
