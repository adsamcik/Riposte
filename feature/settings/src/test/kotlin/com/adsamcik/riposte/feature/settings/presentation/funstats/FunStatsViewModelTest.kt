package com.adsamcik.riposte.feature.settings.presentation.funstats

import android.content.Context
import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import com.adsamcik.riposte.feature.settings.domain.usecase.GetFunStatisticsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetMilestonesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.ObserveLibraryStatsUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FunStatsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var context: Context
    private lateinit var getFunStatisticsUseCase: GetFunStatisticsUseCase
    private lateinit var getMilestonesUseCase: GetMilestonesUseCase
    private lateinit var observeLibraryStatsUseCase: ObserveLibraryStatsUseCase
    private lateinit var viewModel: FunStatsViewModel

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        getFunStatisticsUseCase = mockk()
        getMilestonesUseCase = mockk()
        observeLibraryStatsUseCase = mockk()

        // Default mocks — override in individual tests as needed
        coEvery { getFunStatisticsUseCase() } returns FunStatistics()
        coEvery { getMilestonesUseCase(any()) } returns emptyList()
        every { observeLibraryStatsUseCase() } returns flowOf(LibraryStatistics())
    }

    private fun createViewModel(): FunStatsViewModel =
        FunStatsViewModel(
            context = context,
            getFunStatisticsUseCase = getFunStatisticsUseCase,
            getMilestonesUseCase = getMilestonesUseCase,
            observeLibraryStatsUseCase = observeLibraryStatsUseCase,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    // region Initialization Tests

    @Test
    fun `initial state is loading`() =
        runTest {
            viewModel = createViewModel()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isTrue()
        }

    @Test
    fun `loading success populates all fields`() =
        runTest {
            val topEmojis = listOf(
                EmojiUsageStats(emoji = "😂", emojiName = "laughing", count = 10),
                EmojiUsageStats(emoji = "🔥", emojiName = "fire", count = 5),
            )
            val stats = FunStatistics(
                totalStorageBytes = 5_000_000L,
                totalMemes = 42,
                favoriteMemes = 7,
                topEmojis = topEmojis,
            )
            val milestones = listOf(
                MilestoneState(id = "first_meme", icon = "🎉", isUnlocked = true),
                MilestoneState(id = "ten_memes", icon = "🔟", isUnlocked = false),
            )

            coEvery { getFunStatisticsUseCase() } returns stats
            coEvery { getMilestonesUseCase(stats) } returns milestones

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.totalStorageBytes).isEqualTo(5_000_000L)
            assertThat(state.topVibes).hasSize(2)
            assertThat(state.milestones).hasSize(2)
            assertThat(state.unlockedMilestoneCount).isEqualTo(1)
            assertThat(state.totalMilestoneCount).isEqualTo(2)
        }

    @Test
    fun `loading failure sets isLoading to false`() =
        runTest {
            coEvery { getFunStatisticsUseCase() } throws RuntimeException("DB error")

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
        }

    // endregion

    // region Library Stats Observation Tests

    @Test
    fun `observeLibraryStats updates totalMemeCount and favoriteMemeCount`() =
        runTest {
            val statsFlow = MutableStateFlow(LibraryStatistics(totalMemes = 10, favoriteMemes = 3))
            every { observeLibraryStatsUseCase() } returns statsFlow

            viewModel = createViewModel()
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertThat(state.totalMemeCount).isEqualTo(10)
            assertThat(state.favoriteMemeCount).isEqualTo(3)

            // Emit updated stats
            statsFlow.value = LibraryStatistics(totalMemes = 25, favoriteMemes = 8)
            advanceUntilIdle()

            state = viewModel.uiState.value
            assertThat(state.totalMemeCount).isEqualTo(25)
            assertThat(state.favoriteMemeCount).isEqualTo(8)
        }

    // endregion
}
