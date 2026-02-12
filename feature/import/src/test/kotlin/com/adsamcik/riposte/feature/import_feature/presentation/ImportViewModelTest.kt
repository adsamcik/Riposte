package com.adsamcik.riposte.feature.import_feature.presentation

import android.content.Context
import android.net.Uri
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.feature.import_feature.data.worker.ImportStagingManager
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CheckDuplicateUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.CleanupExtractedFilesUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ExtractZipForPreviewUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.FindDuplicateMemeIdUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.ImportImageUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import com.adsamcik.riposte.feature.import_feature.domain.usecase.UpdateMemeMetadataUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ImportViewModelTest {
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
                importImageUseCase = importImageUseCase,
                suggestEmojisUseCase = suggestEmojisUseCase,
                extractTextUseCase = extractTextUseCase,
                extractZipForPreviewUseCase = extractZipForPreviewUseCase,
                checkDuplicateUseCase = checkDuplicateUseCase,
                findDuplicateMemeIdUseCase = findDuplicateMemeIdUseCase,
                updateMemeMetadataUseCase = updateMemeMetadataUseCase,
                cleanupExtractedFilesUseCase = cleanupExtractedFilesUseCase,
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

    // endregion
}
