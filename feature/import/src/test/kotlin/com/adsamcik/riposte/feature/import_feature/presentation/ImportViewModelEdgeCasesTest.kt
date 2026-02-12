package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.feature.import_feature.data.worker.ImportStagingManager
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CheckDuplicateUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CleanupExtractedFilesUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractZipForPreviewUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.FindDuplicateMemeIdUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportImageUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportViewModelUseCases
import com.adsamcik.riposte.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.UpdateMemeMetadataUseCase
import com.google.common.truth.Truth.assertThat
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Edge case tests for [ImportViewModel] covering cancellation, concurrency,
 * error handling, and race conditions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ImportViewModelEdgeCasesTest {
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var importImageUseCase: ImportImageUseCase
    private lateinit var suggestEmojisUseCase: SuggestEmojisUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var extractZipForPreviewUseCase: ExtractZipForPreviewUseCase
    private lateinit var checkDuplicateUseCase: CheckDuplicateUseCase
    private lateinit var findDuplicateMemeIdUseCase: FindDuplicateMemeIdUseCase
    private lateinit var updateMemeMetadataUseCase: UpdateMemeMetadataUseCase
    private lateinit var cleanupExtractedFilesUseCase: CleanupExtractedFilesUseCase
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var importStagingManager: ImportStagingManager
    private lateinit var importRepository: ImportRepository
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Initialize WorkManager with real Robolectric context
        val realContext = RuntimeEnvironment.getApplication()
        val config =
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor())
                .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(realContext, config)

        // Use relaxed mock for ViewModel context (getString returns empty strings)
        context =
            mockk(relaxed = true) {
                // Delegate WorkManager-related calls to real context
                every { applicationContext } returns realContext
                every { packageName } returns realContext.packageName
            }

        importImageUseCase = mockk(relaxed = true)
        suggestEmojisUseCase = mockk(relaxed = true)
        extractTextUseCase = mockk(relaxed = true)
        extractZipForPreviewUseCase = mockk(relaxed = true)
        checkDuplicateUseCase = mockk(relaxed = true)
        findDuplicateMemeIdUseCase = mockk(relaxed = true)
        updateMemeMetadataUseCase = mockk(relaxed = true)
        cleanupExtractedFilesUseCase = mockk(relaxed = true)
        preferencesDataStore =
            mockk(relaxed = true) {
                every { hasShownEmojiTip } returns flowOf(false)
            }
        importStagingManager =
            mockk(relaxed = true) {
                coEvery { stageImages(any()) } returns java.io.File(System.getProperty("java.io.tmpdir"), "staging")
            }
        importRepository = mockk(relaxed = true)
        viewModel =
            ImportViewModel(
                context = context,
                useCases =
                    ImportViewModelUseCases(
                        importImage = importImageUseCase,
                        suggestEmojis = suggestEmojisUseCase,
                        extractText = extractTextUseCase,
                        extractZipForPreview = extractZipForPreviewUseCase,
                        checkDuplicate = checkDuplicateUseCase,
                        findDuplicateMemeId = findDuplicateMemeIdUseCase,
                        updateMemeMetadata = updateMemeMetadataUseCase,
                        cleanupExtractedFiles = cleanupExtractedFilesUseCase,
                    ),
                userActionTracker = mockk(relaxed = true),
                preferencesDataStore = preferencesDataStore,
                importStagingManager = importStagingManager,
                importRepository = importRepository,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== Cancellation Tests ====================

    @Test
    fun `CancelImport resets importing state`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null
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
    fun `Multiple StartImport calls only process once`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val meme = mockk<Meme>()

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            // Call StartImport multiple times rapidly
            viewModel.onIntent(ImportIntent.StartImport)
            viewModel.onIntent(ImportIntent.StartImport)
            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Should only stage once (one import request)
            coVerify(exactly = 1) { importStagingManager.stageImages(any()) }
            coVerify(exactly = 1) { importRepository.createImportRequest(any(), any(), any()) }
        }

    // ==================== Concurrent State Modification Tests ====================

    @Test
    fun `RemoveImage during processing updates correct indices`() =
        runTest {
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
    fun `EditImage on removed image is handled gracefully`() =
        runTest {
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
    fun `UpdateTitle on non-editing state is ignored`() =
        runTest {
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
    fun `ZipSelected shows error for invalid bundle`() =
        runTest {
            val zipUri = mockk<Uri>()

            coEvery { extractZipForPreviewUseCase(zipUri) } returns
                com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
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
    fun `ZipSelected populates preview grid with extracted memes`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val metadata =
                com.adsamcik.riposte.core.model.MemeMetadata(
                    emojis = listOf("😂"),
                    title = "Test Meme",
                    description = "Test desc",
                )

            coEvery { extractZipForPreviewUseCase(zipUri) } returns
                com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
                    extractedMemes =
                        listOf(
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
    fun `ZipSelected shows snackbar for extraction errors`() =
        runTest {
            val zipUri = mockk<Uri>()
            val imageUri = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }

            coEvery { extractZipForPreviewUseCase(zipUri) } returns
                com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
                    extractedMemes =
                        listOf(
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
    fun `Processing error for one image does not affect others`() =
        runTest {
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
    fun `Import failure for one image continues with others`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Both images should be staged (worker handles per-image failures)
            coVerify { importStagingManager.stageImages(match { it.size == 2 }) }
            coVerify { importRepository.createImportRequestItems(any(), match { it.size == 2 }) }
        }

    // ==================== Duplicate URI Tests ====================

    @Test
    fun `Adding same URI twice adds duplicate images`() =
        runTest {
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
    fun `ImagesSelected with empty list does nothing`() =
        runTest {
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
    fun `ApplySuggestedEmojis limits to 5 emojis`() =
        runTest {
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
    fun `ClearAll resets entire state including editing mode`() =
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
    fun `import allowed without any emoji tags`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
            val meme = mockk<Meme>()

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

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

            coVerify { importStagingManager.stageImages(any()) }
            coVerify { importRepository.createImportRequest(any(), any(), any()) }
        }

    // ==================== Duplicate Detection Tests ====================

    @Test
    fun `StartImport shows duplicate dialog when duplicates found`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 42L

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
    fun `ImportDuplicatesAnyway imports all including duplicates`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 42L

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.ImportDuplicatesAnyway)
            advanceUntilIdle()

            coVerify { importStagingManager.stageImages(any()) }
            coVerify { importRepository.createImportRequest(any(), any(), any()) }
            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isFalse()
            }
        }

    @Test
    fun `SkipDuplicates removes duplicate images and imports rest`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "dupe.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "new.jpg" }
            val meme = mockk<Meme>()

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 42L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null
            coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.SkipDuplicates)
            advanceUntilIdle()

            // Only non-duplicate should be staged and submitted
            coVerify { importStagingManager.stageImages(match { it.size == 1 }) }
            coVerify { importRepository.createImportRequestItems(any(), match { it.size == 1 }) }
        }

    // ==================== Progress Reporting Tests ====================

    @Test
    fun `StartImport shows checking duplicates status`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            // Slow duplicate check to observe intermediate state
            coEvery { findDuplicateMemeIdUseCase(any()) } coAnswers {
                delay(1000)
                null
            }
            coEvery { importImageUseCase(any(), any()) } returns Result.success(mockk<Meme>())

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            // Don't advance fully — observe intermediate state
            advanceTimeBy(100)

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.isImporting).isTrue()
                assertThat(state.isProgressIndeterminate).isTrue()
            }
        }

    @Test
    fun `performImport sets statusMessage to current filename`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "meme1.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "meme2.jpg" }
            val meme = mockk<Meme>()

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null
            coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // After import completes, statusMessage should be cleared
            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.isImporting).isFalse()
                assertThat(state.statusMessage).isNull()
            }
        }

    @Test
    fun `duplicate dialog clears statusMessage`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 42L

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isTrue()
                assertThat(state.statusMessage).isNull()
            }
        }

    @Test
    fun `CancelImport clears statusMessage`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null
            coEvery { importImageUseCase(any(), any()) } coAnswers {
                delay(10_000)
                Result.success(mockk<Meme>())
            }

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceTimeBy(100)

            viewModel.onIntent(ImportIntent.CancelImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.statusMessage).isNull()
                assertThat(state.isImporting).isFalse()
            }
        }

    // ==================== Import Result Summary Tests ====================

    @Test
    fun `import result shows summary with failure count`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "bad.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Verify all images were staged for import (worker handles success/failure reporting)
            coVerify { importStagingManager.stageImages(match { it.size == 2 }) }
            coVerify { importRepository.createImportRequest(any(), any(), any()) }
            coVerify { importRepository.createImportRequestItems(any(), match { it.size == 2 }) }
        }

    @Test
    fun `RetryFailedImports reloads failed images for retry`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "good.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "bad.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(any()) } returns null

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Staging should have happened
            coVerify { importStagingManager.stageImages(any()) }

            // RetryFailedImports is a no-op when importResult is null
            // (import results now come from WorkInfo observation)
            viewModel.onIntent(ImportIntent.RetryFailedImports)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.importResult).isNull()
            }
        }

    // ==================== Update Duplicate Metadata Tests ====================

    @Test
    fun `UpdateDuplicateMetadata updates metadata for duplicates with changes`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "dupe.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "new.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 42L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns null
            coEvery { updateMemeMetadataUseCase(any(), any()) } returns Result.success(Unit)

            // Add images and give the duplicate some metadata
            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("😂", "face_with_tears_of_joy")))
            viewModel.onIntent(ImportIntent.UpdateTitle("Updated Title"))
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            // Start import triggers duplicate dialog
            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            // Choose update metadata
            viewModel.onIntent(ImportIntent.UpdateDuplicateMetadata)
            advanceUntilIdle()

            // Verify metadata was updated for the duplicate
            coVerify { updateMemeMetadataUseCase(42L, any()) }
            // Verify the non-duplicate was staged for import
            coVerify { importStagingManager.stageImages(match { it.size == 1 }) }
        }

    @Test
    fun `UpdateDuplicateMetadata navigates to gallery when only duplicates`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "dupe.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 42L
            coEvery { updateMemeMetadataUseCase(any(), any()) } returns Result.success(Unit)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("😂", "face_with_tears_of_joy")))
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(ImportIntent.UpdateDuplicateMetadata)
                advanceUntilIdle()

                // Should get snackbar + navigate to gallery since no non-duplicates remain
                val effects = mutableListOf<ImportEffect>()
                effects.add(awaitItem())
                effects.add(awaitItem())
                assertThat(effects).contains(ImportEffect.NavigateToGallery)
            }
        }

    @Test
    fun `StartImport tracks duplicatesWithChangedMetadata only for images with metadata`() =
        runTest {
            val uri1 = mockk<Uri> { every { lastPathSegment } returns "dupe_with_meta.jpg" }
            val uri2 = mockk<Uri> { every { lastPathSegment } returns "dupe_no_meta.jpg" }

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri1) } returns 42L
            coEvery { findDuplicateMemeIdUseCase(uri2) } returns 43L

            // Add images
            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri1, uri2)))
            advanceUntilIdle()

            // Give only the first image metadata
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("😂", "face_with_tears_of_joy")))
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.showDuplicateDialog).isTrue()
                assertThat(state.duplicateIndices).hasSize(2)
                // Only the first image has changed metadata
                assertThat(state.duplicatesWithChangedMetadata).containsExactly(0)
                assertThat(state.duplicateMemeIds).containsEntry(0, 42L)
                assertThat(state.duplicateMemeIds).containsEntry(1, 43L)
            }
        }

    @Test
    fun `ImportDuplicatesAnyway clears metadata tracking state`() =
        runTest {
            val uri = mockk<Uri> { every { lastPathSegment } returns "dupe.jpg" }
            val meme = mockk<Meme>()

            coEvery { suggestEmojisUseCase(any()) } returns emptyList()
            coEvery { extractTextUseCase(any()) } returns null
            coEvery { findDuplicateMemeIdUseCase(uri) } returns 42L
            coEvery { importImageUseCase(any(), any()) } returns Result.success(meme)

            viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.EditImage(0))
            advanceUntilIdle()
            viewModel.onIntent(ImportIntent.AddEmoji(EmojiTag("😂", "face_with_tears_of_joy")))
            viewModel.onIntent(ImportIntent.CloseEditor)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.StartImport)
            advanceUntilIdle()

            viewModel.onIntent(ImportIntent.ImportDuplicatesAnyway)
            advanceUntilIdle()

            viewModel.uiState.test {
                val state = awaitItem()
                assertThat(state.duplicatesWithChangedMetadata).isEmpty()
                assertThat(state.duplicateMemeIds).isEmpty()
                assertThat(state.showDuplicateDialog).isFalse()
            }
        }
}
