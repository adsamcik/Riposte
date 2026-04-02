package com.adsamcik.riposte.feature.gallery.domain.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RecoverStaleImportsUseCaseTest {
    private lateinit var context: Context
    private lateinit var importRequestDao: ImportRequestDao
    private lateinit var useCase: RecoverStaleImportsUseCase

    private val staleThresholdMs = 300_000L // 5 minutes

    private fun createImportRequest(
        id: String = "import-1",
        status: String = ImportRequestEntity.STATUS_IN_PROGRESS,
        imageCount: Int = 10,
        completedCount: Int = 5,
        failedCount: Int = 1,
    ) = ImportRequestEntity(
        id = id,
        status = status,
        imageCount = imageCount,
        completedCount = completedCount,
        failedCount = failedCount,
        stagingDir = "/staging/$id",
        createdAt = System.currentTimeMillis() - 600_000L,
        updatedAt = System.currentTimeMillis() - 600_000L,
    )

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        importRequestDao = mockk(relaxed = true)

        // Initialize WorkManager for testing with Robolectric
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)

        useCase = RecoverStaleImportsUseCase(context, importRequestDao)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // region Happy Path — no active work, so stale imports get recovered

    @Test
    fun `recovers stale import and marks as completed when has completed items`() = runTest {
        val request = createImportRequest(completedCount = 5, failedCount = 1, imageCount = 10)
        coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(request)

        val result = useCase(staleThresholdMs)

        assertThat(result).hasSize(1)
        assertThat(result[0].completedCount).isEqualTo(5)
        assertThat(result[0].imageCount).isEqualTo(10)

        coVerify {
            importRequestDao.updateRequestProgress(
                id = request.id,
                status = ImportRequestEntity.STATUS_COMPLETED,
                completed = 5,
                failed = 1,
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `recovers stale import and marks as failed when no completed items`() = runTest {
        val request = createImportRequest(completedCount = 0, failedCount = 3, imageCount = 10)
        coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(request)

        val result = useCase(staleThresholdMs)

        assertThat(result).hasSize(1)

        coVerify {
            importRequestDao.updateRequestProgress(
                id = request.id,
                status = ImportRequestEntity.STATUS_FAILED,
                completed = 0,
                failed = 3,
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `recovers multiple stale imports`() = runTest {
        val request1 = createImportRequest(id = "import-1", completedCount = 3, imageCount = 5)
        val request2 = createImportRequest(id = "import-2", completedCount = 0, imageCount = 8)
        coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(request1, request2)

        val result = useCase(staleThresholdMs)

        assertThat(result).hasSize(2)
        assertThat(result[0].completedCount).isEqualTo(3)
        assertThat(result[0].imageCount).isEqualTo(5)
        assertThat(result[1].completedCount).isEqualTo(0)
        assertThat(result[1].imageCount).isEqualTo(8)
    }

    // endregion

    // region Empty / No-Op Cases

    @Test
    fun `returns empty list when no stale requests exist`() = runTest {
        coEvery { importRequestDao.getStaleRequests(any()) } returns emptyList()

        val result = useCase(staleThresholdMs)

        assertThat(result).isEmpty()
    }

    // endregion

    // region Error Propagation

    @Test
    fun `propagates exception when DAO throws`() = runTest {
        coEvery { importRequestDao.getStaleRequests(any()) } throws RuntimeException("DB error")

        val exception = runCatching { useCase(staleThresholdMs) }.exceptionOrNull()

        assertThat(exception).isInstanceOf(RuntimeException::class.java)
        assertThat(exception!!.message).isEqualTo("DB error")
    }

    // endregion

    // region Edge Cases

    @Test
    fun `marks as completed when all items completed`() = runTest {
        val request = createImportRequest(completedCount = 10, failedCount = 0, imageCount = 10)
        coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(request)

        val result = useCase(staleThresholdMs)

        assertThat(result).hasSize(1)
        coVerify {
            importRequestDao.updateRequestProgress(
                id = request.id,
                status = ImportRequestEntity.STATUS_COMPLETED,
                completed = 10,
                failed = 0,
                updatedAt = any(),
            )
        }
    }

    @Test
    fun `marks as failed when zero completed items`() = runTest {
        val request = createImportRequest(completedCount = 0, failedCount = 0, imageCount = 5)
        coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(request)

        val result = useCase(staleThresholdMs)

        assertThat(result).hasSize(1)
        assertThat(result[0].completedCount).isEqualTo(0)
        assertThat(result[0].imageCount).isEqualTo(5)

        coVerify {
            importRequestDao.updateRequestProgress(
                id = request.id,
                status = ImportRequestEntity.STATUS_FAILED,
                completed = 0,
                failed = 0,
                updatedAt = any(),
            )
        }
    }

    // endregion
}
