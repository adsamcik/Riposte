package com.mememymood.feature.import_feature.presentation

import android.content.Context
import android.net.Uri
import app.cash.turbine.test
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.domain.usecase.ExtractTextUseCase
import com.mememymood.feature.import_feature.domain.usecase.ImportImageUseCase
import com.mememymood.feature.import_feature.domain.usecase.ImportZipBundleUseCase
import com.mememymood.feature.import_feature.domain.usecase.SuggestEmojisUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.google.common.truth.Truth.assertThat

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var context: Context
    private lateinit var importImageUseCase: ImportImageUseCase
    private lateinit var suggestEmojisUseCase: SuggestEmojisUseCase
    private lateinit var extractTextUseCase: ExtractTextUseCase
    private lateinit var importZipBundleUseCase: ImportZipBundleUseCase
    private lateinit var viewModel: ImportViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = mockk(relaxed = true)
        importImageUseCase = mockk(relaxed = true)
        suggestEmojisUseCase = mockk(relaxed = true)
        extractTextUseCase = mockk(relaxed = true)
        importZipBundleUseCase = mockk(relaxed = true)
        viewModel = ImportViewModel(
            context = context,
            importImageUseCase = importImageUseCase,
            suggestEmojisUseCase = suggestEmojisUseCase,
            extractTextUseCase = extractTextUseCase,
            importZipBundleUseCase = importZipBundleUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is empty`() = runTest {
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
    fun `ImagesSelected adds images to state`() = runTest {
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
    fun `ImagesSelected processes images for suggestions`() = runTest {
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
    fun `ImagesSelected handles processing errors`() = runTest {
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
    fun `RemoveImage removes image at index`() = runTest {
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
    fun `EditImage sets editingImageIndex`() = runTest {
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
    fun `CloseEditor clears editingImageIndex`() = runTest {
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
    fun `UpdateTitle updates title for editing image`() = runTest {
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
    fun `UpdateDescription updates description for editing image`() = runTest {
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
    fun `AddEmoji adds emoji to editing image`() = runTest {
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
    fun `AddEmoji does not add duplicate emoji`() = runTest {
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
    fun `RemoveEmoji removes emoji from editing image`() = runTest {
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
    fun `ShowEmojiPicker sets showEmojiPicker to true`() = runTest {
        viewModel.onIntent(ImportIntent.ShowEmojiPicker)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.showEmojiPicker).isTrue()
        }
    }

    @Test
    fun `HideEmojiPicker sets showEmojiPicker to false`() = runTest {
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
    fun `ClearAll clears all images`() = runTest {
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
    fun `PickMoreImages emits PickImages effect`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.PickMoreImages)
            advanceUntilIdle()
            
            val effect = awaitItem()
            assertThat(effect).isEqualTo(ImportEffect.OpenImagePicker)
        }
    }

    @Test
    fun `PickZipBundle emits OpenFilePicker effect`() = runTest {
        viewModel.effects.test {
            viewModel.onIntent(ImportIntent.PickZipBundle)
            advanceUntilIdle()
            
            val effect = awaitItem()
            assertThat(effect).isEqualTo(ImportEffect.OpenFilePicker)
        }
    }

    @Test
    fun `canImport is true when all images have emojis`() = runTest {
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
        viewModel.onIntent(ImportIntent.CloseEditor)
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.canImport).isTrue()
        }
    }

    @Test
    fun `canImport is false when images have no emojis`() = runTest {
        val uri = mockk<Uri> { every { lastPathSegment } returns "meme.jpg" }
        
        coEvery { suggestEmojisUseCase(any()) } returns emptyList()
        coEvery { extractTextUseCase(any()) } returns null

        viewModel.onIntent(ImportIntent.ImagesSelected(listOf(uri)))
        advanceUntilIdle()

        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.canImport).isFalse()
        }
    }

    @Test
    fun `ApplySuggestedEmojis adds suggested emojis to editing image`() = runTest {
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
    fun `StartImport triggers import process`() = runTest {
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
        
        viewModel.onIntent(ImportIntent.StartImport)
        advanceUntilIdle()

        coVerify { importImageUseCase(any(), any()) }
    }

    @Test
    fun `RemoveImage clears editingImageIndex when editing that image`() = runTest {
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
}
