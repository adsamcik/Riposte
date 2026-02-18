package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GetMilestonesUseCaseTest {

    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var useCase: GetMilestonesUseCase

    @Before
    fun setup() {
        preferencesDataStore = mockk(relaxed = true)
        every { preferencesDataStore.unlockedMilestones } returns flowOf(emptyMap())
        useCase = GetMilestonesUseCase(preferencesDataStore)
    }

    @Test
    fun `returns all milestones`() = runTest {
        val stats = FunStatistics()
        val result = useCase(stats)
        assertThat(result).hasSize(MILESTONES.size)
    }

    @Test
    fun `unlocks first_steps when totalMemes is 1`() = runTest {
        val stats = FunStatistics(totalMemes = 1)
        val result = useCase(stats)
        val firstSteps = result.find { it.id == "first_steps" }
        assertThat(firstSteps).isNotNull()
        assertThat(firstSteps!!.isUnlocked).isTrue()
    }

    @Test
    fun `does not unlock century_club when totalMemes is 50`() = runTest {
        val stats = FunStatistics(totalMemes = 50)
        val result = useCase(stats)
        val centuryClub = result.find { it.id == "century_club" }
        assertThat(centuryClub).isNotNull()
        assertThat(centuryClub!!.isUnlocked).isFalse()
    }

    @Test
    fun `unlocks century_club when totalMemes is 100`() = runTest {
        val stats = FunStatistics(totalMemes = 100)
        val result = useCase(stats)
        val centuryClub = result.find { it.id == "century_club" }
        assertThat(centuryClub!!.isUnlocked).isTrue()
    }

    @Test
    fun `unlocks emoji_rainbow when uniqueEmojiCount is 15`() = runTest {
        val stats = FunStatistics(uniqueEmojiCount = 15)
        val result = useCase(stats)
        val emojiRainbow = result.find { it.id == "emoji_rainbow" }
        assertThat(emojiRainbow!!.isUnlocked).isTrue()
    }

    @Test
    fun `unlocks format_diplomat when distinctMimeTypes is 3`() = runTest {
        val stats = FunStatistics(distinctMimeTypes = 3)
        val result = useCase(stats)
        val formatDiplomat = result.find { it.id == "format_diplomat" }
        assertThat(formatDiplomat!!.isUnlocked).isTrue()
    }

    @Test
    fun `unlocks deep_diver when maxViewCount is 25`() = runTest {
        val stats = FunStatistics(maxViewCount = 25)
        val result = useCase(stats)
        val deepDiver = result.find { it.id == "deep_diver" }
        assertThat(deepDiver!!.isUnlocked).isTrue()
    }

    @Test
    fun `preserves previously unlocked milestones`() = runTest {
        every { preferencesDataStore.unlockedMilestones } returns flowOf(
            mapOf("first_steps" to 1000L),
        )
        val stats = FunStatistics(totalMemes = 0)
        val result = useCase(stats)
        val firstSteps = result.find { it.id == "first_steps" }
        assertThat(firstSteps!!.isUnlocked).isTrue()
        assertThat(firstSteps.unlockedAt).isEqualTo(1000L)
    }

    @Test
    fun `persists newly unlocked milestones`() = runTest {
        val stats = FunStatistics(totalMemes = 5)
        useCase(stats)
        coVerify { preferencesDataStore.unlockMilestone("first_steps") }
    }

    @Test
    fun `leet milestone only unlocks at exactly 1337`() = runTest {
        val stats1336 = FunStatistics(totalMemes = 1336)
        val result1336 = useCase(stats1336)
        assertThat(result1336.find { it.id == "leet" }!!.isUnlocked).isFalse()

        val stats1337 = FunStatistics(totalMemes = 1337)
        val result1337 = useCase(stats1337)
        assertThat(result1337.find { it.id == "leet" }!!.isUnlocked).isTrue()

        val stats1338 = FunStatistics(totalMemes = 1338)
        val result1338 = useCase(stats1338)
        assertThat(result1338.find { it.id == "leet" }!!.isUnlocked).isFalse()
    }

    // region Boundary tests for all milestones

    @Test
    fun `first_steps not unlocked at 0 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 0))
        assertThat(result.find { it.id == "first_steps" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `getting_started not unlocked at 9 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 9))
        assertThat(result.find { it.id == "getting_started" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `getting_started unlocked at 10 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 10))
        assertThat(result.find { it.id == "getting_started" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `half_century not unlocked at 49 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 49))
        assertThat(result.find { it.id == "half_century" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `half_century unlocked at 50 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 50))
        assertThat(result.find { it.id == "half_century" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `the_archivist not unlocked at 499 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 499))
        assertThat(result.find { it.id == "the_archivist" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `the_archivist unlocked at 500 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 500))
        assertThat(result.find { it.id == "the_archivist" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `meme_hoarder not unlocked at 999 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 999))
        assertThat(result.find { it.id == "meme_hoarder" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `meme_hoarder unlocked at 1000 memes`() = runTest {
        val result = useCase(FunStatistics(totalMemes = 1000))
        assertThat(result.find { it.id == "meme_hoarder" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `loyal_fan not unlocked at 24 favorites`() = runTest {
        val result = useCase(FunStatistics(favoriteMemes = 24))
        assertThat(result.find { it.id == "loyal_fan" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `loyal_fan unlocked at 25 favorites`() = runTest {
        val result = useCase(FunStatistics(favoriteMemes = 25))
        assertThat(result.find { it.id == "loyal_fan" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `emoji_rainbow not unlocked at 14 emojis`() = runTest {
        val result = useCase(FunStatistics(uniqueEmojiCount = 14))
        assertThat(result.find { it.id == "emoji_rainbow" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `format_diplomat not unlocked at 2 types`() = runTest {
        val result = useCase(FunStatistics(distinctMimeTypes = 2))
        assertThat(result.find { it.id == "format_diplomat" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `deep_diver not unlocked at 24 views`() = runTest {
        val result = useCase(FunStatistics(maxViewCount = 24))
        assertThat(result.find { it.id == "deep_diver" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `social_butterfly not unlocked at 99 uses`() = runTest {
        val result = useCase(FunStatistics(totalUseCount = 99))
        assertThat(result.find { it.id == "social_butterfly" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `social_butterfly unlocked at 100 uses`() = runTest {
        val result = useCase(FunStatistics(totalUseCount = 100))
        assertThat(result.find { it.id == "social_butterfly" }!!.isUnlocked).isTrue()
    }

    // endregion

    // region night_owl milestone

    @Test
    fun `night_owl unlocked when oldest import is at midnight`() = runTest {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
        }
        val stats = FunStatistics(oldestImportTimestamp = calendar.timeInMillis)
        val result = useCase(stats)
        assertThat(result.find { it.id == "night_owl" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `night_owl unlocked when oldest import is at 3 AM`() = runTest {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 3)
            set(java.util.Calendar.MINUTE, 59)
        }
        val stats = FunStatistics(oldestImportTimestamp = calendar.timeInMillis)
        val result = useCase(stats)
        assertThat(result.find { it.id == "night_owl" }!!.isUnlocked).isTrue()
    }

    @Test
    fun `night_owl not unlocked when oldest import is at 4 AM`() = runTest {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 4)
            set(java.util.Calendar.MINUTE, 0)
        }
        val stats = FunStatistics(oldestImportTimestamp = calendar.timeInMillis)
        val result = useCase(stats)
        assertThat(result.find { it.id == "night_owl" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `night_owl not unlocked when oldest import is null`() = runTest {
        val stats = FunStatistics(oldestImportTimestamp = null)
        val result = useCase(stats)
        assertThat(result.find { it.id == "night_owl" }!!.isUnlocked).isFalse()
    }

    @Test
    fun `night_owl not unlocked at noon`() = runTest {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
        }
        val stats = FunStatistics(oldestImportTimestamp = calendar.timeInMillis)
        val result = useCase(stats)
        assertThat(result.find { it.id == "night_owl" }!!.isUnlocked).isFalse()
    }

    // endregion

    // region Icon and ordering

    @Test
    fun `all milestones have correct icons`() = runTest {
        val expectedIcons = mapOf(
            "first_steps" to "👶",
            "getting_started" to "📦",
            "half_century" to "🎯",
            "century_club" to "💯",
            "the_archivist" to "📚",
            "meme_hoarder" to "🏰",
            "loyal_fan" to "❤️",
            "emoji_rainbow" to "🌈",
            "format_diplomat" to "🤝",
            "deep_diver" to "🤿",
            "social_butterfly" to "🦋",
            "night_owl" to "🦉",
            "leet" to "🏴\u200D☠️",
        )

        val stats = FunStatistics()
        val result = useCase(stats)

        for ((id, expectedIcon) in expectedIcons) {
            val milestone = result.find { it.id == id }
            assertThat(milestone).isNotNull()
            assertThat(milestone!!.icon).isEqualTo(expectedIcon)
        }
    }

    @Test
    fun `milestones preserve ordering from definition`() = runTest {
        val stats = FunStatistics()
        val result = useCase(stats)
        val ids = result.map { it.id }
        assertThat(ids).isEqualTo(
            listOf(
                "first_steps", "getting_started", "half_century", "century_club",
                "the_archivist", "meme_hoarder", "loyal_fan", "emoji_rainbow",
                "format_diplomat", "deep_diver", "social_butterfly", "night_owl", "leet",
            ),
        )
    }

    @Test
    fun `multiple milestones can be unlocked simultaneously`() = runTest {
        val stats = FunStatistics(
            totalMemes = 100,
            favoriteMemes = 25,
            uniqueEmojiCount = 15,
        )
        val result = useCase(stats)
        val unlockedIds = result.filter { it.isUnlocked }.map { it.id }
        assertThat(unlockedIds).containsAtLeast(
            "first_steps", "getting_started", "half_century", "century_club",
            "loyal_fan", "emoji_rainbow",
        )
    }

    @Test
    fun `no milestones unlocked for default stats`() = runTest {
        val stats = FunStatistics()
        val result = useCase(stats)
        val unlockedCount = result.count { it.isUnlocked }
        assertThat(unlockedCount).isEqualTo(0)
    }

    @Test
    fun `does not persist milestones when none are newly unlocked`() = runTest {
        val stats = FunStatistics()
        useCase(stats)
        coVerify(exactly = 0) { preferencesDataStore.unlockMilestone(any()) }
    }

    // endregion
}
