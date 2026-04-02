package com.adsamcik.riposte.feature.settings.data

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.dao.DuplicateDetectionDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.entity.PotentialDuplicateEntity
import com.adsamcik.riposte.core.ml.DHashCalculator
import com.adsamcik.riposte.feature.settings.domain.model.MemeEntityMerger
import com.adsamcik.riposte.feature.settings.domain.model.MergedMemeData
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
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
class DefaultDuplicateDetectionRepositoryTest {
    private lateinit var dao: DuplicateDetectionDao
    private lateinit var dHashCalculator: DHashCalculator
    private lateinit var merger: MemeEntityMerger
    private lateinit var repository: DefaultDuplicateDetectionRepository

    private fun createMemeEntity(
        id: Long = 1L,
        filePath: String = "/memes/meme_$id.jpg",
        width: Int = 1080,
        height: Int = 1920,
        fileSizeBytes: Long = 50_000L,
        perceptualHash: Long? = null,
        fileHash: String? = null,
    ) = MemeEntity(
        id = id,
        filePath = filePath,
        fileName = "meme_$id.jpg",
        mimeType = "image/jpeg",
        width = width,
        height = height,
        fileSizeBytes = fileSizeBytes,
        importedAt = System.currentTimeMillis(),
        emojiTagsJson = "[]",
        perceptualHash = perceptualHash,
        fileHash = fileHash,
    )

    private fun createDuplicateEntity(
        id: Long = 1L,
        memeId1: Long = 1L,
        memeId2: Long = 2L,
        hammingDistance: Int = 3,
        detectionMethod: String = "perceptual",
    ) = PotentialDuplicateEntity(
        id = id,
        memeId1 = memeId1,
        memeId2 = memeId2,
        hammingDistance = hammingDistance,
        detectionMethod = detectionMethod,
        detectedAt = System.currentTimeMillis(),
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        dHashCalculator = mockk()
        merger = mockk()
        repository = DefaultDuplicateDetectionRepository(dao, dHashCalculator, merger)
    }

    // region observePendingCount

    @Test
    fun `observePendingCount delegates to dao`() = runTest {
        every { dao.getPendingDuplicateCount() } returns flowOf(5)

        repository.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(5)
            awaitComplete()
        }
    }

    @Test
    fun `observePendingCount emits zero when no duplicates`() = runTest {
        every { dao.getPendingDuplicateCount() } returns flowOf(0)

        repository.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(0)
            awaitComplete()
        }
    }

    // endregion

    // region observeDuplicateGroups

    @Test
    fun `observeDuplicateGroups maps duplicates to groups`() = runTest {
        val dup = createDuplicateEntity(id = 1, memeId1 = 1, memeId2 = 2)
        val meme1 = createMemeEntity(id = 1)
        val meme2 = createMemeEntity(id = 2)

        every { dao.getPendingDuplicates() } returns flowOf(listOf(dup))
        coEvery { dao.getMemeById(1) } returns meme1
        coEvery { dao.getMemeById(2) } returns meme2

        repository.observeDuplicateGroups().test {
            val groups = awaitItem()
            assertThat(groups).hasSize(1)
            assertThat(groups[0].duplicateId).isEqualTo(1L)
            assertThat(groups[0].meme1).isEqualTo(meme1)
            assertThat(groups[0].meme2).isEqualTo(meme2)
            assertThat(groups[0].hammingDistance).isEqualTo(3)
            assertThat(groups[0].detectionMethod).isEqualTo("perceptual")
            awaitComplete()
        }
    }

    @Test
    fun `observeDuplicateGroups filters out duplicates with missing memes`() = runTest {
        val dup1 = createDuplicateEntity(id = 1, memeId1 = 1, memeId2 = 2)
        val dup2 = createDuplicateEntity(id = 2, memeId1 = 3, memeId2 = 4)
        val meme1 = createMemeEntity(id = 1)
        val meme2 = createMemeEntity(id = 2)

        every { dao.getPendingDuplicates() } returns flowOf(listOf(dup1, dup2))
        coEvery { dao.getMemeById(1) } returns meme1
        coEvery { dao.getMemeById(2) } returns meme2
        coEvery { dao.getMemeById(3) } returns null // deleted meme
        coEvery { dao.getMemeById(4) } returns createMemeEntity(id = 4)

        repository.observeDuplicateGroups().test {
            val groups = awaitItem()
            assertThat(groups).hasSize(1)
            assertThat(groups[0].duplicateId).isEqualTo(1L)
            awaitComplete()
        }
    }

    @Test
    fun `observeDuplicateGroups returns empty list when no pending duplicates`() = runTest {
        every { dao.getPendingDuplicates() } returns flowOf(emptyList())

        repository.observeDuplicateGroups().test {
            assertThat(awaitItem()).isEmpty()
            awaitComplete()
        }
    }

    @Test
    fun `observeDuplicateGroups propagates error from dao`() = runTest {
        every { dao.getPendingDuplicates() } returns flow { throw RuntimeException("DB error") }

        repository.observeDuplicateGroups().test {
            val error = awaitError()
            assertThat(error).isInstanceOf(RuntimeException::class.java)
            assertThat(error.message).isEqualTo("DB error")
        }
    }

    // endregion

    // region dismissDuplicate

    @Test
    fun `dismissDuplicate delegates to dao`() = runTest {
        repository.dismissDuplicate(42L)

        coVerify(exactly = 1) { dao.dismissDuplicate(42L) }
    }

    // endregion

    // region dismissAll

    @Test
    fun `dismissAll delegates to dao`() = runTest {
        repository.dismissAll()

        coVerify(exactly = 1) { dao.dismissAllPending() }
    }

    // endregion

    // region mergeDuplicates

    @Test
    fun `mergeDuplicates performs merge and returns result`() = runTest {
        val dup = createDuplicateEntity(id = 10, memeId1 = 1, memeId2 = 2)
        val meme1 = createMemeEntity(id = 1, width = 1920, height = 1080)
        val meme2 = createMemeEntity(id = 2, width = 800, height = 600)
        val mergedData = MergedMemeData(
            winnerId = 1L,
            loserId = 2L,
            loserFilePath = "/memes/meme_2.jpg",
            emojiTagsJson = "[]",
            title = null,
            description = null,
            textContent = null,
            searchPhrasesJson = null,
            useCount = 0,
            viewCount = 0,
            isFavorite = false,
        )

        coEvery { dao.getPendingDuplicateById(10L) } returns dup
        coEvery { dao.getMemeById(1L) } returns meme1
        coEvery { dao.getMemeById(2L) } returns meme2
        every { merger.merge(meme1, meme2) } returns mergedData

        val result = repository.mergeDuplicates(10L)

        assertThat(result.winnerId).isEqualTo(1L)
        assertThat(result.loserId).isEqualTo(2L)
        assertThat(result.loserFilePath).isEqualTo("/memes/meme_2.jpg")

        coVerify {
            dao.performMerge(
                winnerId = 1L,
                loserId = 2L,
                duplicateId = 10L,
                emojiTagsJson = "[]",
                title = null,
                description = null,
                textContent = null,
                searchPhrasesJson = null,
                useCount = 0,
                viewCount = 0,
                isFavorite = false,
            )
        }
    }

    @Test
    fun `mergeDuplicates throws when duplicate not found`() = runTest {
        coEvery { dao.getPendingDuplicateById(99L) } returns null

        val exception = runCatching { repository.mergeDuplicates(99L) }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(exception!!.message).contains("99")
    }

    @Test
    fun `mergeDuplicates throws when meme1 not found`() = runTest {
        val dup = createDuplicateEntity(id = 10, memeId1 = 1, memeId2 = 2)
        coEvery { dao.getPendingDuplicateById(10L) } returns dup
        coEvery { dao.getMemeById(1L) } returns null

        val exception = runCatching { repository.mergeDuplicates(10L) }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception!!.message).contains("1")
    }

    @Test
    fun `mergeDuplicates throws when meme2 not found`() = runTest {
        val dup = createDuplicateEntity(id = 10, memeId1 = 1, memeId2 = 2)
        val meme1 = createMemeEntity(id = 1)
        coEvery { dao.getPendingDuplicateById(10L) } returns dup
        coEvery { dao.getMemeById(1L) } returns meme1
        coEvery { dao.getMemeById(2L) } returns null

        val exception = runCatching { repository.mergeDuplicates(10L) }.exceptionOrNull()

        assertThat(exception).isInstanceOf(IllegalStateException::class.java)
        assertThat(exception!!.message).contains("2")
    }

    // endregion

    // region mergeAll

    @Test
    fun `mergeAll merges all pending duplicates`() = runTest {
        val dup1 = createDuplicateEntity(id = 1, memeId1 = 1, memeId2 = 2)
        val dup2 = createDuplicateEntity(id = 2, memeId1 = 3, memeId2 = 4)
        val meme1 = createMemeEntity(id = 1)
        val meme2 = createMemeEntity(id = 2)
        val meme3 = createMemeEntity(id = 3)
        val meme4 = createMemeEntity(id = 4)

        coEvery { dao.getPendingDuplicatesList() } returns listOf(dup1, dup2)
        coEvery { dao.getPendingDuplicateById(1L) } returns dup1
        coEvery { dao.getPendingDuplicateById(2L) } returns dup2
        coEvery { dao.getMemeById(1L) } returns meme1
        coEvery { dao.getMemeById(2L) } returns meme2
        coEvery { dao.getMemeById(3L) } returns meme3
        coEvery { dao.getMemeById(4L) } returns meme4

        val mergedData1 = MergedMemeData(
            winnerId = 1L, loserId = 2L, loserFilePath = "/memes/meme_2.jpg",
            emojiTagsJson = "[]", title = null, description = null, textContent = null,
            searchPhrasesJson = null, useCount = 0, viewCount = 0, isFavorite = false,
        )
        val mergedData2 = MergedMemeData(
            winnerId = 3L, loserId = 4L, loserFilePath = "/memes/meme_4.jpg",
            emojiTagsJson = "[]", title = null, description = null, textContent = null,
            searchPhrasesJson = null, useCount = 0, viewCount = 0, isFavorite = false,
        )
        every { merger.merge(meme1, meme2) } returns mergedData1
        every { merger.merge(meme3, meme4) } returns mergedData2

        val results = repository.mergeAll()

        assertThat(results).hasSize(2)
        assertThat(results[0].winnerId).isEqualTo(1L)
        assertThat(results[1].winnerId).isEqualTo(3L)
    }

    @Test
    fun `mergeAll returns empty list when no pending duplicates`() = runTest {
        coEvery { dao.getPendingDuplicatesList() } returns emptyList()

        val results = repository.mergeAll()

        assertThat(results).isEmpty()
    }

    @Test
    fun `mergeAll skips failed merges and continues with remaining`() = runTest {
        val dup1 = createDuplicateEntity(id = 1, memeId1 = 1, memeId2 = 2)
        val dup2 = createDuplicateEntity(id = 2, memeId1 = 3, memeId2 = 4)
        val meme3 = createMemeEntity(id = 3)
        val meme4 = createMemeEntity(id = 4)

        coEvery { dao.getPendingDuplicatesList() } returns listOf(dup1, dup2)
        // First merge fails — duplicate not found
        coEvery { dao.getPendingDuplicateById(1L) } returns null
        // Second merge succeeds
        coEvery { dao.getPendingDuplicateById(2L) } returns dup2
        coEvery { dao.getMemeById(3L) } returns meme3
        coEvery { dao.getMemeById(4L) } returns meme4

        val mergedData = MergedMemeData(
            winnerId = 3L, loserId = 4L, loserFilePath = "/memes/meme_4.jpg",
            emojiTagsJson = "[]", title = null, description = null, textContent = null,
            searchPhrasesJson = null, useCount = 0, viewCount = 0, isFavorite = false,
        )
        every { merger.merge(meme3, meme4) } returns mergedData

        val results = repository.mergeAll()

        assertThat(results).hasSize(1)
        assertThat(results[0].winnerId).isEqualTo(3L)
    }

    // endregion

    // region observePendingCount Error

    @Test
    fun `observePendingCount propagates error`() = runTest {
        every { dao.getPendingDuplicateCount() } returns flow { throw RuntimeException("DB read failed") }

        repository.observePendingCount().test {
            val error = awaitError()
            assertThat(error.message).isEqualTo("DB read failed")
        }
    }

    // endregion
}
