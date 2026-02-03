package com.mememymood.feature.import_feature.presentation

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.mememymood.feature.import_feature.domain.usecase.ImportImageUseCase
import com.mememymood.feature.import_feature.domain.usecase.ImportZipBundleStreamingUseCase
import com.mememymood.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import com.mememymood.feature.import_feature.domain.usecase.ZipImportEvent
import com.mememymood.feature.import_feature.domain.usecase.ZipImportResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Edge case tests for [ImportViewModel] covering cancellation, concurrency,
 * error handling, and race conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelEdgeCasesTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var importImageUseCase: ImportImageUseCase
    private lateinit var suggestEmojisUseCase: SuggestEmojisUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var importZipBundleStreamingUseCase: ImportZipBundleStreamingUseCase
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        importImageUseCase = mockk(relaxed = true)
        suggestEmojisUseCase = mockk(relaxed = true)
        extractTextUseCase = mockk(relaxed = true)
        importZipBundleStreamingUseCase = mockk(relaxed = true)
        viewModel = ImportViewModel(
            context = context,
            importImageUseCase = importImageUseCase,
            suggestEmojisUseCase = suggestEmojisUseCase,
            extractTextUseCase = extractTextUseCase,
            importZipBundleStreamingUseCase = importZipBundleStreamingUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Cancellation Tests ====================

    @Test
    fun `CancelImport resets importing state`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val emoji = EmojiTag("😀", "happy")

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        // Slow import to allow cancellation
        coEvery { importImageUseCase(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(mockk<Meme>())
        }

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.AddEmoji(emoji))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.CloseEditor)
        advanceUntilIdle()

        // Start import
        viewModel.onIntent(ImportIntent.StartImport)
        advanceTimeBy(100) // Let it start

        // Cancel while import is in progress
        viewModel.onIntent(ImportIntent.CancelImport)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.isImporting).isFalse()
            assertThat(state.importProgress).isEqualTo(0f)
        }
    }

    @Test
    fun `Multiple StartImport calls only process once`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val emoji = EmojiTag("😀", "happy")
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.AddEmoji(emoji))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.CloseEditor)
        advanceUntilIdle()

        // Call StartImport multiple times rapidly
        viewModel.onIntent(ImportIntent.StartImport)
        viewModel.onIntent(ImportIntent.StartImport)
        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        // Should only import once (one call per image)
        coVerify(exactly = 1) { importImageUseCase(any(), any()) }
    }

    // ==================== Concurrent State Modification Tests ====================

    @Test
    fun `RemoveImage during processing updates correct indices`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }
        val uri3 = mockk<Uri> { every { lastPathSegment } returns "meme3.jpg" }

        // Slow processing for first image
        coEvery { suggestEmojisUseCase(uri1) } coAnswers {
            delay(1000)
            listOf(EmojiTag("😀", "happy"))
        }
        coEvery { suggestEmojisUseCase(uri2) } returns listOf(EmojiTag("😂", "laughing"))
        coEvery { suggestEmojisUseCase(uri3) } returns listOf(EmojiTag("🔥", "fire"))
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2, uri3)))
        advanceTimeBy(100) // Let processing start

        // Remove second image while first is still processing
        viewModel.onIntent(ImportIntent.RemoveImage(1))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages).hasSize(2)
            assertThat(state.selectedImages[0].fileName).isEqualTo("meme1.jpg")
            assertThat(state.selectedImages[1].fileName).isEqualTo("meme3.jpg")
        }
    }

    @Test
    fun `EditImage on removed image is handled gracefully`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        // Remove the image
        viewModel.onIntent(ImportIntent.RemoveImage(0))
        advanceUntilIdle()

        // Try to edit the removed image (index out of bounds)
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            // Should not crash and editingImage should be null
            assertThat(state.editingImage).isNull()
        }
    }

    @Test
    fun `UpdateTitle on non-editing state is ignored`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        // Don't enter edit mode, try to update title
        viewModel.onIntent(ImportIntent.UpdateTitle("New Title"))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            // Title should still be null (not updated)
            assertThat(state.selectedImages[0].title).isNull()
        }
    }

    // ==================== ZIP Import Tests ====================

    @Test
    fun `ZipSelected shows error for invalid bundle`() = runTest {
        val zipUri = mockk<Uri>()

        every { importZipBundleStreamingUseCase(zipUri) } returns flowOf(
            ZipImportEvent.Error("bundle", "Not a valid .meme.zip bundle"),
            ZipImportEvent.Complete(
                ZipImportResult(
                    successCount = 0,
                    failureCount = 1,
                    importedMemes = emptyList(),
                    errors = mapOf("bundle" to "Not a valid .meme.zip bundle"),
                ),
            ),
        )

        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.ZipSelected(zipUri))
            advanceUntilIdle()

            // Should receive error effect
            val effect = awaitItem()
            assertThat(effect).isInstanceOf(ImportEffect.ShowError::class.java)
        }
    }

    @Test
    fun `ZipSelected navigates to gallery on success`() = runTest {
        val zipUri = mockk<Uri>()
        val meme = mockk<Meme>()

        every { importZipBundleStreamingUseCase(zipUri) } returns flowOf(
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.Complete(
                ZipImportResult(
                    successCount = 1,
                    failureCount = 0,
                    importedMemes = listOf(meme),
                    errors = emptyMap(),
                ),
            ),
        )

        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.ZipSelected(zipUri))
            advanceUntilIdle()

            // Should receive success effect - ImportComplete triggers navigation via callback
            val effect1 = awaitItem()
            assertThat(effect1).isInstanceOf(ImportEffect.ImportComplete::class.java)

            // Should receive NavigateToGallery
            val effect2 = awaitItem()
            assertThat(effect2).isInstanceOf(ImportEffect.NavigateToGallery::class.java)
        }
    }

    @Test
    fun `ZipSelected shows partial success message`() = runTest {
        val zipUri = mockk<Uri>()
        val meme = mockk<Meme>()

        every { importZipBundleStreamingUseCase(zipUri) } returns flowOf(
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.MemeImported(meme),
            ZipImportEvent.Error("img1.jpg", "Failed"),
            ZipImportEvent.Error("img2.jpg", "Failed"),
            ZipImportEvent.Complete(
                ZipImportResult(
                    successCount = 5,
                    failureCount = 2,
                    importedMemes = listOf(meme, meme, meme, meme, meme),
                    errors = mapOf("img1.jpg" to "Failed", "img2.jpg" to "Failed"),
                ),
            ),
        )

        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.ZipSelected(zipUri))
            advanceUntilIdle()

            // First effect: ImportComplete - triggers navigation via callback
            val effect1 = awaitItem()
            assertThat(effect1).isInstanceOf(ImportEffect.ImportComplete::class.java)

            // Second effect: ShowSnackbar for partial success
            val effect2 = awaitItem()
            assertThat(effect2).isInstanceOf(ImportEffect.ShowSnackbar::class.java)

            // Third effect: NavigateToGallery
            val effect3 = awaitItem()
            assertThat(effect3).isInstanceOf(ImportEffect.NavigateToGallery::class.java)
        }
    }

    // ==================== Processing Error Tests ====================

    @Test
    fun `Processing error for one image does not affect others`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "bad.jpg" }

        coEvery { suggestEmojisUseCase(uri1) } returns listOf(EmojiTag("😀", "happy"))
        coEvery { suggestEmojisUseCase(uri2) } throws RuntimeException("Processing failed")
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages).hasSize(2)
            // First image processed successfully
            assertThat(state.selectedImages[0].suggestedEmojis).isNotEmpty()
            assertThat(state.selectedImages[0].error).isNull()
            // Second image has error
            assertThat(state.selectedImages[1].error).isEqualTo("Processing failed")
        }
    }

    @Test
    fun `Import failure for one image continues with others`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }
        val emoji = EmojiTag("😀", "happy")
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        // First fails, second succeeds
        coEvery { importImageUseCase(uri1, any()) } returns Result.failure(Exception("Failed"))
        coEvery { importImageUseCase(uri2, any()) } returns Result.success(meme)

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
        advanceUntilIdle()

        // Add emoji to both
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.AddEmoji(emoji))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.CloseEditor)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.EditImage(1))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.AddEmoji(emoji))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.CloseEditor)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        // Both should be called
        coVerify { importImageUseCase(uri1, any()) }
        coVerify { importImageUseCase(uri2, any()) }
    }

    // ==================== Duplicate URI Tests ====================

    @Test
    fun `Adding same URI twice adds duplicate images`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            // Both are added (app allows duplicates)
            assertThat(state.selectedImages).hasSize(2)
        }
    }

    // ==================== Edge Case: Empty Selection ====================

    @Test
    fun `ImagesSelected with empty list does nothing`() = runTest {
        viewModel.onIntent(ImportIntent.ImagesSelected(emptyList()))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages).isEmpty()
            assertThat(state.hasImages).isFalse()
        }
    }

    // ==================== Edge Case: ApplySuggestedEmojis ====================

    @Test
    fun `ApplySuggestedEmojis limits to 5 emojis`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val manyEmojis = (1..10).map { EmojiTag("😀", "emoji_$it") }

        coEvery { suggestEmojisUseCase(uri) } returns manyEmojis
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.ApplySuggestedEmojis)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages[0].emojis).hasSize(5)
        }
    }

    // ==================== ClearAll Cleans Everything ====================

    @Test
    fun `ClearAll resets entire state including editing mode`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val emoji = EmojiTag("😀", "happy")

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.EditImage(0))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.AddEmoji(emoji))
        advanceUntilIdle()
        viewModel.onIntent(ImportIntent.ShowEmojiPicker)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.ClearAll)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages).isEmpty()
            assertThat(state.editingImageIndex).isNull()
            assertThat(state.showEmojiPicker).isFalse()
            assertThat(state.isImporting).isFalse()
            assertThat(state.error).isNull()
        }
    }
}
