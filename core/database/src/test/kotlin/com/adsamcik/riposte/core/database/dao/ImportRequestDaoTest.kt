package com.adsamcik.riposte.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportRequestDaoTest {

    private lateinit var database: MemeDatabase
    private lateinit var dao: ImportRequestDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MemeDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        dao = database.importRequestDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // region Test Data Helpers

    private fun createRequest(
        id: String = "req-1",
        status: String = ImportRequestEntity.STATUS_PENDING,
        imageCount: Int = 3,
        completedCount: Int = 0,
        failedCount: Int = 0,
        stagingDir: String = "/staging/req-1",
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
    ) = ImportRequestEntity(
        id = id,
        status = status,
        imageCount = imageCount,
        completedCount = completedCount,
        failedCount = failedCount,
        stagingDir = stagingDir,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun createItem(
        id: String = "item-1",
        requestId: String = "req-1",
        stagedFilePath: String = "/staging/req-1/meme.png",
        originalFileName: String = "meme.png",
        emojis: String = "😂",
        title: String? = "Funny meme",
        description: String? = "A funny meme",
        extractedText: String? = null,
        status: String = ImportRequestEntity.STATUS_PENDING,
        errorMessage: String? = null,
    ) = ImportRequestItemEntity(
        id = id,
        requestId = requestId,
        stagedFilePath = stagedFilePath,
        originalFileName = originalFileName,
        emojis = emojis,
        title = title,
        description = description,
        extractedText = extractedText,
        status = status,
        errorMessage = errorMessage,
    )

    // endregion

    // region Insert and Query Tests

    @Test
    fun `insertRequest and getRequest returns entity`() = runTest {
        val request = createRequest()

        dao.insertRequest(request)
        val result = dao.getRequest("req-1")

        assertThat(result).isEqualTo(request)
    }

    @Test
    fun `getRequest returns null for unknown id`() = runTest {
        val result = dao.getRequest("nonexistent")

        assertThat(result).isNull()
    }

    @Test
    fun `getActiveRequests returns only pending and in_progress`() = runTest {
        dao.insertRequest(createRequest(id = "pending", status = ImportRequestEntity.STATUS_PENDING, createdAt = 1000L))
        dao.insertRequest(createRequest(id = "in-progress", status = ImportRequestEntity.STATUS_IN_PROGRESS, createdAt = 2000L))
        dao.insertRequest(createRequest(id = "completed", status = ImportRequestEntity.STATUS_COMPLETED, createdAt = 3000L))
        dao.insertRequest(createRequest(id = "failed", status = ImportRequestEntity.STATUS_FAILED, createdAt = 4000L))

        dao.getActiveRequests().test {
            val result = awaitItem()
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id }).containsExactly("in-progress", "pending")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getActiveRequests orders by createdAt desc`() = runTest {
        dao.insertRequest(createRequest(id = "oldest", status = ImportRequestEntity.STATUS_PENDING, createdAt = 1000L))
        dao.insertRequest(createRequest(id = "newest", status = ImportRequestEntity.STATUS_PENDING, createdAt = 3000L))
        dao.insertRequest(createRequest(id = "middle", status = ImportRequestEntity.STATUS_IN_PROGRESS, createdAt = 2000L))

        dao.getActiveRequests().test {
            val result = awaitItem()
            assertThat(result).hasSize(3)
            assertThat(result[0].id).isEqualTo("newest")
            assertThat(result[1].id).isEqualTo("middle")
            assertThat(result[2].id).isEqualTo("oldest")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getPendingItems returns only pending items`() = runTest {
        dao.insertRequest(createRequest())
        dao.insertItems(
            listOf(
                createItem(id = "item-pending", status = ImportRequestEntity.STATUS_PENDING),
                createItem(id = "item-completed", status = ImportRequestEntity.STATUS_COMPLETED),
                createItem(id = "item-failed", status = ImportRequestEntity.STATUS_FAILED),
            ),
        )

        val result = dao.getPendingItems("req-1")

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo("item-pending")
    }

    @Test
    fun `getAllItems returns all items for request`() = runTest {
        dao.insertRequest(createRequest(id = "req-1"))
        dao.insertRequest(createRequest(id = "req-2", stagingDir = "/staging/req-2"))
        dao.insertItems(
            listOf(
                createItem(id = "item-1", requestId = "req-1"),
                createItem(id = "item-2", requestId = "req-1"),
                createItem(id = "item-3", requestId = "req-2"),
            ),
        )

        val result = dao.getAllItems("req-1")

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactly("item-1", "item-2")
    }

    @Test
    fun `insertItems inserts multiple items`() = runTest {
        dao.insertRequest(createRequest())
        val items = listOf(
            createItem(id = "item-1"),
            createItem(id = "item-2", originalFileName = "meme2.png"),
            createItem(id = "item-3", originalFileName = "meme3.png"),
        )

        dao.insertItems(items)

        val result = dao.getAllItems("req-1")
        assertThat(result).hasSize(3)
    }

    // endregion

    // region Update Tests

    @Test
    fun `updateItemStatus updates status and error message`() = runTest {
        dao.insertRequest(createRequest())
        dao.insertItems(listOf(createItem(id = "item-1")))

        dao.updateItemStatus("item-1", ImportRequestEntity.STATUS_FAILED, "File corrupt")

        val items = dao.getAllItems("req-1")
        assertThat(items[0].status).isEqualTo(ImportRequestEntity.STATUS_FAILED)
        assertThat(items[0].errorMessage).isEqualTo("File corrupt")
    }

    @Test
    fun `updateItemStatus with null error message`() = runTest {
        dao.insertRequest(createRequest())
        dao.insertItems(listOf(createItem(id = "item-1")))

        dao.updateItemStatus("item-1", ImportRequestEntity.STATUS_COMPLETED)

        val items = dao.getAllItems("req-1")
        assertThat(items[0].status).isEqualTo(ImportRequestEntity.STATUS_COMPLETED)
        assertThat(items[0].errorMessage).isNull()
    }

    @Test
    fun `updateRequestProgress updates all fields`() = runTest {
        dao.insertRequest(createRequest())

        dao.updateRequestProgress(
            id = "req-1",
            status = ImportRequestEntity.STATUS_IN_PROGRESS,
            completed = 2,
            failed = 1,
            updatedAt = 5000L,
        )

        val result = dao.getRequest("req-1")
        assertThat(result).isNotNull()
        assertThat(result!!.status).isEqualTo(ImportRequestEntity.STATUS_IN_PROGRESS)
        assertThat(result.completedCount).isEqualTo(2)
        assertThat(result.failedCount).isEqualTo(1)
        assertThat(result.updatedAt).isEqualTo(5000L)
    }

    @Test
    fun `insertRequest with REPLACE updates existing`() = runTest {
        val original = createRequest(id = "req-1", imageCount = 3)
        dao.insertRequest(original)

        val updated = original.copy(imageCount = 5, status = ImportRequestEntity.STATUS_IN_PROGRESS)
        dao.insertRequest(updated)

        val result = dao.getRequest("req-1")
        assertThat(result).isEqualTo(updated)
    }

    // endregion

    // region Cleanup Tests

    @Test
    fun `cleanupOldRequests deletes completed and failed before cutoff`() = runTest {
        dao.insertRequest(createRequest(id = "old-completed", status = ImportRequestEntity.STATUS_COMPLETED, updatedAt = 1000L))
        dao.insertRequest(createRequest(id = "old-failed", status = ImportRequestEntity.STATUS_FAILED, updatedAt = 1500L))
        dao.insertRequest(createRequest(id = "recent-completed", status = ImportRequestEntity.STATUS_COMPLETED, updatedAt = 5000L))

        dao.cleanupOldRequests(before = 2000L)

        assertThat(dao.getRequest("old-completed")).isNull()
        assertThat(dao.getRequest("old-failed")).isNull()
        assertThat(dao.getRequest("recent-completed")).isNotNull()
    }

    @Test
    fun `cleanupOldRequests preserves active requests`() = runTest {
        dao.insertRequest(createRequest(id = "old-pending", status = ImportRequestEntity.STATUS_PENDING, updatedAt = 500L))
        dao.insertRequest(createRequest(id = "old-in-progress", status = ImportRequestEntity.STATUS_IN_PROGRESS, updatedAt = 500L))

        dao.cleanupOldRequests(before = 2000L)

        assertThat(dao.getRequest("old-pending")).isNotNull()
        assertThat(dao.getRequest("old-in-progress")).isNotNull()
    }

    @Test
    fun `cleanupOldRequestItems deletes items of old requests`() = runTest {
        dao.insertRequest(createRequest(id = "old-req", status = ImportRequestEntity.STATUS_COMPLETED, updatedAt = 1000L))
        dao.insertRequest(createRequest(id = "active-req", status = ImportRequestEntity.STATUS_PENDING, updatedAt = 500L))
        dao.insertItems(
            listOf(
                createItem(id = "old-item", requestId = "old-req"),
                createItem(id = "active-item", requestId = "active-req"),
            ),
        )

        dao.cleanupOldRequestItems(before = 2000L)

        assertThat(dao.getAllItems("old-req")).isEmpty()
        assertThat(dao.getAllItems("active-req")).hasSize(1)
    }

    // endregion
}
