package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CheckDuplicateUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractZipForPreviewUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportImageUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.SuggestEmojisUseCase
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
    private lateinit var extractZipForPreviewUseCase: ExtractZipForPreviewUseCase
    private lateinit var checkDuplicateUseCase: CheckDuplicateUseCase
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        importImageUseCase = mockk(relaxed = true)
        suggestEmojisUseCase = mockk(relaxed = true)
        extractTextUseCase = mockk(relaxed = true)
        extractZipForPreviewUseCase = mockk(relaxed = true)
        checkDuplicateUseCase = mockk(relaxed = true)
        preferencesDataStore = mockk(relaxed = true) {
            every { hasShownEmojiTip } returns flowOf(false)
        }
        viewModel = ImportViewModel(
            context = context,
            importImageUseCase = importImageUseCase,
            suggestEmojisUseCase = suggestEmojisUseCase,
            extractTextUseCase = extractTextUseCase,
            extractZipForPreviewUseCase = extractZipForPreviewUseCase,
            checkDuplicateUseCase = checkDuplicateUseCase,
            userActionTracker = mockk(relaxed = true),
            preferencesDataStore = preferencesDataStore,
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

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(any()) } returns false
        // Slow import to allow cancellation
        coEvery { importImageUseCase(any(), any()) } coAnswers {
            delay(10_000)
            Result.success(mockk<Meme>())
        }

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
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
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)
        coEvery { checkDuplicateUseCase(any()) } returns false

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
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

        coEvery { extractZipForPreviewUseCase(zipUri) } returns com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
            extractedMemes = emptyList(),
            errors = mapOf("bundle" to "Not a valid .meme.zip bundle"),
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
    fun `ZipSelected populates preview grid with extracted memes`() = runTest {
        val zipUri = mockk<Uri>()
        val imageUri = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
        val metadata = com.adsamcik.riposte.core.model.MemeMetadata(
            emojis = listOf("😂"),
            title = "Test Meme",
            description = "Test desc",
        )

        coEvery { extractZipForPreviewUseCase(zipUri) } returns com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
            extractedMemes = listOf(
                com.adsamcik.riposte.feature.import_feature.data.ExtractedMeme(
                    imageUri = imageUri,
                    metadata = metadata,
                ),
            ),
            errors = emptyMap(),
        )

        viewModel.onIntent(ImportIntent.ZipSelected(zipUri))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.selectedImages).hasSize(1)
            assertThat(state.selectedImages[0].title).isEqualTo("Test Meme")
            assertThat(state.selectedImages[0].emojis).hasSize(1)
            assertThat(state.isImporting).isFalse()
        }
    }

    @Test
    fun `ZipSelected shows snackbar for extraction errors`() = runTest {
        val zipUri = mockk<Uri>()
        val imageUri = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }

        coEvery { extractZipForPreviewUseCase(zipUri) } returns com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
            extractedMemes = listOf(
                com.adsamcik.riposte.feature.import_feature.data.ExtractedMeme(
                    imageUri = imageUri,
                    metadata = null,
                ),
            ),
            errors = mapOf("bad.jpg" to "Corrupt file", "bad2.jpg" to "Too large"),
        )

        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.ZipSelected(zipUri))
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(ImportEffect.ShowSnackbar::class.java)
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
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(any()) } returns false

        // First fails, second succeeds
        coEvery { importImageUseCase(uri1, any()) } returns Result.failure(Exception("Failed"))
        coEvery { importImageUseCase(uri2, any()) } returns Result.success(meme)

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
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

    // ==================== Import Without Emojis ====================

    @Test
    fun `import allowed without any emoji tags`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)
        coEvery { checkDuplicateUseCase(any()) } returns false

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        // Verify canImport is true without emojis
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.canImport).isTrue()
            assertThat(state.selectedImages[0].emojis).isEmpty()
        }

        // Import should succeed without emojis
        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        coVerify { importImageUseCase(any(), any()) }
    }

    // ==================== Duplicate Detection Tests ====================

    @Test
    fun `StartImport shows duplicate dialog when duplicates found`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(uri) } returns true

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showDuplicateDialog).isTrue()
            assertThat(state.duplicateIndices).containsExactly(0)
            assertThat(state.isImporting).isFalse()
        }
    }

    @Test
    fun `ImportDuplicatesAnyway imports all including duplicates`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(uri) } returns true
        coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.ImportDuplicatesAnyway)
        advanceUntilIdle()

        coVerify { importImageUseCase(any(), any()) }
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showDuplicateDialog).isFalse()
        }
    }

    @Test
    fun `SkipDuplicates removes duplicate images and imports rest`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "dupe.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "new.jpg" }
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(uri1) } returns true
        coEvery { checkDuplicateUseCase(uri2) } returns false
        coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.SkipDuplicates)
        advanceUntilIdle()

        // Only non-duplicate should be imported
        coVerify(exactly = 1) { importImageUseCase(uri2, any()) }
        coVerify(exactly = 0) { importImageUseCase(uri1, any()) }
    }

    // ==================== Import Result Summary Tests ====================

    @Test
    fun `import result shows summary with failure count`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "bad.jpg" }
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(any()) } returns false
        coEvery { importImageUseCase(uri1, any()) } returns Result.success(meme)
        coEvery { importImageUseCase(uri2, any()) } returns Result.failure(Exception("Failed"))

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.importResult).isNotNull()
            assertThat(state.importResult?.successCount).isEqualTo(1)
            assertThat(state.importResult?.failureCount).isEqualTo(1)
            assertThat(state.importResult?.failedImages).hasSize(1)
        }
    }

    @Test
    fun `RetryFailedImports reloads failed images for retry`() = runTest {
        val uri1 = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }
        val uri2 = mockk<Uri> { every { lastPathSegment } returns "bad.jpg" }
        val meme = mockk<Meme>()

        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null
        coEvery { checkDuplicateUseCase(any()) } returns false
        coEvery { importImageUseCase(uri1, any()) } returns Result.success(meme)
        coEvery { importImageUseCase(uri2, any()) } returns Result.failure(Exception("Failed"))

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        viewModel.onIntent(ImportIntent.RetryFailedImports)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.importResult).isNull()
            assertThat(state.selectedImages).hasSize(1)
            assertThat(state.selectedImages[0].fileName).isEqualTo("bad.jpg")
        }
    }
}
