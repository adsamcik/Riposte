package com.adsamcik.riposte.feature.settings.domain.usecase

import app.cash.turbine.test
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.EmbeddingModelInfo
import com.adsamcik.riposte.core.ml.EmbeddingStatistics
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveEmbeddingStatisticsUseCaseTest {
    private lateinit var embeddingManager: EmbeddingManager
    private lateinit var useCase: ObserveEmbeddingStatisticsUseCase

    private fun createStatistics(
        validCount: Int = 100,
        pendingCount: Int = 10,
        regenerationCount: Int = 5,
        currentVersion: String = "v2",
    ) = EmbeddingStatistics(
        validEmbeddingCount = validCount,
        pendingEmbeddingCount = pendingCount,
        regenerationNeededCount = regenerationCount,
        currentModelVersion = currentVersion,
        embeddingsByVersion = mapOf(currentVersion to validCount),
    )

    private fun createModelInfo(
        version: String = "v2",
        name: String = "EmbeddingGemma",
        dimension: Int = 768,
        description: String = "Generic embedding model",
    ) = EmbeddingModelInfo(
        version = version,
        name = name,
        dimension = dimension,
        description = description,
    )

    @Before
    fun setup() {
        embeddingManager = mockk()
    }

    // region Happy Path

    @Test
    fun `emits combined statistics and model info on valid embedding count change`() = runTest {
        val stats = createStatistics()
        val modelInfo = createModelInfo()

        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(100)
        coEvery { embeddingManager.getStatistics() } returns stats
        every { embeddingManager.getModelInfo() } returns modelInfo
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val item = awaitItem()
            assertThat(item.statistics).isEqualTo(stats)
            assertThat(item.modelInfo).isEqualTo(modelInfo)
            awaitComplete()
        }
    }

    @Test
    fun `re-fetches statistics on each new embedding count emission`() = runTest {
        val stats1 = createStatistics(validCount = 50, pendingCount = 50)
        val stats2 = createStatistics(validCount = 80, pendingCount = 20)
        val modelInfo = createModelInfo()

        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(50, 80)
        coEvery { embeddingManager.getStatistics() } returnsMany listOf(stats1, stats2)
        every { embeddingManager.getModelInfo() } returns modelInfo
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val first = awaitItem()
            assertThat(first.statistics.validEmbeddingCount).isEqualTo(50)

            val second = awaitItem()
            assertThat(second.statistics.validEmbeddingCount).isEqualTo(80)
            awaitComplete()
        }
    }

    // endregion

    // region Empty Data

    @Test
    fun `emits zero statistics when no embeddings exist`() = runTest {
        val emptyStats = createStatistics(validCount = 0, pendingCount = 0, regenerationCount = 0)
        val modelInfo = createModelInfo()

        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(0)
        coEvery { embeddingManager.getStatistics() } returns emptyStats
        every { embeddingManager.getModelInfo() } returns modelInfo
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val item = awaitItem()
            assertThat(item.statistics.validEmbeddingCount).isEqualTo(0)
            assertThat(item.statistics.isFullyIndexed).isTrue()
            awaitComplete()
        }
    }

    @Test
    fun `completes without emission when observe flow is empty`() = runTest {
        every { embeddingManager.observeValidEmbeddingCount() } returns emptyFlow()
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            awaitComplete()
        }
    }

    // endregion

    // region Error Propagation

    @Test
    fun `propagates error when observeValidEmbeddingCount throws`() = runTest {
        every { embeddingManager.observeValidEmbeddingCount() } returns flow {
            throw RuntimeException("DB connection lost")
        }
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val error = awaitError()
            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).isEqualTo("DB connection lost")
        }
    }

    @Test
    fun `propagates error when getStatistics throws inside flatMapLatest`() = runTest {
        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(10)
        coEvery { embeddingManager.getStatistics() } throws RuntimeException("Statistics unavailable")
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val error = awaitError()
            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).isEqualTo("Statistics unavailable")
        }
    }

    @Test
    fun `propagates error when getModelInfo throws inside flatMapLatest`() = runTest {
        val stats = createStatistics()
        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(10)
        coEvery { embeddingManager.getStatistics() } returns stats
        every { embeddingManager.getModelInfo() } throws RuntimeException("Model not loaded")
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val error = awaitError()
            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).isEqualTo("Model not loaded")
        }
    }

    // endregion

    // region Edge Cases

    @Test
    fun `statistics reports model error when present`() = runTest {
        val stats = EmbeddingStatistics(
            validEmbeddingCount = 50,
            pendingEmbeddingCount = 10,
            regenerationNeededCount = 0,
            currentModelVersion = "v2",
            embeddingsByVersion = mapOf("v2" to 50),
            modelError = "Model failed to load",
        )
        val modelInfo = createModelInfo()

        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(50)
        coEvery { embeddingManager.getStatistics() } returns stats
        every { embeddingManager.getModelInfo() } returns modelInfo
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val item = awaitItem()
            assertThat(item.statistics.modelError).isEqualTo("Model failed to load")
            awaitComplete()
        }
    }

    @Test
    fun `totalPendingWork includes both pending and regeneration counts`() = runTest {
        val stats = createStatistics(validCount = 50, pendingCount = 15, regenerationCount = 5)
        val modelInfo = createModelInfo()

        every { embeddingManager.observeValidEmbeddingCount() } returns flowOf(50)
        coEvery { embeddingManager.getStatistics() } returns stats
        every { embeddingManager.getModelInfo() } returns modelInfo
        useCase = ObserveEmbeddingStatisticsUseCase(embeddingManager)

        useCase().test {
            val item = awaitItem()
            assertThat(item.statistics.totalPendingWork).isEqualTo(20)
            awaitComplete()
        }
    }

    // endregion
}
