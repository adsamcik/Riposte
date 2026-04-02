package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.database.FunStatsProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GetFunStatisticsUseCaseTest {
    private lateinit var funStatsProvider: FunStatsProvider
    private lateinit var useCase: GetFunStatisticsUseCase

    @Before
    fun setup() {
        funStatsProvider = mockk()
        useCase = GetFunStatisticsUseCase(funStatsProvider)
    }

    // region Happy Path

    @Test
    fun `returns fun statistics from provider`() = runTest {
        val expected = FunStatistics(
            totalStorageBytes = 1_000_000L,
            averageFileSize = 50_000L,
            largestFileSize = 200_000L,
            totalUseCount = 150,
            totalViewCount = 500,
            maxViewCount = 42,
            totalMemes = 100,
            favoriteMemes = 10,
            uniqueEmojiCount = 25,
            completedImports = 5,
            lastImportTimestamp = 1_700_000_000L,
            totalImportedMemes = 100,
        )
        coEvery { funStatsProvider.getStatistics() } returns expected

        val result = useCase()

        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `delegates to provider exactly once`() = runTest {
        coEvery { funStatsProvider.getStatistics() } returns FunStatistics()

        useCase()

        coVerify(exactly = 1) { funStatsProvider.getStatistics() }
    }

    // endregion

    // region Empty Data

    @Test
    fun `returns default statistics when library has no memes`() = runTest {
        val emptyStats = FunStatistics()
        coEvery { funStatsProvider.getStatistics() } returns emptyStats

        val result = useCase()

        assertThat(result.totalMemes).isEqualTo(0)
        assertThat(result.totalStorageBytes).isEqualTo(0)
        assertThat(result.totalUseCount).isEqualTo(0)
        assertThat(result.totalViewCount).isEqualTo(0)
        assertThat(result.uniqueEmojiCount).isEqualTo(0)
        assertThat(result.topEmojis).isEmpty()
        assertThat(result.weeklyImportCounts).isEmpty()
        assertThat(result.lastImportTimestamp).isNull()
    }

    // endregion

    // region Error Propagation

    @Test
    fun `propagates RuntimeException from provider`() = runTest {
        coEvery { funStatsProvider.getStatistics() } throws RuntimeException("Database corrupted")

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertThat(exception).isInstanceOf(RuntimeException::class.java)
        assertThat(exception!!.message).isEqualTo("Database corrupted")
    }

    @Test
    fun `propagates IllegalStateException from provider`() = runTest {
        coEvery { funStatsProvider.getStatistics() } throws IllegalStateException("DB not initialized")

        val exception = runCatching { useCase() }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception!!.message).isEqualTo("DB not initialized")
    }

    // endregion

    // region Edge Cases

    @Test
    fun `returns statistics with null timestamp fields`() = runTest {
        val stats = FunStatistics(
            totalMemes = 5,
            lastImportTimestamp = null,
            oldestImportTimestamp = null,
            newestImportTimestamp = null,
        )
        coEvery { funStatsProvider.getStatistics() } returns stats

        val result = useCase()

        assertThat(result.lastImportTimestamp).isNull()
        assertThat(result.oldestImportTimestamp).isNull()
        assertThat(result.newestImportTimestamp).isNull()
    }

    @Test
    fun `returns statistics with large values`() = runTest {
        val stats = FunStatistics(
            totalStorageBytes = Long.MAX_VALUE,
            totalMemes = Int.MAX_VALUE,
            totalUseCount = Int.MAX_VALUE,
        )
        coEvery { funStatsProvider.getStatistics() } returns stats

        val result = useCase()

        assertThat(result.totalStorageBytes).isEqualTo(Long.MAX_VALUE)
        assertThat(result.totalMemes).isEqualTo(Int.MAX_VALUE)
    }

    // endregion
}
