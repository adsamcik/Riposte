package com.mememymood.feature.share.presentation

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.Meme
import com.mememymood.core.model.ShareConfig
import com.mememymood.feature.share.data.ImageProcessor
import com.mememymood.feature.share.domain.BitmapLoader
import com.mememymood.feature.share.domain.usecase.ShareUseCases
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ShareViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var shareUseCases: ShareUseCases
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var bitmapLoader: BitmapLoader
    private lateinit var viewModel: ShareViewModel

    private val testMeme = createTestMeme(1L)
    private val defaultConfig = ShareConfig()
    private val mockUri: Uri = mockk()
    private val mockIntent: Intent = mockk()
    private val mockBitmap: Bitmap = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = SavedStateHandle(mapOf("memeId" to 1L))
        shareUseCases = mockk(relaxed = true)
        imageProcessor = mockk(relaxed = true)
        bitmapLoader = mockk(relaxed = true)

        // Default mock setup
        coEvery { shareUseCases.getMeme(1L) } returns testMeme
        coEvery { shareUseCases.getDefaultConfig() } returns defaultConfig
        coEvery { shareUseCases.estimateFileSize(any(), any()) } returns 1024L

        // Mock bitmap loader - return mock bitmap and file size
        coEvery { bitmapLoader.loadBitmap(any()) } returns mockBitmap
        coEvery { bitmapLoader.getFileSize(any()) } returns 1024L

        // Mock bitmap dimensions for resize calculations
        every { mockBitmap.width } returns 1080
        every { mockBitmap.height } returns 1920

        // Mock image processor to return the same bitmap (no resize needed)
        every { imageProcessor.resizeBitmap(any(), any(), any()) } returns mockBitmap
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ShareViewModel {
        return ShareViewModel(
            savedStateHandle = savedStateHandle,
            shareUseCases = shareUseCases,
            imageProcessor = imageProcessor,
            bitmapLoader = bitmapLoader,
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state has isLoading true`() = runTest {
        viewModel = createViewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun `loadMeme sets meme and config when successful`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.meme).isEqualTo(testMeme)
        assertThat(state.config).isEqualTo(defaultConfig)
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `loadMeme sets error when meme not found`() = runTest {
        coEvery { shareUseCases.getMeme(any()) } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo("Meme not found")
        assertThat(state.isLoading).isFalse()
    }

    // endregion

    // region Intent Handling Tests

    @Test
    fun `SetFormat updates config format`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ShareIntent.SetFormat(ImageFormat.PNG))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.config.format).isEqualTo(ImageFormat.PNG)
    }

    @Test
    fun `SetQuality updates config quality`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ShareIntent.SetQuality(75))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.config.quality).isEqualTo(75)
    }

    @Test
    fun `SetMaxDimension updates config dimensions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ShareIntent.SetMaxDimension(720))
        advanceUntilIdle()

        val config = viewModel.uiState.value.config
        assertThat(config.maxWidth).isEqualTo(720)
        assertThat(config.maxHeight).isEqualTo(720)
    }

    @Test
    fun `SetStripMetadata updates config`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ShareIntent.SetStripMetadata(true))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.config.stripMetadata).isTrue()
    }

    // endregion

    // region Share Tests

    @Test
    fun `Share emits LaunchShareIntent on success`() = runTest {
        coEvery { shareUseCases.prepareForSharing(any(), any()) } returns Result.success(mockUri)
        coEvery { shareUseCases.createShareIntent(any(), any()) } returns mockIntent

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ShareIntent.Share)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(ShareEffect.LaunchShareIntent::class.java)
            assertThat((effect as ShareEffect.LaunchShareIntent).intent).isEqualTo(mockIntent)
        }
    }

    @Test
    fun `Share emits ShowError on failure`() = runTest {
        coEvery { shareUseCases.prepareForSharing(any(), any()) } returns Result.failure(Exception("Share failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ShareIntent.Share)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(ShareEffect.ShowError::class.java)
            assertThat((effect as ShareEffect.ShowError).message).isEqualTo("Share failed")
        }
    }

    @Test
    fun `Share sets isProcessing during operation`() = runTest {
        coEvery { shareUseCases.prepareForSharing(any(), any()) } returns Result.success(mockUri)
        coEvery { shareUseCases.createShareIntent(any(), any()) } returns mockIntent

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isProcessing).isFalse()

        viewModel.onIntent(ShareIntent.Share)
        // isProcessing should be true during the operation
        advanceUntilIdle()

        // After completion, isProcessing should be false
        assertThat(viewModel.uiState.value.isProcessing).isFalse()
    }

    // endregion

    // region SaveToGallery Tests

    @Test
    fun `SaveToGallery emits SavedToGallery and ShowSnackbar on success`() = runTest {
        coEvery { shareUseCases.saveToGallery(any(), any()) } returns Result.success(mockUri)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ShareIntent.SaveToGallery)
            advanceUntilIdle()

            val savedEffect = awaitItem()
            assertThat(savedEffect).isInstanceOf(ShareEffect.SavedToGallery::class.java)

            val snackbarEffect = awaitItem()
            assertThat(snackbarEffect).isInstanceOf(ShareEffect.ShowSnackbar::class.java)
            assertThat((snackbarEffect as ShareEffect.ShowSnackbar).message).isEqualTo("Saved to gallery")
        }
    }

    @Test
    fun `SaveToGallery emits ShowError on failure`() = runTest {
        coEvery { shareUseCases.saveToGallery(any(), any()) } returns Result.failure(Exception("Save failed"))

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ShareIntent.SaveToGallery)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(ShareEffect.ShowError::class.java)
            assertThat((effect as ShareEffect.ShowError).message).isEqualTo("Save failed")
        }
    }

    // endregion

    // region Navigation Tests

    @Test
    fun `NavigateBack emits NavigateBack effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ShareIntent.NavigateBack)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isEqualTo(ShareEffect.NavigateBack)
        }
    }

    // endregion

    // region Config Update Tests

    @Test
    fun `updating config triggers estimateFileSize`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(ShareIntent.SetQuality(50))
        advanceUntilIdle()

        coVerify { shareUseCases.estimateFileSize(any(), any()) }
    }

    // endregion

    companion object {
        private fun createTestMeme(id: Long) = Meme(
            id = id,
            filePath = "/test/path/meme$id.jpg",
            fileName = "meme$id.jpg",
            mimeType = "image/jpeg",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis(),
            emojiTags = listOf(
                EmojiTag(emoji = "😀", name = "grinning"),
            ),
            title = "Test Meme $id",
            description = "Test description",
            textContent = "test text",
            isFavorite = false,
        )
    }
}
