package com.adsamcik.riposte.feature.import_feature.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ImportWorker].
 *
 * Uses TestListenableWorkerBuilder + a custom WorkerFactory to inject
 * mocked dependencies into the HiltWorker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportWorkerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var importRepository: ImportRepository
    private lateinit var importRequestDao: ImportRequestDao
    private lateinit var appLifecycleTracker: AppLifecycleTracker
    private lateinit var notificationManager: ImportNotificationManager
    private val isInBackgroundFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        importRepository = mockk(relaxed = true)
        importRequestDao = mockk(relaxed = true)
        appLifecycleTracker =
            mockk {
                every { isInBackground } returns isInBackgroundFlow
            }
        notificationManager = mockk(relaxed = true)
    }

    private fun createWorker(inputData: Data = workDataOf()): ImportWorker {
        return TestListenableWorkerBuilder<ImportWorker>(context)
            .setInputData(inputData)
            .setWorkerFactory(TestImportWorkerFactory())
            .build() as ImportWorker
    }

    private inner class TestImportWorkerFactory : androidx.work.WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: androidx.work.WorkerParameters,
        ): ListenableWorker {
            return ImportWorker(
                appContext = appContext,
                params = workerParameters,
                importRepository = importRepository,
                importRequestDao = importRequestDao,
                appLifecycleTracker = appLifecycleTracker,
                notificationManager = notificationManager,
            )
        }
    }

    // region Test Data Helpers

    private fun createRequest(
        id: String = "req-1",
        imageCount: Int = 2,
        completedCount: Int = 0,
        failedCount: Int = 0,
        stagingDir: String = tempFolder.root.absolutePath,
    ) = ImportRequestEntity(
        id = id,
        status = ImportRequestEntity.STATUS_PENDING,
        imageCount = imageCount,
        completedCount = completedCount,
        failedCount = failedCount,
        stagingDir = stagingDir,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun createItem(
        id: String = "item-1",
        requestId: String = "req-1",
        fileName: String = "meme1.png",
        emojis: String = "😂,🔥",
        title: String? = "Funny meme",
        description: String? = "A meme",
    ): ImportRequestItemEntity {
        val stagedFile = tempFolder.newFile(fileName)
        stagedFile.writeText("fake-image-data")
        return ImportRequestItemEntity(
            id = id,
            requestId = requestId,
            stagedFilePath = stagedFile.absolutePath,
            originalFileName = fileName,
            emojis = emojis,
            title = title,
            description = description,
            extractedText = null,
        )
    }

    private fun createMeme(id: Long = 1L) =
        Meme(
            id = id,
            filePath = "/memes/meme.png",
            fileName = "meme.png",
            mimeType = "image/png",
            width = 800,
            height = 600,
            fileSizeBytes = 1000L,
            importedAt = System.currentTimeMillis(),
            emojiTags = emptyList(),
        )

    // endregion

    @Test
    fun `doWork returns failure when request id is missing`() =
        runTest {
            val worker = createWorker(workDataOf())

            val result = worker.doWork()

            assertThat(result).isEqualTo(ListenableWorker.Result.failure())
        }

    @Test
    fun `doWork returns failure when request not found in database`() =
        runTest {
            coEvery { importRequestDao.getRequest("nonexistent") } returns null

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "nonexistent"),
                )

            val result = worker.doWork()

            assertThat(result).isEqualTo(ListenableWorker.Result.failure())
        }

    @Test
    fun `doWork processes all pending items successfully`() =
        runTest {
            val request = createRequest()
            val items =
                listOf(
                    createItem(id = "item-1", fileName = "meme1.png"),
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val outputData = (result as ListenableWorker.Result.Success).outputData
            assertThat(outputData.getInt(ImportWorker.KEY_COMPLETED, -1)).isEqualTo(2)
            assertThat(outputData.getInt(ImportWorker.KEY_FAILED, -1)).isEqualTo(0)
            assertThat(outputData.getInt(ImportWorker.KEY_TOTAL, -1)).isEqualTo(2)
        }

    @Test
    fun `doWork handles import failure for individual items`() =
        runTest {
            val request = createRequest()
            val items =
                listOf(
                    createItem(id = "item-1", fileName = "meme1.png"),
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returnsMany
                listOf(
                    Result.success(createMeme()),
                    Result.failure(RuntimeException("Corrupt file")),
                )

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val outputData = (result as ListenableWorker.Result.Success).outputData
            assertThat(outputData.getInt(ImportWorker.KEY_COMPLETED, -1)).isEqualTo(1)
            assertThat(outputData.getInt(ImportWorker.KEY_FAILED, -1)).isEqualTo(1)

            coVerify {
                importRequestDao.updateItemStatus("item-1", ImportRequestEntity.STATUS_COMPLETED)
            }
            coVerify {
                importRequestDao.updateItemStatus("item-2", ImportRequestEntity.STATUS_FAILED, "Corrupt file")
            }
        }

    @Test
    fun `doWork marks request as completed when at least one item succeeds`() =
        runTest {
            val request = createRequest()
            val items =
                listOf(
                    createItem(id = "item-1", fileName = "meme1.png"),
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returnsMany
                listOf(
                    Result.success(createMeme()),
                    Result.failure(RuntimeException("fail")),
                )

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            // Final updateRequestProgress should set STATUS_COMPLETED (not all failed)
            coVerify {
                importRequestDao.updateRequestProgress(
                    id = "req-1",
                    status = ImportRequestEntity.STATUS_COMPLETED,
                    completed = 1,
                    failed = 1,
                    updatedAt = any(),
                )
            }
        }

    @Test
    fun `doWork marks request as failed when all items fail`() =
        runTest {
            val request = createRequest()
            val items =
                listOf(
                    createItem(id = "item-1", fileName = "meme1.png"),
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returns
                Result.failure(RuntimeException("fail"))

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            // Final updateRequestProgress should set STATUS_FAILED (all items failed)
            coVerify {
                importRequestDao.updateRequestProgress(
                    id = "req-1",
                    status = ImportRequestEntity.STATUS_FAILED,
                    completed = 0,
                    failed = 2,
                    updatedAt = any(),
                )
            }
        }

    @Test
    fun `doWork creates notification channel`() =
        runTest {
            val request = createRequest()
            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns emptyList()

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            verify { notificationManager.createChannel() }
        }

    @Test
    fun `doWork cleans up old requests`() =
        runTest {
            val request = createRequest()
            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns emptyList()

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            coVerify { importRequestDao.cleanupOldRequestItems(any()) }
            coVerify { importRequestDao.cleanupOldRequests(any()) }
        }

    @Test
    fun `doWork shows completion notification when in background`() =
        runTest {
            isInBackgroundFlow.value = true
            val request = createRequest()
            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns emptyList()

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            verify { notificationManager.showCompleteNotification(0, 0) }
        }

    @Test
    fun `doWork does not show notification when in foreground`() =
        runTest {
            isInBackgroundFlow.value = false
            val request = createRequest()
            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns emptyList()

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            verify(exactly = 0) { notificationManager.showCompleteNotification(any(), any()) }
        }

    @Test
    fun `doWork resumes from previously completed items`() =
        runTest {
            val request = createRequest(completedCount = 1, failedCount = 0)
            val items =
                listOf(
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            val result = worker.doWork()

            assertThat(result).isInstanceOf(ListenableWorker.Result.Success::class.java)
            val outputData = (result as ListenableWorker.Result.Success).outputData
            assertThat(outputData.getInt(ImportWorker.KEY_COMPLETED, -1)).isEqualTo(2)
        }

    @Test
    fun `doWork cleans up staging directory`() =
        runTest {
            val stagingDir = tempFolder.newFolder("staging-test")
            java.io.File(stagingDir, "temp-file.jpg").writeText("data")

            val request = createRequest(stagingDir = stagingDir.absolutePath)
            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns emptyList()

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            assertThat(stagingDir.exists()).isFalse()
        }

    @Test
    fun `doWork passes correct metadata to importImage`() =
        runTest {
            val request = createRequest(imageCount = 1)
            val item =
                createItem(
                    id = "item-1",
                    emojis = "😂,🔥",
                    title = "Test meme",
                    description = "A test description",
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns listOf(item)
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            coVerify {
                importRepository.importImage(
                    match { it.scheme == "file" },
                    match { metadata ->
                        metadata!!.emojis == listOf("😂", "🔥") &&
                            metadata.title == "Test meme" &&
                            metadata.description == "A test description"
                    },
                )
            }
        }

    @Test
    fun `doWork uses metadataJson for full metadata preservation`() =
        runTest {
            val request = createRequest(imageCount = 1)
            val fullMetadata =
                com.adsamcik.riposte.core.model.MemeMetadata(
                    emojis = listOf("🎉", "🚀"),
                    title = "Launch Day",
                    description = "Rocket launch meme",
                    textContent = "To the moon!",
                    searchPhrases = listOf("rocket launch", "celebration"),
                    basedOn = "SpaceX",
                    primaryLanguage = "en",
                )
            val metadataJson = kotlinx.serialization.json.Json.encodeToString(fullMetadata)
            val stagedFile = tempFolder.newFile("rocket.png")
            stagedFile.writeText("fake-image-data")
            val item =
                ImportRequestItemEntity(
                    id = "item-meta",
                    requestId = "req-1",
                    stagedFilePath = stagedFile.absolutePath,
                    originalFileName = "rocket.png",
                    emojis = "🎉,🚀",
                    title = "Launch Day",
                    description = "Rocket launch meme",
                    extractedText = "To the moon!",
                    metadataJson = metadataJson,
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns listOf(item)
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            coVerify {
                importRepository.importImage(
                    any(),
                    match { metadata ->
                        metadata != null &&
                            metadata.emojis == listOf("🎉", "🚀") &&
                            metadata.searchPhrases == listOf("rocket launch", "celebration") &&
                            metadata.basedOn == "SpaceX" &&
                            metadata.primaryLanguage == "en" &&
                            metadata.textContent == "To the moon!"
                    },
                )
            }
        }

    @Test
    fun `doWork falls back to individual fields when metadataJson is null`() =
        runTest {
            val request = createRequest(imageCount = 1)
            val item =
                createItem(
                    id = "item-legacy",
                    emojis = "😂",
                    title = "Legacy",
                    description = "Old import",
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns listOf(item)
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            coVerify {
                importRepository.importImage(
                    any(),
                    match { metadata ->
                        metadata != null &&
                            metadata.emojis == listOf("😂") &&
                            metadata.title == "Legacy"
                    },
                )
            }
        }

    @Test
    fun `doWork passes null metadata when emojis empty and no metadataJson`() =
        runTest {
            val request = createRequest(imageCount = 1)
            val stagedFile = tempFolder.newFile("noemoji.png")
            stagedFile.writeText("fake-image-data")
            val item =
                ImportRequestItemEntity(
                    id = "item-empty",
                    requestId = "req-1",
                    stagedFilePath = stagedFile.absolutePath,
                    originalFileName = "noemoji.png",
                    emojis = "",
                    title = "No emojis",
                    description = null,
                    extractedText = null,
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns listOf(item)
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            coVerify {
                importRepository.importImage(any(), isNull())
            }
        }

    @Test
    fun `doWork updates progress for each item`() =
        runTest {
            val request = createRequest(imageCount = 2)
            val items =
                listOf(
                    createItem(id = "item-1", fileName = "meme1.png"),
                    createItem(id = "item-2", fileName = "meme2.png"),
                )

            coEvery { importRequestDao.getRequest("req-1") } returns request
            coEvery { importRequestDao.getPendingItems("req-1") } returns items
            coEvery { importRepository.importImage(any(), any()) } returns Result.success(createMeme())

            val worker =
                createWorker(
                    workDataOf(ImportWorker.KEY_REQUEST_ID to "req-1"),
                )

            worker.doWork()

            // Should update progress after each item + initial + final = at least 4 calls
            coVerify(atLeast = 3) {
                importRequestDao.updateRequestProgress(
                    id = "req-1",
                    status = any(),
                    completed = any(),
                    failed = any(),
                    updatedAt = any(),
                )
            }
        }
}
