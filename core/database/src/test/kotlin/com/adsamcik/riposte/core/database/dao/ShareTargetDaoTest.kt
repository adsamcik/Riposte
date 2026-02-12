package com.adsamcik.riposte.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.entity.ShareTargetEntity
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
class ShareTargetDaoTest {
    private lateinit var database: MemeDatabase
    private lateinit var shareTargetDao: ShareTargetDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(
                context,
                MemeDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()
        shareTargetDao = database.shareTargetDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // region Test Data Helpers

    private fun createShareTarget(
        packageName: String = "com.example.app",
        activityName: String = "com.example.app.ShareActivity",
        displayLabel: String = "Example App",
        shareCount: Int = 0,
        lastSharedAt: Long? = null,
    ) = ShareTargetEntity(
        packageName = packageName,
        activityName = activityName,
        displayLabel = displayLabel,
        shareCount = shareCount,
        lastSharedAt = lastSharedAt,
    )

    // endregion

    // region Insert and Query Tests

    @Test
    fun `upsertShareTarget inserts new share target`() =
        runTest {
            val target = createShareTarget()

            shareTargetDao.upsertShareTarget(target)

            val result = shareTargetDao.getTopShareTargets(10)
            assertThat(result).hasSize(1)
            assertThat(result[0].packageName).isEqualTo("com.example.app")
            assertThat(result[0].displayLabel).isEqualTo("Example App")
        }

    @Test
    fun `upsertShareTarget replaces existing share target`() =
        runTest {
            val original = createShareTarget(shareCount = 1)
            shareTargetDao.upsertShareTarget(original)

            val updated = original.copy(shareCount = 5, displayLabel = "Updated App")
            shareTargetDao.upsertShareTarget(updated)

            val result = shareTargetDao.getTopShareTargets(10)
            assertThat(result).hasSize(1)
            assertThat(result[0].shareCount).isEqualTo(5)
            assertThat(result[0].displayLabel).isEqualTo("Updated App")
        }

    @Test
    fun `getTopShareTargets returns targets ordered by shareCount descending`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget("com.low", shareCount = 1))
            shareTargetDao.upsertShareTarget(createShareTarget("com.high", shareCount = 10))
            shareTargetDao.upsertShareTarget(createShareTarget("com.mid", shareCount = 5))

            val result = shareTargetDao.getTopShareTargets(10)

            assertThat(result).hasSize(3)
            assertThat(result[0].packageName).isEqualTo("com.high")
            assertThat(result[1].packageName).isEqualTo("com.mid")
            assertThat(result[2].packageName).isEqualTo("com.low")
        }

    @Test
    fun `getTopShareTargets respects limit`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget("com.a", shareCount = 3))
            shareTargetDao.upsertShareTarget(createShareTarget("com.b", shareCount = 2))
            shareTargetDao.upsertShareTarget(createShareTarget("com.c", shareCount = 1))

            val result = shareTargetDao.getTopShareTargets(2)

            assertThat(result).hasSize(2)
            assertThat(result[0].packageName).isEqualTo("com.a")
            assertThat(result[1].packageName).isEqualTo("com.b")
        }

    @Test
    fun `getTopShareTargets returns empty list when no targets exist`() =
        runTest {
            val result = shareTargetDao.getTopShareTargets(10)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getTopShareTargets breaks ties by lastSharedAt descending`() =
        runTest {
            val now = System.currentTimeMillis()
            shareTargetDao.upsertShareTarget(
                createShareTarget("com.older", shareCount = 5, lastSharedAt = now - 1000),
            )
            shareTargetDao.upsertShareTarget(
                createShareTarget("com.newer", shareCount = 5, lastSharedAt = now),
            )

            val result = shareTargetDao.getTopShareTargets(10)

            assertThat(result[0].packageName).isEqualTo("com.newer")
            assertThat(result[1].packageName).isEqualTo("com.older")
        }

    // endregion

    // region recordShare Tests

    @Test
    fun `recordShare increments share count for existing target`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget(shareCount = 3))

            val updated = shareTargetDao.recordShare("com.example.app")

            assertThat(updated).isEqualTo(1)
            val result = shareTargetDao.getTopShareTargets(10)
            assertThat(result[0].shareCount).isEqualTo(4)
        }

    @Test
    fun `recordShare updates lastSharedAt timestamp`() =
        runTest {
            val timestamp = 1700000000000L
            shareTargetDao.upsertShareTarget(createShareTarget(lastSharedAt = null))

            shareTargetDao.recordShare("com.example.app", timestamp)

            val result = shareTargetDao.getTopShareTargets(10)
            assertThat(result[0].lastSharedAt).isEqualTo(timestamp)
        }

    @Test
    fun `recordShare returns 0 for non-existent target`() =
        runTest {
            val updated = shareTargetDao.recordShare("com.nonexistent")

            assertThat(updated).isEqualTo(0)
        }

    // endregion

    // region clearAll Tests

    @Test
    fun `clearAll removes all share targets`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget("com.a"))
            shareTargetDao.upsertShareTarget(createShareTarget("com.b"))

            shareTargetDao.clearAll()

            val result = shareTargetDao.getTopShareTargets(10)
            assertThat(result).isEmpty()
        }

    // endregion

    // region Flow Emission Tests

    @Test
    fun `getShareTargets emits initial value and updates on insert`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget())

            shareTargetDao.getShareTargets().test {
                val initial = awaitItem()
                assertThat(initial).hasSize(1)
                assertThat(initial[0].packageName).isEqualTo("com.example.app")

                shareTargetDao.upsertShareTarget(createShareTarget("com.second.app"))
                assertThat(awaitItem()).hasSize(2)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getShareTargets emits when share target is updated`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget(shareCount = 1))

            shareTargetDao.getShareTargets().test {
                val initial = awaitItem()
                assertThat(initial).hasSize(1)
                assertThat(initial[0].shareCount).isEqualTo(1)

                shareTargetDao.recordShare("com.example.app")
                val updated = awaitItem()
                assertThat(updated[0].shareCount).isEqualTo(2)

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getShareTargets emits ordered by shareCount descending`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget("com.low", shareCount = 1))
            shareTargetDao.upsertShareTarget(createShareTarget("com.high", shareCount = 10))

            shareTargetDao.getShareTargets().test {
                val result = awaitItem()
                assertThat(result).hasSize(2)
                assertThat(result[0].packageName).isEqualTo("com.high")
                assertThat(result[1].packageName).isEqualTo("com.low")

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getShareTargets emits empty list after clearAll`() =
        runTest {
            shareTargetDao.upsertShareTarget(createShareTarget())

            shareTargetDao.getShareTargets().test {
                assertThat(awaitItem()).hasSize(1)

                shareTargetDao.clearAll()
                assertThat(awaitItem()).isEmpty()

                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion
}
