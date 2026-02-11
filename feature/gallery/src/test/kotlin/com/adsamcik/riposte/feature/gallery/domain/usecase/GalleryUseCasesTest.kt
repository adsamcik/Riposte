package com.adsamcik.riposte.feature.gallery.domain.usecase

import app.cash.turbine.test
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class GalleryUseCasesTest {
    private lateinit var repository: GalleryRepository

    private val testMemes =
        listOf(
            createTestMeme(1, "meme1.jpg"),
            createTestMeme(2, "meme2.jpg"),
            createTestMeme(3, "meme3.jpg", isFavorite = true),
        )

    @Before
    fun setup() {
        repository = mockk()
    }

    // region GetMemesUseCase Tests

    @Test
    fun `GetMemesUseCase returns flow of memes from repository`() =
        runTest {
            every { repository.getMemes() } returns flowOf(testMemes)
            val useCase = GetMemesUseCase(repository)

            useCase().test {
                val memes = awaitItem()
                assertThat(memes).hasSize(3)
                assertThat(memes[0].id).isEqualTo(1)
                assertThat(memes[1].id).isEqualTo(2)
                assertThat(memes[2].id).isEqualTo(3)
                awaitComplete()
            }

            verify { repository.getMemes() }
        }

    @Test
    fun `GetMemesUseCase returns empty list when no memes`() =
        runTest {
            every { repository.getMemes() } returns flowOf(emptyList())
            val useCase = GetMemesUseCase(repository)

            useCase().test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `GetMemesUseCase emits updated list when repository updates`() =
        runTest {
            val mutableFlow = kotlinx.coroutines.flow.MutableStateFlow(testMemes)
            every { repository.getMemes() } returns mutableFlow
            val useCase = GetMemesUseCase(repository)

            useCase().test {
                assertThat(awaitItem()).hasSize(3)

                mutableFlow.value = testMemes + createTestMeme(4, "new.jpg")
                assertThat(awaitItem()).hasSize(4)
            }
        }

    // endregion

    // region GetFavoritesUseCase Tests

    @Test
    fun `GetFavoritesUseCase returns only favorite memes`() =
        runTest {
            val favorites = testMemes.filter { it.isFavorite }
            every { repository.getFavorites() } returns flowOf(favorites)
            val useCase = GetFavoritesUseCase(repository)

            useCase().test {
                val memes = awaitItem()
                assertThat(memes).hasSize(1)
                assertThat(memes[0].isFavorite).isTrue()
                awaitComplete()
            }

            verify { repository.getFavorites() }
        }

    @Test
    fun `GetFavoritesUseCase returns empty list when no favorites`() =
        runTest {
            every { repository.getFavorites() } returns flowOf(emptyList())
            val useCase = GetFavoritesUseCase(repository)

            useCase().test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    // endregion

    // region GetMemeByIdUseCase Tests

    @Test
    fun `GetMemeByIdUseCase returns meme when found`() =
        runTest {
            val expectedMeme = testMemes[0]
            coEvery { repository.getMemeById(1) } returns expectedMeme
            val useCase = GetMemeByIdUseCase(repository)

            val result = useCase(1)

            assertThat(result).isNotNull()
            assertThat(result?.id).isEqualTo(1)
            coVerify { repository.getMemeById(1) }
        }

    @Test
    fun `GetMemeByIdUseCase returns null when not found`() =
        runTest {
            coEvery { repository.getMemeById(999) } returns null
            val useCase = GetMemeByIdUseCase(repository)

            val result = useCase(999)

            assertThat(result).isNull()
        }

    // endregion

    // region GetMemesByEmojiUseCase Tests

    @Test
    fun `GetMemesByEmojiUseCase returns memes with matching emoji`() =
        runTest {
            val emojiMemes = listOf(createTestMeme(1, "laugh.jpg"))
            every { repository.getMemesByEmoji("😂") } returns flowOf(emojiMemes)
            val useCase = GetMemesByEmojiUseCase(repository)

            useCase("😂").test {
                val memes = awaitItem()
                assertThat(memes).hasSize(1)
                awaitComplete()
            }

            verify { repository.getMemesByEmoji("😂") }
        }

    @Test
    fun `GetMemesByEmojiUseCase returns empty list for unknown emoji`() =
        runTest {
            every { repository.getMemesByEmoji("🦄") } returns flowOf(emptyList())
            val useCase = GetMemesByEmojiUseCase(repository)

            useCase("🦄").test {
                val memes = awaitItem()
                assertThat(memes).isEmpty()
                awaitComplete()
            }
        }

    @Test
    fun `GetMemesByEmojiUseCase handles multiple memes with same emoji`() =
        runTest {
            val emojiMemes =
                listOf(
                    createTestMeme(1, "laugh1.jpg"),
                    createTestMeme(2, "laugh2.jpg"),
                    createTestMeme(3, "laugh3.jpg"),
                )
            every { repository.getMemesByEmoji("😂") } returns flowOf(emojiMemes)
            val useCase = GetMemesByEmojiUseCase(repository)

            useCase("😂").test {
                val memes = awaitItem()
                assertThat(memes).hasSize(3)
                awaitComplete()
            }
        }

    // endregion

    // region DeleteMemesUseCase Tests

    @Test
    fun `DeleteMemesUseCase deletes single meme successfully`() =
        runTest {
            coEvery { repository.deleteMeme(1) } returns Result.success(Unit)
            val useCase = DeleteMemesUseCase(repository)

            val result = useCase(1)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.deleteMeme(1) }
        }

    @Test
    fun `DeleteMemesUseCase single delete returns failure on error`() =
        runTest {
            coEvery { repository.deleteMeme(1) } returns Result.failure(Exception("Delete failed"))
            val useCase = DeleteMemesUseCase(repository)

            val result = useCase(1)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Delete failed")
        }

    @Test
    fun `DeleteMemesUseCase deletes multiple memes successfully`() =
        runTest {
            val idsToDelete = setOf(1L, 2L, 3L)
            coEvery { repository.deleteMemes(idsToDelete) } returns Result.success(Unit)
            val useCase = DeleteMemesUseCase(repository)

            val result = useCase(idsToDelete)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.deleteMemes(idsToDelete) }
        }

    @Test
    fun `DeleteMemesUseCase batch delete returns failure on error`() =
        runTest {
            val idsToDelete = setOf(1L, 2L)
            coEvery { repository.deleteMemes(idsToDelete) } returns Result.failure(Exception("Batch delete failed"))
            val useCase = DeleteMemesUseCase(repository)

            val result = useCase(idsToDelete)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Batch delete failed")
        }

    @Test
    fun `DeleteMemesUseCase handles empty set`() =
        runTest {
            val emptySet = emptySet<Long>()
            coEvery { repository.deleteMemes(emptySet) } returns Result.success(Unit)
            val useCase = DeleteMemesUseCase(repository)

            val result = useCase(emptySet)

            assertThat(result.isSuccess).isTrue()
        }

    // endregion

    // region ToggleFavoriteUseCase Tests

    @Test
    fun `ToggleFavoriteUseCase toggles favorite successfully`() =
        runTest {
            coEvery { repository.toggleFavorite(1) } returns Result.success(Unit)
            val useCase = ToggleFavoriteUseCase(repository)

            val result = useCase(1)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.toggleFavorite(1) }
        }

    @Test
    fun `ToggleFavoriteUseCase returns failure on error`() =
        runTest {
            coEvery { repository.toggleFavorite(1) } returns Result.failure(Exception("Toggle failed"))
            val useCase = ToggleFavoriteUseCase(repository)

            val result = useCase(1)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Toggle failed")
        }

    @Test
    fun `ToggleFavoriteUseCase handles non-existent meme`() =
        runTest {
            coEvery { repository.toggleFavorite(999) } returns
                Result.failure(
                    Exception("Meme not found"),
                )
            val useCase = ToggleFavoriteUseCase(repository)

            val result = useCase(999)

            assertThat(result.isFailure).isTrue()
        }

    // endregion

    // region UpdateMemeUseCase Tests

    @Test
    fun `UpdateMemeUseCase updates meme successfully`() =
        runTest {
            val meme = createTestMeme(1, "meme1.jpg")
            coEvery { repository.updateMemeWithEmojis(meme) } returns Result.success(Unit)
            val useCase = UpdateMemeUseCase(repository)

            val result = useCase(meme)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.updateMemeWithEmojis(meme) }
        }

    @Test
    fun `UpdateMemeUseCase returns failure on error`() =
        runTest {
            val meme = createTestMeme(1, "meme1.jpg")
            coEvery { repository.updateMemeWithEmojis(meme) } returns Result.failure(Exception("Update failed"))
            val useCase = UpdateMemeUseCase(repository)

            val result = useCase(meme)

            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("Update failed")
        }

    @Test
    fun `UpdateMemeUseCase updates meme with new emojis`() =
        runTest {
            val meme =
                createTestMeme(1, "meme1.jpg").copy(
                    emojiTags = listOf(EmojiTag.fromEmoji("🎉"), EmojiTag.fromEmoji("🔥")),
                )
            coEvery { repository.updateMemeWithEmojis(meme) } returns Result.success(Unit)
            val useCase = UpdateMemeUseCase(repository)

            val result = useCase(meme)

            assertThat(result.isSuccess).isTrue()
            coVerify { repository.updateMemeWithEmojis(meme) }
        }

    // endregion

    // region Helper Functions

    private fun createTestMeme(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false,
    ): Meme =
        Meme(
            id = id,
            filePath = "/storage/memes/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(EmojiTag.fromEmoji("😂")),
            title = "Test Meme $id",
            description = null,
            textContent = null,
            isFavorite = isFavorite,
        )

    // endregion
}
