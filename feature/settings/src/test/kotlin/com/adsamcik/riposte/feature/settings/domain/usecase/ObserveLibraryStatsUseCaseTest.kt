package com.adsamcik.riposte.feature.settings.domain.usecase

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.database.LibraryStatsProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveLibraryStatsUseCaseTest {
    private lateinit var statsProvider: LibraryStatsProvider
    private lateinit var useCase: ObserveLibraryStatsUseCase

    @Before
    fun setup() {
        statsProvider = mockk()
    }

    // region Happy Path

    @Test
    fun `emits library statistics from provider`() = runTest {
        val expected = LibraryStatistics(
            totalMemes = 42,
            favoriteMemes = 7,
            indexedMemes = 35,
            pendingIndexing = 7,
        )
        every { statsProvider.observeStatistics() } returns flowOf(expected)
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase().test {
            val item = awaitItem()
            assertThat(item).isEqualTo(expected)
            awaitComplete()
        }
    }

    @Test
    fun `emits multiple updates when statistics change`() = runTest {
        val first = LibraryStatistics(totalMemes = 10, favoriteMemes = 1, indexedMemes = 5, pendingIndexing = 5)
        val second = LibraryStatistics(totalMemes = 20, favoriteMemes = 3, indexedMemes = 18, pendingIndexing = 2)
        every { statsProvider.observeStatistics() } returns flowOf(first, second)
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase().test {
            assertThat(awaitItem()).isEqualTo(first)
            assertThat(awaitItem()).isEqualTo(second)
            awaitComplete()
        }
    }

    // endregion

    // region Empty Data

    @Test
    fun `emits default zero statistics when library is empty`() = runTest {
        val empty = LibraryStatistics(totalMemes = 0, favoriteMemes = 0, indexedMemes = 0, pendingIndexing = 0)
        every { statsProvider.observeStatistics() } returns flowOf(empty)
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase().test {
            val item = awaitItem()
            assertThat(item.totalMemes).isEqualTo(0)
            assertThat(item.favoriteMemes).isEqualTo(0)
            assertThat(item.indexedMemes).isEqualTo(0)
            assertThat(item.pendingIndexing).isEqualTo(0)
            awaitComplete()
        }
    }

    @Test
    fun `completes immediately when provider emits empty flow`() = runTest {
        every { statsProvider.observeStatistics() } returns emptyFlow()
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase().test {
            awaitComplete()
        }
    }

    // endregion

    // region Error Propagation

    @Test
    fun `propagates error from stats provider`() = runTest {
        val exception = RuntimeException("Database read error")
        every { statsProvider.observeStatistics() } returns flow { throw exception }
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase().test {
            val error = awaitError()
            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).isEqualTo("Database read error")
        }
    }

    // endregion

    // region Delegation

    @Test
    fun `delegates to stats provider exactly once per invoke`() = runTest {
        every { statsProvider.observeStatistics() } returns emptyFlow()
        useCase = ObserveLibraryStatsUseCase(statsProvider)

        useCase()

        verify(exactly = 1) { statsProvider.observeStatistics() }
    }

    // endregion
}
