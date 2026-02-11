package com.adsamcik.riposte.feature.gallery.data.repository

import app.cash.turbine.test
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
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

class GalleryRepositoryImplTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var memeDao: MemeDao
    private lateinit var emojiTagDao: EmojiTagDao
    private lateinit var repository: GalleryRepositoryImpl

    private val testMemeEntities =
        listOf(
            createTestMemeEntity(1, "meme1.jpg"),
            createTestMemeEntity(2, "meme2.jpg"),
            createTestMemeEntity(3, "meme3.jpg", isFavorite = true),
        )

    @Before
    fun setup() {
        memeDao = mockk()
        emojiTagDao = mockk()
        repository =
            GalleryRepositoryImpl(
                memeDao = memeDao,
                emojiTagDao = emojiTagDao,
                ioDispatcher = testDispatcher,
            )
    }

    // region getMemes Tests

    @Test
    fun `getMemes returns flow of memes from dao`() =
        runTest(testDispatcher) {
            every { memeDao.getAllMemes() } returns flowOf(testMemeEntities)

            repository.getMemes().test {
                val memes = awaitItem()
                assertThat(memes).hasSize(3)
                assertThat(memes[0].id).isEqualTo(1)
                assertThat(memes[0].fileName).isEqualTo("meme1.jpg")
                awaitComplete()
            }
        }

    @Test
    fun `getMemes returns empty list when dao returns empty`() =
        runTest(testDispatcher) {
            every { memeDao.getAllMemes() } returns flowOf(emptyList())

            repository.getMemes().test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `getMemes maps entity to domain correctly`() =
        runTest(testDispatcher) {
            val entity =
                createTestMemeEntity(
                    id = 42,
                    fileName = "test.jpg",
                    isFavorite = true,
                    title = "My Title",
                    description = "My Description",
                )
            every { memeDao.getAllMemes() } returns flowOf(listOf(entity))

            repository.getMemes().test {
                val memes = awaitItem()
                val meme = memes[0]
                assertThat(meme.id).isEqualTo(42)
                assertThat(meme.fileName).isEqualTo("test.jpg")
                assertThat(meme.isFavorite).isTrue()
                assertThat(meme.title).isEqualTo("My Title")
                assertThat(meme.description).isEqualTo("My Description")
                awaitComplete()
            }
        }

    // endregion

    // region getFavorites Tests

    @Test
    fun `getFavorites returns only favorite memes`() =
        runTest(testDispatcher) {
            val favorites = testMemeEntities.filter { it.isFavorite }
            every { memeDao.getFavoriteMemes() } returns flowOf(favorites)

            repository.getFavorites().test {
                val memes = awaitItem()
                assertThat(memes).hasSize(1)
                assertThat(memes[0].isFavorite).isTrue()
                awaitComplete()
            }
        }

    @Test
    fun `getFavorites returns empty list when no favorites`() =
        runTest(testDispatcher) {
            every { memeDao.getFavoriteMemes() } returns flowOf(emptyList())

            repository.getFavorites().test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    // endregion

    // region getMemeById Tests

    @Test
    fun `getMemeById returns meme when found`() =
        runTest(testDispatcher) {
            val entity = testMemeEntities[0]
            coEvery { memeDao.getMemeById(1) } returns entity

            val result = repository.getMemeById(1)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(1)
            assertThat(result?.fileName).isEqualTo("meme1.jpg")
        }

    @Test
    fun `getMemeById returns null when not found`() =
        runTest(testDispatcher) {
            coEvery { memeDao.getMemeById(999) } returns null

            val result = repository.getMemeById(999)

            assertThat(result).isNull()
        }

    // endregion

    // region observeMeme Tests

    @Test
    fun `observeMeme emits meme updates`() =
        runTest(testDispatcher) {
            val entity = testMemeEntities[0]
            every { memeDao.observeMemeById(1) } returns flowOf(entity)

            repository.observeMeme(1).test {
                val meme = awaitItem()
                assertThat(meme).isNotNull()
                assertThat(meme?.id).isEqualTo(1)
                awaitComplete()
            }
        }

    @Test
    fun `observeMeme emits null when meme not found`() =
        runTest(testDispatcher) {
            every { memeDao.observeMemeById(999) } returns flowOf(null)

            repository.observeMeme(999).test {
                val meme = awaitItem()
                assertThat(meme).isNull()
                awaitComplete()
            }
        }

    // endregion

    // region updateMeme Tests

    @Test
    fun `updateMeme returns success when update succeeds`() =
        runTest(testDispatcher) {
            coEvery { memeDao.updateMeme(any()) } just Runs

            val result =
                repository.updateMeme(
                    com.adsamcik.riposte.core.model.Meme(
                        id = 1,
                        filePath = "/storage/memes/meme1.jpg",
                        fileName = "meme1.jpg",
                        mimeType = "image/jpeg",
                        width = 1080,
                        height = 1080,
                        fileSizeBytes = 1024L,
                        importedAt = System.currentTimeMillis(),
                        emojiTags = emptyList(),
                        isFavorite = false,
                    ),
                )

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.updateMeme(any()) }
        }

    @Test
    fun `updateMeme returns failure when exception thrown`() =
        runTest(testDispatcher) {
            coEvery { memeDao.updateMeme(any()) } throws Exception("Update failed")

            val result =
                repository.updateMeme(
                    com.adsamcik.riposte.core.model.Meme(
                        id = 1,
                        filePath = "/storage/memes/meme1.jpg",
                        fileName = "meme1.jpg",
                        mimeType = "image/jpeg",
                        width = 1080,
                        height = 1080,
                        fileSizeBytes = 1024L,
                        importedAt = System.currentTimeMillis(),
                        emojiTags = emptyList(),
                        isFavorite = false,
                    ),
                )

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Update failed")
        }

    // endregion

    // region updateMemeWithEmojis Tests

    @Test
    fun `updateMemeWithEmojis updates meme and emoji tags`() =
        runTest(testDispatcher) {
            val emojiTags =
                listOf(
                    com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("😂"),
                    com.adsamcik.riposte.core.model.EmojiTag.fromEmoji("🔥"),
                )
            coEvery { memeDao.updateMeme(any()) } just Runs
            coEvery { emojiTagDao.deleteEmojiTagsForMeme(1) } just Runs
            coEvery { emojiTagDao.insertEmojiTags(any()) } just Runs

            val result =
                repository.updateMemeWithEmojis(
                    com.adsamcik.riposte.core.model.Meme(
                        id = 1,
                        filePath = "/storage/memes/meme1.jpg",
                        fileName = "meme1.jpg",
                        mimeType = "image/jpeg",
                        width = 1080,
                        height = 1080,
                        fileSizeBytes = 1024L,
                        importedAt = System.currentTimeMillis(),
                        emojiTags = emojiTags,
                        isFavorite = false,
                    ),
                )

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.updateMeme(any()) }
            coVerify { emojiTagDao.deleteEmojiTagsForMeme(1) }
            coVerify { emojiTagDao.insertEmojiTags(any()) }
        }

    @Test
    fun `updateMemeWithEmojis clears emoji tags when empty`() =
        runTest(testDispatcher) {
            coEvery { memeDao.updateMeme(any()) } just Runs
            coEvery { emojiTagDao.deleteEmojiTagsForMeme(1) } just Runs

            val result =
                repository.updateMemeWithEmojis(
                    com.adsamcik.riposte.core.model.Meme(
                        id = 1,
                        filePath = "/storage/memes/meme1.jpg",
                        fileName = "meme1.jpg",
                        mimeType = "image/jpeg",
                        width = 1080,
                        height = 1080,
                        fileSizeBytes = 1024L,
                        importedAt = System.currentTimeMillis(),
                        emojiTags = emptyList(),
                        isFavorite = false,
                    ),
                )

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.updateMeme(any()) }
            coVerify { emojiTagDao.deleteEmojiTagsForMeme(1) }
            coVerify(exactly = 0) { emojiTagDao.insertEmojiTags(any()) }
        }

    @Test
    fun `updateMemeWithEmojis returns failure on error`() =
        runTest(testDispatcher) {
            coEvery { memeDao.updateMeme(any()) } throws Exception("Database error")

            val result =
                repository.updateMemeWithEmojis(
                    com.adsamcik.riposte.core.model.Meme(
                        id = 1,
                        filePath = "/storage/memes/meme1.jpg",
                        fileName = "meme1.jpg",
                        mimeType = "image/jpeg",
                        width = 1080,
                        height = 1080,
                        fileSizeBytes = 1024L,
                        importedAt = System.currentTimeMillis(),
                        emojiTags = emptyList(),
                        isFavorite = false,
                    ),
                )

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Database error")
        }

    // endregion

    // region deleteMeme Tests

    @Test
    fun `deleteMeme deletes meme from database successfully`() =
        runTest(testDispatcher) {
            val entity = testMemeEntities[0]
            coEvery { memeDao.getMemeById(1) } returns entity
            coEvery { memeDao.deleteMemeById(1) } just Runs

            val result = repository.deleteMeme(1)

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.deleteMemeById(1) }
        }

    @Test
    fun `deleteMeme succeeds when meme not found`() =
        runTest(testDispatcher) {
            coEvery { memeDao.getMemeById(999) } returns null

            val result = repository.deleteMeme(999)

            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `deleteMeme returns failure when exception thrown`() =
        runTest(testDispatcher) {
            coEvery { memeDao.getMemeById(1) } throws Exception("Database error")

            val result = repository.deleteMeme(1)

            assertThat(result.isFailure).isTrue()
        }

    // endregion

    // region deleteMemes Tests

    @Test
    fun `deleteMemes deletes multiple memes successfully`() =
        runTest(testDispatcher) {
            testMemeEntities.forEach { entity ->
                coEvery { memeDao.getMemeById(entity.id) } returns entity
            }
            coEvery { memeDao.deleteMemesByIds(any()) } just Runs

            val result = repository.deleteMemes(setOf(1L, 2L, 3L))

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.deleteMemesByIds(listOf(1L, 2L, 3L)) }
        }

    @Test
    fun `deleteMemes handles empty set`() =
        runTest(testDispatcher) {
            coEvery { memeDao.deleteMemesByIds(emptyList()) } just Runs

            val result = repository.deleteMemes(emptySet())

            assertThat(result.isSuccess).isTrue()
        }

    @Test
    fun `deleteMemes returns failure when exception thrown`() =
        runTest(testDispatcher) {
            coEvery { memeDao.getMemeById(any()) } throws Exception("Database error")

            val result = repository.deleteMemes(setOf(1L))

            assertThat(result.isFailure).isTrue()
        }

    // endregion

    // region toggleFavorite Tests

    @Test
    fun `toggleFavorite succeeds`() =
        runTest(testDispatcher) {
            coEvery { memeDao.toggleFavorite(1) } just Runs

            val result = repository.toggleFavorite(1)

            assertThat(result.isSuccess).isTrue()
            coVerify { memeDao.toggleFavorite(1) }
        }

    @Test
    fun `toggleFavorite returns failure when exception thrown`() =
        runTest(testDispatcher) {
            coEvery { memeDao.toggleFavorite(1) } throws Exception("Toggle failed")

            val result = repository.toggleFavorite(1)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Toggle failed")
        }

    // endregion

    // region getMemesByEmoji Tests

    @Test
    fun `getMemesByEmoji returns memes matching emoji`() =
        runTest(testDispatcher) {
            every { memeDao.getMemesByEmoji("😂") } returns flowOf(testMemeEntities)

            repository.getMemesByEmoji("😂").test {
                val memes = awaitItem()
                assertThat(memes).hasSize(3)
                awaitComplete()
            }
        }

    @Test
    fun `getMemesByEmoji returns empty list for unknown emoji`() =
        runTest(testDispatcher) {
            every { memeDao.getMemesByEmoji("🦄") } returns flowOf(emptyList())

            repository.getMemesByEmoji("🦄").test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    // endregion

    // region recordMemeView Tests

    @Test
    fun `recordMemeView calls dao recordView`() =
        runTest(testDispatcher) {
            coEvery { memeDao.recordView(any(), any()) } just Runs

            repository.recordMemeView(1L)

            coVerify { memeDao.recordView(1L, any()) }
        }

    @Test
    fun `recordMemeView handles non-existent meme gracefully`() =
        runTest(testDispatcher) {
            coEvery { memeDao.recordView(any(), any()) } just Runs

            repository.recordMemeView(999L)

            coVerify { memeDao.recordView(999L, any()) }
        }

    // endregion

    // region Helper Functions

    private fun createTestMemeEntity(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false,
        title: String? = null,
        description: String? = null,
    ): MemeEntity =
        MemeEntity(
            id = id,
            filePath = "/storage/memes/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            title = title,
            description = description,
            textContent = null,
            isFavorite = isFavorite,
            emojiTagsJson = "[\"😂\"]",
        )

    // endregion
}
