package com.adsamcik.riposte.core.database.repository

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.dao.ShareTargetDao
import com.adsamcik.riposte.core.database.entity.ShareTargetEntity
import com.adsamcik.riposte.core.model.ShareTarget
import com.google.common.truth.Truth.assertThat
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ShareTargetRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var shareTargetDao: ShareTargetDao
    private lateinit var repository: ShareTargetRepositoryImpl

    @Before
    fun setup() {
        shareTargetDao = mockk()
        repository =
            ShareTargetRepositoryImpl(
                shareTargetDao = shareTargetDao,
                ioDispatcher = testDispatcher,
            )
    }

    // region Test Data Helpers

    private fun createEntity(
        packageName: String = "com.example.app",
        activityName: String = "com.example.app.ShareActivity",
        displayLabel: String = "Example App",
        shareCount: Int = 5,
        lastSharedAt: Long? = 1700000000000L,
    ) = ShareTargetEntity(
        packageName = packageName,
        activityName = activityName,
        displayLabel = displayLabel,
        shareCount = shareCount,
        lastSharedAt = lastSharedAt,
    )

    private fun createDomain(
        packageName: String = "com.example.app",
        activityName: String = "com.example.app.ShareActivity",
        displayLabel: String = "Example App",
        shareCount: Int = 5,
        lastSharedAt: Long? = 1700000000000L,
    ) = ShareTarget(
        packageName = packageName,
        activityName = activityName,
        displayLabel = displayLabel,
        shareCount = shareCount,
        lastSharedAt = lastSharedAt,
    )

    // endregion

    // region getShareTargets Tests

    @Test
    fun `getShareTargets returns flow of domain models from dao`() =
        runTest(testDispatcher) {
            val entities =
                listOf(
                    createEntity("com.a", shareCount = 10),
                    createEntity("com.b", shareCount = 5),
                )
            every { shareTargetDao.getShareTargets() } returns flowOf(entities)

            repository.getShareTargets().test {
                val targets = awaitItem()
                assertThat(targets).hasSize(2)
                assertThat(targets[0].packageName).isEqualTo("com.a")
                assertThat(targets[0].shareCount).isEqualTo(10)
                assertThat(targets[1].packageName).isEqualTo("com.b")
                awaitComplete()
            }
        }

    @Test
    fun `getShareTargets returns empty list when dao returns empty`() =
        runTest(testDispatcher) {
            every { shareTargetDao.getShareTargets() } returns flowOf(emptyList())

            repository.getShareTargets().test {
                val targets = awaitItem()
                assertThat(targets).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `getShareTargets maps entity fields to domain correctly`() =
        runTest(testDispatcher) {
            val entity =
                createEntity(
                    packageName = "com.test",
                    activityName = "com.test.Activity",
                    displayLabel = "Test App",
                    shareCount = 42,
                    lastSharedAt = 1700000000000L,
                )
            every { shareTargetDao.getShareTargets() } returns flowOf(listOf(entity))

            repository.getShareTargets().test {
                val target = awaitItem()[0]
                assertThat(target.packageName).isEqualTo("com.test")
                assertThat(target.activityName).isEqualTo("com.test.Activity")
                assertThat(target.displayLabel).isEqualTo("Test App")
                assertThat(target.shareCount).isEqualTo(42)
                assertThat(target.lastSharedAt).isEqualTo(1700000000000L)
                awaitComplete()
            }
        }

    // endregion

    // region getTopShareTargets Tests

    @Test
    fun `getTopShareTargets delegates to dao with correct limit`() =
        runTest(testDispatcher) {
            val entities = listOf(createEntity("com.a"), createEntity("com.b"))
            coEvery { shareTargetDao.getTopShareTargets(3) } returns entities

            val result = repository.getTopShareTargets(3)

            assertThat(result).hasSize(2)
            coVerify { shareTargetDao.getTopShareTargets(3) }
        }

    @Test
    fun `getTopShareTargets returns empty list when dao returns empty`() =
        runTest(testDispatcher) {
            coEvery { shareTargetDao.getTopShareTargets(any()) } returns emptyList()

            val result = repository.getTopShareTargets(5)

            assertThat(result).isEmpty()
        }

    @Test
    fun `getTopShareTargets maps entities to domain models`() =
        runTest(testDispatcher) {
            coEvery { shareTargetDao.getTopShareTargets(1) } returns
                listOf(createEntity(shareCount = 99))

            val result = repository.getTopShareTargets(1)

            assertThat(result[0].shareCount).isEqualTo(99)
            assertThat(result[0].packageName).isEqualTo("com.example.app")
        }

    // endregion

    // region recordShare Tests

    @Test
    fun `recordShare updates existing target when recordShare returns 1`() =
        runTest(testDispatcher) {
            val target = createDomain()
            coEvery { shareTargetDao.recordShare(target.packageName, any()) } returns 1

            repository.recordShare(target)

            coVerify { shareTargetDao.recordShare(target.packageName, any()) }
            coVerify(exactly = 0) { shareTargetDao.upsertShareTarget(any()) }
        }

    @Test
    fun `recordShare inserts new target when recordShare returns 0`() =
        runTest(testDispatcher) {
            val target = createDomain(packageName = "com.new.app")
            coEvery { shareTargetDao.recordShare("com.new.app", any()) } returns 0
            coEvery { shareTargetDao.upsertShareTarget(any()) } just Runs

            repository.recordShare(target)

            coVerify { shareTargetDao.recordShare("com.new.app", any()) }
            coVerify {
                shareTargetDao.upsertShareTarget(
                    match {
                        it.packageName == "com.new.app" &&
                            it.shareCount == 1 &&
                            it.lastSharedAt != null
                    },
                )
            }
        }

    @Test
    fun `recordShare inserts entity with correct fields for new target`() =
        runTest(testDispatcher) {
            val target =
                createDomain(
                    packageName = "com.chat",
                    activityName = "com.chat.ShareActivity",
                    displayLabel = "Chat App",
                )
            coEvery { shareTargetDao.recordShare("com.chat", any()) } returns 0
            coEvery { shareTargetDao.upsertShareTarget(any()) } just Runs

            repository.recordShare(target)

            coVerify {
                shareTargetDao.upsertShareTarget(
                    match {
                        it.packageName == "com.chat" &&
                            it.activityName == "com.chat.ShareActivity" &&
                            it.displayLabel == "Chat App" &&
                            it.shareCount == 1
                    },
                )
            }
        }

    // endregion
}
