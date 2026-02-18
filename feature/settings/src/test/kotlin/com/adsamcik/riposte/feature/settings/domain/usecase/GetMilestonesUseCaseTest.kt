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
}
