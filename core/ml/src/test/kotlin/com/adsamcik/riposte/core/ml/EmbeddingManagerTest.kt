package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EmbeddingManagerTest {
    private lateinit var context: Context
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var memeEmbeddingDao: MemeEmbeddingDao
    private lateinit var versionManager: EmbeddingModelVersionManager
    private lateinit var appLifecycleTracker: AppLifecycleTracker
    private lateinit var isInBackgroundFlow: MutableStateFlow<Boolean>
    private lateinit var embeddingManager: EmbeddingManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()

        // Initialize WorkManager for testing
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        embeddingGenerator = mockk()
        memeEmbeddingDao = mockk(relaxed = true)
        versionManager = mockk()
        appLifecycleTracker = mockk()
        isInBackgroundFlow = MutableStateFlow(false)

        every { versionManager.currentModelVersion } returns "mediapipe_use:1.0.0"
        every { embeddingGenerator.initializationError } returns null
        every { appLifecycleTracker.isInBackground } returns isInBackgroundFlow

        embeddingManager =
            EmbeddingManager(
                context = context,
                embeddingGenerator = embeddingGenerator,
                memeEmbeddingDao = memeEmbeddingDao,
                versionManager = versionManager,
                appLifecycleTracker = appLifecycleTracker,
            )
    }

    @Test
    fun `generateAndStoreEmbedding creates embedding and stores it`() =
        runTest {
            // Given
            val memeId = 1L
            val searchText = "funny cat meme"
            val embedding = FloatArray(512) { it.toFloat() / 512f }

            coEvery { embeddingGenerator.generateFromText(searchText) } returns embedding
            coEvery { memeEmbeddingDao.insertEmbedding(any()) } returns 1L

            // When
            val result = embeddingManager.generateAndStoreEmbedding(memeId, searchText)

            // Then
            assertThat(result).isTrue()

            val entitySlot = slot<MemeEmbeddingEntity>()
            coVerify { memeEmbeddingDao.insertEmbedding(capture(entitySlot)) }

            val savedEntity = entitySlot.captured
            assertThat(savedEntity.memeId).isEqualTo(memeId)
            assertThat(savedEntity.dimension).isEqualTo(512)
            assertThat(savedEntity.modelVersion).isEqualTo("mediapipe_use:1.0.0")
            assertThat(savedEntity.needsRegeneration).isFalse()
        }

    @Test
    fun `generateAndStoreEmbedding returns false on failure`() =
        runTest {
            // Given
            val memeId = 1L
            val searchText = "funny cat meme"

            coEvery { embeddingGenerator.generateFromText(searchText) } throws RuntimeException("Model error")

            // When
            val result = embeddingManager.generateAndStoreEmbedding(memeId, searchText)

            // Then
            assertThat(result).isFalse()
            coVerify(exactly = 0) { memeEmbeddingDao.insertEmbedding(any()) }
        }

    @Test
    fun `getEmbedding returns decoded embedding`() =
        runTest {
            // Given
            val memeId = 1L
            val embeddingSize = 128
            val originalEmbedding = FloatArray(embeddingSize) { it.toFloat() }
            val encodedBytes = encodeEmbedding(originalEmbedding)

            val entity =
                MemeEmbeddingEntity(
                    id = 1,
                    memeId = memeId,
                    embedding = encodedBytes,
                    dimension = embeddingSize,
                    modelVersion = "test:1.0.0",
                    generatedAt = System.currentTimeMillis(),
                )

            coEvery { memeEmbeddingDao.getEmbeddingByMemeId(memeId) } returns entity

            // When
            val result = embeddingManager.getEmbedding(memeId)

            // Then
            assertThat(result).isNotNull()
            assertThat(result!!.size).isEqualTo(embeddingSize)
            // Verify first and last values
            assertThat(result[0]).isEqualTo(0f)
            assertThat(result[embeddingSize - 1]).isEqualTo((embeddingSize - 1).toFloat())
        }

    @Test
    fun `getEmbedding returns null when not found`() =
        runTest {
            // Given
            coEvery { memeEmbeddingDao.getEmbeddingByMemeId(any()) } returns null

            // When
            val result = embeddingManager.getEmbedding(999L)

            // Then
            assertThat(result).isNull()
        }

    @Test
    fun `hasValidEmbedding delegates to DAO`() =
        runTest {
            // Given
            coEvery { memeEmbeddingDao.hasValidEmbedding(1L) } returns true
            coEvery { memeEmbeddingDao.hasValidEmbedding(2L) } returns false

            // When & Then
            assertThat(embeddingManager.hasValidEmbedding(1L)).isTrue()
            assertThat(embeddingManager.hasValidEmbedding(2L)).isFalse()
        }

    @Test
    fun `getStatistics returns correct counts`() =
        runTest {
            // Given
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 100
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 20
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 5
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns
                listOf(
                    com.adsamcik.riposte.core.database.dao.EmbeddingVersionCount("mediapipe_use:1.0.0", 100),
                )

            // When
            val stats = embeddingManager.getStatistics()

            // Then
            assertThat(stats.validEmbeddingCount).isEqualTo(100)
            assertThat(stats.pendingEmbeddingCount).isEqualTo(20)
            assertThat(stats.regenerationNeededCount).isEqualTo(5)
            assertThat(stats.totalPendingWork).isEqualTo(25)
            assertThat(stats.isFullyIndexed).isFalse()
            assertThat(stats.embeddingsByVersion).containsEntry("mediapipe_use:1.0.0", 100)
        }

    @Test
    fun `checkAndHandleModelUpgrade marks old embeddings for regeneration`() =
        runTest {
            // Given
            coEvery { versionManager.hasModelBeenUpgraded() } returns true
            coEvery { versionManager.updateToCurrentVersion() } returns Unit

            // When
            embeddingManager.checkAndHandleModelUpgrade()

            // Then
            coVerify { memeEmbeddingDao.markOutdatedForRegeneration("mediapipe_use:1.0.0") }
            coVerify { versionManager.updateToCurrentVersion() }
        }

    @Test
    fun `checkAndHandleModelUpgrade does nothing when no upgrade`() =
        runTest {
            // Given
            coEvery { versionManager.hasModelBeenUpgraded() } returns false

            // When
            embeddingManager.checkAndHandleModelUpgrade()

            // Then
            coVerify(exactly = 0) { memeEmbeddingDao.markOutdatedForRegeneration(any()) }
            coVerify(exactly = 0) { versionManager.updateToCurrentVersion() }
        }

    // region No-Fallback Regression Tests
    //
    // These tests guard the design decision that model errors are surfaced
    // immediately in statistics, not deferred until "confirmed".
    // See docs/SEMANTIC_SEARCH.md "Error Handling — No Silent Fallback".

    @Test
    fun `getStatistics surfaces model error immediately without confirmation`() =
        runTest {
            // Given — model has error but NOT yet confirmed
            every { embeddingGenerator.initializationError } returns "Model not compatible with this device"
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 33
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            // When
            val stats = embeddingManager.getStatistics()

            // Then — error is surfaced immediately
            assertThat(stats.modelError).isEqualTo("Model not compatible with this device")
            assertThat(stats.validEmbeddingCount).isEqualTo(0)
            assertThat(stats.pendingEmbeddingCount).isEqualTo(33)
        }

    @Test
    fun `getStatistics does not call isErrorConfirmedForVersion`() =
        runTest {
            // Given
            every { embeddingGenerator.initializationError } returns "Model files not found"
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            // When
            embeddingManager.getStatistics()

            // Then — confirmation check must not be called (immediate surfacing)
            coVerify(exactly = 0) { versionManager.isErrorConfirmedForVersion(any()) }
        }

    @Test
    fun `getStatistics returns null modelError when no initialization error`() =
        runTest {
            // Given — model is healthy
            every { embeddingGenerator.initializationError } returns null
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 50
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            // When
            val stats = embeddingManager.getStatistics()

            // Then
            assertThat(stats.modelError).isNull()
            coVerify(exactly = 0) { versionManager.isErrorConfirmedForVersion(any()) }
        }

    @Test
    fun `getStatistics surfaces each error type correctly`() =
        runTest {
            val errorTypes = listOf(
                "Model not compatible with this device",
                "Model files not found",
                "Model failed to load",
                "Model initialization failed",
            )

            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            for (errorType in errorTypes) {
                every { embeddingGenerator.initializationError } returns errorType

                val stats = embeddingManager.getStatistics()

                assertThat(stats.modelError).isEqualTo(errorType)
            }
        }

    @Test
    fun `getStatistics with empty error string surfaces as non-null modelError`() =
        runTest {
            // Edge case: empty string error should still be surfaced, not treated as null
            every { embeddingGenerator.initializationError } returns ""
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            val stats = embeddingManager.getStatistics()

            assertThat(stats.modelError).isEqualTo("")
        }

    @Test
    fun `getStatistics with error still returns correct embedding counts`() =
        runTest {
            // When model has error, existing embedding counts should still be accurate
            every { embeddingGenerator.initializationError } returns "Model failed to load"
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 42
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 8
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 3
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            val stats = embeddingManager.getStatistics()

            assertThat(stats.modelError).isEqualTo("Model failed to load")
            assertThat(stats.validEmbeddingCount).isEqualTo(42)
            assertThat(stats.pendingEmbeddingCount).isEqualTo(8)
        }

    @Test
    fun `getStatistics called multiple times returns consistent error`() =
        runTest {
            every { embeddingGenerator.initializationError } returns "Model not compatible with this device"
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            val stats1 = embeddingManager.getStatistics()
            val stats2 = embeddingManager.getStatistics()

            assertThat(stats1.modelError).isEqualTo(stats2.modelError)
            assertThat(stats1.modelError).isEqualTo("Model not compatible with this device")
        }

    // endregion

    // region Foreground Resume Tests

    @Test
    fun `warmUpAndResumeIndexing schedules work when returning to foreground with pending memes`() =
        runTest {
            // Given
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 5
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns false

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                // When — start monitoring
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Simulate going to background then returning to foreground
                isInBackgroundFlow.value = true
                advanceUntilIdle()
                isInBackgroundFlow.value = false
                advanceUntilIdle()

                // Then — getStatistics was called for foreground check (beyond the initial startup check)
                // Two calls: one at startup, one on foreground return
                coVerify(atLeast = 2) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `warmUpAndResumeIndexing does not schedule work when fully indexed on foreground return`() =
        runTest {
            // Given — fully indexed
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns false

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                // When
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Simulate foreground return
                isInBackgroundFlow.value = true
                advanceUntilIdle()
                isInBackgroundFlow.value = false
                advanceUntilIdle()

                // Then — stats are checked but no additional scheduling needed
                coVerify(atLeast = 2) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `warmUpAndResumeIndexing skips foreground resume when model has error`() =
        runTest {
            // Given
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            every { embeddingGenerator.initializationError } returns "Model error"
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns true
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                // When
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Simulate foreground return
                isInBackgroundFlow.value = true
                advanceUntilIdle()
                isInBackgroundFlow.value = false
                advanceUntilIdle()

                // Then — should not check stats since model error is present
                // Both startup and foreground resume skip stats when model has error
                coVerify(exactly = 0) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `warmUpAndResumeIndexing does not react to initial background state`() =
        runTest {
            // Given — start in background
            isInBackgroundFlow.value = true
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 5
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns false

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                // When — start monitoring (initial value is dropped)
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Then — only startup check, no foreground resume triggered by initial value
                coVerify(exactly = 1) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `warmUpAndResumeIndexing handles multiple foreground returns`() =
        runTest {
            // Given
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 5
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns false

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                // When
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Simulate multiple background/foreground cycles
                repeat(3) {
                    isInBackgroundFlow.value = true
                    advanceUntilIdle()
                    isInBackgroundFlow.value = false
                    advanceUntilIdle()
                }

                // Then — startup + 3 foreground returns = 4 checks
                coVerify(atLeast = 4) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun `warmUpAndResumeIndexing handles statistics exception on foreground return gracefully`() =
        runTest {
            // Given
            coEvery { embeddingGenerator.initialize() } returns Unit
            coEvery { versionManager.clearInitializationFailure() } returns Unit
            coEvery { versionManager.hasModelBeenUpgraded() } returns false
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()
            coEvery { versionManager.isErrorConfirmedForVersion(any()) } returns false

            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob())
            try {
                embeddingManager.warmUpAndResumeIndexing(scope)
                advanceUntilIdle()

                // Now make stats throw on next foreground return
                coEvery { memeEmbeddingDao.countValidEmbeddings() } throws RuntimeException("DB error")

                // When — foreground return should not crash
                isInBackgroundFlow.value = true
                advanceUntilIdle()
                isInBackgroundFlow.value = false
                advanceUntilIdle()

                // Then — no crash, flow continues working
                // Verify next cycle still works after error recovery
                coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 10
                coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 3
                isInBackgroundFlow.value = true
                advanceUntilIdle()
                isInBackgroundFlow.value = false
                advanceUntilIdle()

                coVerify(atLeast = 1) { memeEmbeddingDao.countMemesWithoutEmbeddings() }
            } finally {
                scope.cancel()
            }
        }

    // endregion

    private fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer =
            java.nio.ByteBuffer.allocate(embedding.size * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
