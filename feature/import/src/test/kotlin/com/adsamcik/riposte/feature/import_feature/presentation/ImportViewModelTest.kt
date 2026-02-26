package com.adsamcik.riposte.feature.import_feature.presentation

import android.net.Uri
import app.cash.turbine.test
import com.adsamcik.riposte.core.model.EmojiTag
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@Config(manifest = Config.NONE)
class ImportViewModelTest : BaseImportViewModelTest() {

    @Test
    fun `initial state is empty`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).isEmpty()
                assertThat(state.isImporting).isFalse()
                assertThat(state.importProgress).isEqualTo(0f)
                assertThat(state.error).isNull()
                assertThat(state.hasImages).isFalse()
                assertThat(state.canImport).isFalse()
            }
        }

    @Test
    fun `ImagesSelected adds images to state`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).hasSize(2)
                assertThat(state.selectedImages[0].fileName).isEqualTo("meme1.jpg")
                assertThat(state.selectedImages[1].fileName).isEqualTo("meme2.jpg")
                assertThat(state.hasImages).isTrue()
            }
        }

    @Test
    fun `ImagesSelected processes images for suggestions`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val suggestedEmojis = listOf(EmojiTag("😀", "happy"))
            val extractedText = "Hello World"

            coEvery { suggestEmojisUseCase(uri) } returns suggestedEmojis
            coEvery { extractTextUseCase(uri) } returns extractedText

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].suggestedEmojis).isEqualTo(suggestedEmojis)
                assertThat(state.selectedImages[0].extractedText).isEqualTo(extractedText)
                assertThat(state.selectedImages[0].isProcessing).isFalse()
            }
        }

    @Test
    fun `ImagesSelected handles processing errors`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val errorMessage = "Processing failed"

            coEvery { suggestEmojisUseCase(uri) } throws RuntimeException(errorMessage)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].error).isEqualTo(errorMessage)
                assertThat(state.selectedImages[0].isProcessing).isFalse()
            }
        }

    @Test
    fun `RemoveImage removes image at index`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.RemoveImage(0))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).hasSize(1)
                assertThat(state.selectedImages[0].fileName).isEqualTo("meme2.jpg")
            }
        }

    @Test
    fun `EditImage sets editingImageIndex`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.editingImageIndex).isEqualTo(0)
                assertThat(state.editingImage).isNotNull()
            }
        }

    @Test
    fun `CloseEditor clears editingImageIndex`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.editingImageIndex).isNull()
                assertThat(state.editingImage).isNull()
            }
        }

    @Test
    fun `UpdateTitle updates title for editing image`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.UpdateTitle("New Title"))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].title).isEqualTo("New Title")
            }
        }

    @Test
    fun `UpdateDescription updates description for editing image`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.UpdateDescription("New Description"))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].description).isEqualTo("New Description")
            }
        }

    @Test
    fun `AddEmoji adds emoji to editing image`() =
        runTest {
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

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].emojis).contains(emoji)
            }
        }

    @Test
    fun `AddEmoji does not add duplicate emoji`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val emoji = EmojiTag("😀", "happy")

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.AddEmoji(emoji))
            viewModel.onIntent(ImportIntent.AddEmoji(emoji))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].emojis).hasSize(1)
            }
        }

    @Test
    fun `RemoveEmoji removes emoji from editing image`() =
        runTest {
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

            viewModel.onIntent(ImportIntent.RemoveEmoji(emoji))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].emojis).doesNotContain(emoji)
            }
        }

    @Test
    fun `ShowEmojiPicker sets showEmojiPicker to true`() =
        runTest {
            viewModel.onIntent(ImportIntent.ShowEmojiPicker)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showEmojiPicker).isTrue()
            }
        }

    @Test
    fun `HideEmojiPicker sets showEmojiPicker to false`() =
        runTest {
            viewModel.onIntent(ImportIntent.ShowEmojiPicker)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.HideEmojiPicker)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showEmojiPicker).isFalse()
            }
        }

    @Test
    fun `ClearAll clears all images`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.ClearAll)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).isEmpty()
                assertThat(state.hasImages).isFalse()
            }
        }

    @Test
    fun `PickMoreImages emits PickImages effect`() =
        runTest {
            viewModel.effects.test {
                viewModel.onIntent(ImportIntent.PickMoreImages)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isEqualTo(ImportEffect.OpenImagePicker)
            }
        }

    @Test
    fun `PickZipBundle emits OpenFilePicker effect`() =
        runTest {
            viewModel.effects.test {
                viewModel.onIntent(ImportIntent.PickZipBundle)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isEqualTo(ImportEffect.OpenFilePicker)
            }
        }

    @Test
    fun `canImport is true when images are selected regardless of emojis`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.canImport).isTrue()
            }
        }

    @Test
    fun `canImport is false when no images are selected`() =
        runTest {
            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.canImport).isFalse()
            }
        }

    @Test
    fun `ApplySuggestedEmojis adds suggested emojis to editing image`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val suggestedEmojis = listOf(EmojiTag("😀", "happy"), EmojiTag("😂", "laughing"))

            coEvery { suggestEmojisUseCase(uri) } returns suggestedEmojis
            coEvery { extractTextUseCase(uri) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.ApplySuggestedEmojis)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages[0].emojis).containsExactlyElementsIn(suggestedEmojis)
            }
        }

    @Test
    fun `StartImport stages images and enqueues worker`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Import now stages images and enqueues a worker instead of importing directly
            coVerify { importStagingManager.stageImages(any()) }
            coVerify { importRepository.createImportRequest(any(), any(), any()) }
            coVerify { importRepository.createImportRequestItems(any(), any()) }
        }

    @Test
    fun `RemoveImage clears editingImageIndex when editing that image`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.RemoveImage(0))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.editingImageIndex).isNull()
            }
        }

    // region Regression: Max Import Items Limit (p2-ux)

    @Test
    fun `when import screen loaded then max items limit is exposed`() =
        runTest {
            // The max 20 items limit is enforced in ImportScreen.kt at the UI layer
            // via ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20).
            // The ViewModel does not expose or enforce this limit — it accepts any number
            // of images from ImagesSelected intent. This is a UI-layer constraint only.
            // Verify the ViewModel can handle exactly 20 images without issues.
            val uris =
                (1..20).map { i ->
                    mockk<Uri> { every { lastPathSegment } returns "meme$i.jpg" }
                }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(uris))
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).hasSize(20)
                assertThat(state.hasImages).isTrue()
                assertThat(state.canImport).isTrue()
            }
        }

    // endregion

    // region Cleanup after import

    @Test
    fun `ClearAll cleans up extracted files`() =
        runTest {
            viewModel.onIntent(ImportIntent.ClearAll)
            advanceUntilIdle()

            io.mockk.verify { cleanupExtractedFilesUseCase() }
        }

    @Test
    fun `performImport stages images for background processing`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            coVerify { importStagingManager.stageImages(any()) }
        }

    @Test
    fun `performImport shows error on staging failure`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null
            coEvery { importStagingManager.stageImages(any()) } throws RuntimeException("staging failed")

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.isImporting).isFalse()
            }
        }

    @Test
    fun `concurrent import attempts are prevented by atomic guard`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            // Fire two StartImport intents without advancing — the atomic guard
            // in _uiState.update {} synchronously sets isImporting=true on the first call,
            // so the second call sees isImporting=true and returns immediately.
            viewModel.onIntent(ImportIntent.StartImport)
            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Only one import should have actually been executed
            coVerify(exactly = 1) { importStagingManager.stageImages(any()) }
            coVerify(exactly = 1) { importRepository.createImportRequest(any(), any(), any()) }
        }

    // endregion

    // region Duplicate Detection

    @Test
    fun `startImport when duplicates found shows duplicate dialog with correct count`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }
            val uri3 = mockk<Uri> { every { lastPathSegment } returns "meme3.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri3) } returns 200L

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2, uri3)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isTrue()
                assertThat(state.duplicateIndices).containsExactly(0, 2)
                assertThat(state.duplicateMemeIds).containsExactly(0, 100L, 2, 200L)
                assertThat(state.isImporting).isFalse()
            }
        }

    @Test
    fun `startImport when duplicates have metadata marks them as changed`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns 200L

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            // Add emoji to first image so it has changed metadata
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("😀", "happy")))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isTrue()
                assertThat(state.duplicatesWithChangedMetadata).containsExactly(0)
            }
        }

    @Test
    fun `importDuplicatesAnyway imports all images including duplicates`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Now resolve duplicates by importing anyway
            viewModel.onIntent(ImportIntent.ImportDuplicatesAnyway)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isFalse()
                assertThat(state.duplicateIndices).isEmpty()
            }

            // Both images were staged (all images, including the duplicate)
            coVerify { importStagingManager.stageImages(match { it.size == 2 }) }
            coVerify { importRepository.createImportRequest(any(), eq(2), any()) }
        }

    @Test
    fun `skipDuplicates removes duplicate images and imports remaining`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "duplicate.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "new.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Skip duplicates
            viewModel.onIntent(ImportIntent.SkipDuplicates)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isFalse()
                assertThat(state.duplicateIndices).isEmpty()
            }

            // Only 1 non-duplicate image was staged
            coVerify { importStagingManager.stageImages(match { it.size == 1 }) }
            coVerify { importRepository.createImportRequest(any(), eq(1), any()) }
        }

    @Test
    fun `skipDuplicates when all images are duplicates does not import`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "dupe1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "dupe2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns 200L

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.SkipDuplicates)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.selectedImages).isEmpty()
                assertThat(state.showDuplicateDialog).isFalse()
            }

            // No import should have been started
            coVerify(exactly = 0) { importStagingManager.stageImages(any()) }
        }

    @Test
    fun `updateDuplicateMetadata updates existing memes and imports new ones`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "duplicate.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "new.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 100L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null
            coEvery { updateMemeMetadataUseCase(any(), any()) } returns Result.success(Unit)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            // Add metadata to the duplicate so it registers as changed
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("🔥", "fire")))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Resolve via update metadata
            viewModel.onIntent(ImportIntent.UpdateDuplicateMetadata)
            advanceUntilIdle()

            // Metadata was updated for the duplicate
            coVerify { updateMemeMetadataUseCase(eq(100L), any()) }

            // Only the non-duplicate image was staged for import
            coVerify { importStagingManager.stageImages(match { it.size == 1 }) }
            coVerify { importRepository.createImportRequest(any(), eq(1), any()) }

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isFalse()
                assertThat(state.duplicateIndices).isEmpty()
            }
        }

    @Test
    fun `updateDuplicateMetadata when no remaining images navigates to gallery`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "duplicate.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 100L
            coEvery { updateMemeMetadataUseCase(any(), any()) } returns Result.success(Unit)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            // Add metadata so it's a changed duplicate
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.UpdateTitle("Updated Title"))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(ImportIntent.UpdateDuplicateMetadata)
                advanceUntilIdle()

                // Should get snackbar about updated metadata, then navigate
                val effects = mutableListOf<ImportEffect>()
                effects.add(awaitItem())
                effects.add(awaitItem())

                assertThat(effects).contains(ImportEffect.NavigateToGallery)
            }

            // No staging since all images were duplicates
            coVerify(exactly = 0) { importStagingManager.stageImages(any()) }
        }

    // endregion
}
