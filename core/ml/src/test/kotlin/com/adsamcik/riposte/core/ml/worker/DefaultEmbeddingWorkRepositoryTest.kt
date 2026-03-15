package com.adsamcik.riposte.core.ml.worker

import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.ml.EmbeddingModelVersionManager
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DefaultEmbeddingWorkRepositoryTest {

    private lateinit var repository: DefaultEmbeddingWorkRepository
    private lateinit var memeDao: MemeDao
    private lateinit var memeEmbeddingDao: MemeEmbeddingDao

    @Before
    fun setup() {
        memeDao = mockk(relaxed = true)
        memeEmbeddingDao = mockk(relaxed = true)
        repository = DefaultEmbeddingWorkRepository(memeDao, memeEmbeddingDao)
    }

    @Test
    fun `countMemesNeedingEmbeddings delegates to SQL count query`() = runTest {
        coEvery {
            memeEmbeddingDao.countAllMemesNeedingEmbeddings(
                expectedTypeCount = 5,
                currentVersion = EmbeddingModelVersionManager.CURRENT_VERSION,
            )
        } returns 42

        val count = repository.countMemesNeedingEmbeddings()

        assertThat(count).isEqualTo(42)
        coVerify(exactly = 1) {
            memeEmbeddingDao.countAllMemesNeedingEmbeddings(
                expectedTypeCount = 5,
                currentVersion = EmbeddingModelVersionManager.CURRENT_VERSION,
            )
        }
        // Verify the old memory-heavy approach is NOT used
        coVerify(exactly = 0) { memeEmbeddingDao.getMemeIdsWithoutEmbeddings(any()) }
        coVerify(exactly = 0) { memeEmbeddingDao.getMemeIdsNeedingRegeneration(any()) }
    }

    @Test
    fun `markMemeFullyAttempted delegates to incrementIndexingAttempts`() = runTest {
        repository.markMemeFullyAttempted(123L)

        coVerify(exactly = 1) { memeEmbeddingDao.incrementIndexingAttempts(123L, any()) }
    }

    @Test
    fun `countMemesNeedingEmbeddings returns zero when no work needed`() = runTest {
        coEvery {
            memeEmbeddingDao.countAllMemesNeedingEmbeddings(any(), any())
        } returns 0

        assertThat(repository.countMemesNeedingEmbeddings()).isEqualTo(0)
    }
}
