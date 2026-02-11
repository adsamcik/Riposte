package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddingManagerTest {
    private lateinit var context: Context
    private lateinit var embeddingGenerator: EmbeddingGenerator
    private lateinit var memeEmbeddingDao: MemeEmbeddingDao
    private lateinit var versionManager: EmbeddingModelVersionManager
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

        every { versionManager.currentModelVersion } returns "mediapipe_use:1.0.0"
        every { embeddingGenerator.initializationError } returns null

        embeddingManager =
            EmbeddingManager(
                context = context,
                embeddingGenerator = embeddingGenerator,
                memeEmbeddingDao = memeEmbeddingDao,
                versionManager = versionManager,
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
                    com.adsamcik.riposte.core.database.dao.EmbeddingVersionCount("mediapipe_use:1.0.0", 80),
                    com.adsamcik.riposte.core.database.dao.EmbeddingVersionCount("simple_hash:1.0.0", 20),
                )

            // When
            val stats = embeddingManager.getStatistics()

            // Then
            assertThat(stats.validEmbeddingCount).isEqualTo(100)
            assertThat(stats.pendingEmbeddingCount).isEqualTo(20)
            assertThat(stats.regenerationNeededCount).isEqualTo(5)
            assertThat(stats.totalPendingWork).isEqualTo(25)
            assertThat(stats.isFullyIndexed).isFalse()
            assertThat(stats.embeddingsByVersion).containsEntry("mediapipe_use:1.0.0", 80)
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

    @Test
    fun `getStatistics includes model error when initialization failed`() =
        runTest {
            // Given
            every { embeddingGenerator.initializationError } returns "Model not compatible with this device"
            coEvery { memeEmbeddingDao.countValidEmbeddings() } returns 0
            coEvery { memeEmbeddingDao.countMemesWithoutEmbeddings() } returns 33
            coEvery { memeEmbeddingDao.countEmbeddingsNeedingRegeneration() } returns 0
            coEvery { memeEmbeddingDao.getEmbeddingCountByModelVersion() } returns emptyList()

            // When
            val stats = embeddingManager.getStatistics()

            // Then
            assertThat(stats.modelError).isEqualTo("Model not compatible with this device")
            assertThat(stats.validEmbeddingCount).isEqualTo(0)
            assertThat(stats.pendingEmbeddingCount).isEqualTo(33)
        }

    private fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer =
            java.nio.ByteBuffer.allocate(embedding.size * 4)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }
}
