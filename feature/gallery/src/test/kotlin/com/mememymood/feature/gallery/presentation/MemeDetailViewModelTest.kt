package com.mememymood.feature.gallery.presentation

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.Meme
import com.mememymood.core.model.ShareConfig
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.mememymood.feature.gallery.domain.usecase.UpdateMemeUseCase
import com.mememymood.feature.share.domain.usecase.ShareUseCases
import io.mockk.coEvery
import io.mockk.coVerify
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
class MemeDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var getMemeByIdUseCase: GetMemeByIdUseCase
    private lateinit var updateMemeUseCase: UpdateMemeUseCase
    private lateinit var deleteMemeUseCase: DeleteMemesUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var shareUseCases: ShareUseCases
    private lateinit var viewModel: MemeDetailViewModel

    private val testMeme = createTestMeme(1L)
    private val mockUri: Uri = mockk()
    private val mockIntent: Intent = mockk()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = SavedStateHandle(mapOf("memeId" to 1L))
        getMemeByIdUseCase = mockk()
        updateMemeUseCase = mockk()
        deleteMemeUseCase = mockk()
        toggleFavoriteUseCase = mockk()
        shareUseCases = mockk(relaxed = true)

        // Default mock setup
        coEvery { getMemeByIdUseCase(1L) } returns testMeme
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MemeDetailViewModel {
        return MemeDetailViewModel(
            savedStateHandle = savedStateHandle,
            getMemeByIdUseCase = getMemeByIdUseCase,
            updateMemeUseCase = updateMemeUseCase,
            deleteMemeUseCase = deleteMemeUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            shareUseCases = shareUseCases,
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state has isLoading true`() = runTest {
        viewModel = createViewModel()

        assertThat(viewModel.uiState.value.isLoading).isTrue()
    }

    @Test
    fun `loadMeme sets meme when found`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.meme).isEqualTo(testMeme)
        assertThat(state.isLoading).isFalse()
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun `loadMeme sets error when meme not found`() = runTest {
        coEvery { getMemeByIdUseCase(any()) } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo("Meme not found")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `loadMeme sets error for invalid meme ID`() = runTest {
        savedStateHandle = SavedStateHandle(mapOf("memeId" to -1L))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.errorMessage).isEqualTo("Invalid meme ID")
    }

    @Test
    fun `loadMeme initializes edit fields from meme`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.editedTitle).isEqualTo(testMeme.title)
        assertThat(state.editedDescription).isEqualTo(testMeme.description)
        assertThat(state.editedEmojis).containsExactly("😀")
    }

    // endregion

    // region Edit Mode Tests

    @Test
    fun `ToggleEditMode enables edit mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEditMode).isTrue()
    }

    @Test
    fun `ToggleEditMode disables edit mode when no changes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEditMode).isFalse()
    }

    @Test
    fun `ToggleEditMode with unsaved changes shows snackbar`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)
        }
    }

    // endregion

    // region Edit Operations Tests

    @Test
    fun `UpdateTitle updates editedTitle`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedTitle).isEqualTo("New Title")
    }

    @Test
    fun `UpdateDescription updates editedDescription`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.UpdateDescription("New Description"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedDescription).isEqualTo("New Description")
    }

    @Test
    fun `AddEmoji adds emoji to list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.AddEmoji("🎉"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedEmojis).contains("🎉")
    }

    @Test
    fun `AddEmoji does not add duplicate`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val initialSize = viewModel.uiState.value.editedEmojis.size
        viewModel.onIntent(MemeDetailIntent.AddEmoji("😀")) // Already exists
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedEmojis.size).isEqualTo(initialSize)
    }

    @Test
    fun `RemoveEmoji removes emoji from list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.RemoveEmoji("😀"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedEmojis).doesNotContain("😀")
    }

    // endregion

    // region Favorite Tests

    @Test
    fun `ToggleFavorite calls use case`() = runTest {
        val updatedMeme = testMeme.copy(isFavorite = true)
        coEvery { toggleFavoriteUseCase(1L) } returns Result.success(Unit)
        coEvery { getMemeByIdUseCase(1L) } returns updatedMeme

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
        advanceUntilIdle()

        coVerify { toggleFavoriteUseCase(1L) }
    }

    @Test
    fun `ToggleFavorite success shows snackbar`() = runTest {
        val updatedMeme = testMeme.copy(isFavorite = true)
        coEvery { toggleFavoriteUseCase(1L) } returns Result.success(Unit)
        coEvery { getMemeByIdUseCase(1L) } returns updatedMeme

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)
            assertThat((effect as MemeDetailEffect.ShowSnackbar).message).contains("Added to favorites")
        }
    }

    // endregion

    // region Delete Tests

    @Test
    fun `ShowDeleteDialog sets showDeleteDialog true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ShowDeleteDialog)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteDialog).isTrue()
    }

    @Test
    fun `DismissDeleteDialog sets showDeleteDialog false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ShowDeleteDialog)
        viewModel.onIntent(MemeDetailIntent.DismissDeleteDialog)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showDeleteDialog).isFalse()
    }

    @Test
    fun `ConfirmDelete calls delete use case`() = runTest {
        coEvery { deleteMemeUseCase(1L) } returns Result.success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ConfirmDelete)
        advanceUntilIdle()

        coVerify { deleteMemeUseCase(1L) }
    }

    @Test
    fun `ConfirmDelete success navigates back`() = runTest {
        coEvery { deleteMemeUseCase(1L) } returns Result.success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.ConfirmDelete)
            advanceUntilIdle()

            val snackbarEffect = awaitItem()
            assertThat(snackbarEffect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)

            val navEffect = awaitItem()
            assertThat(navEffect).isEqualTo(MemeDetailEffect.NavigateBack)
        }
    }

    // endregion

    // region Share Tests

    @Test
    fun `Share prepares and launches share intent`() = runTest {
        val config = ShareConfig()
        coEvery { shareUseCases.getDefaultConfig() } returns config
        coEvery { shareUseCases.prepareForSharing(any(), any()) } returns Result.success(mockUri)
        coEvery { shareUseCases.createShareIntent(any(), any()) } returns mockIntent

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.Share)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.LaunchShareIntent::class.java)
        }
    }

    @Test
    fun `OpenShareScreen emits NavigateToShare effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.OpenShareScreen)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.NavigateToShare::class.java)
            assertThat((effect as MemeDetailEffect.NavigateToShare).memeId).isEqualTo(1L)
        }
    }

    // endregion

    // region Save Changes Tests

    @Test
    fun `SaveChanges calls update use case with modified meme`() = runTest {
        coEvery { updateMemeUseCase(any()) } returns Result.success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.UpdateTitle("Updated Title"))
        viewModel.onIntent(MemeDetailIntent.SaveChanges)
        advanceUntilIdle()

        coVerify { updateMemeUseCase(match { it.title == "Updated Title" }) }
    }

    @Test
    fun `SaveChanges success exits edit mode`() = runTest {
        coEvery { updateMemeUseCase(any()) } returns Result.success(Unit)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.SaveChanges)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEditMode).isFalse()
    }

    // endregion

    // region Discard Changes Tests

    @Test
    fun `DiscardChanges resets edit fields`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
        viewModel.onIntent(MemeDetailIntent.DiscardChanges)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedTitle).isEqualTo(testMeme.title)
        assertThat(viewModel.uiState.value.isEditMode).isFalse()
    }

    // endregion

    // region Emoji Picker Tests

    @Test
    fun `ShowEmojiPicker sets showEmojiPicker true`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ShowEmojiPicker)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showEmojiPicker).isTrue()
    }

    @Test
    fun `DismissEmojiPicker sets showEmojiPicker false`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ShowEmojiPicker)
        viewModel.onIntent(MemeDetailIntent.DismissEmojiPicker)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showEmojiPicker).isFalse()
    }

    @Test
    fun `AddEmoji dismisses emoji picker`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ShowEmojiPicker)
        viewModel.onIntent(MemeDetailIntent.AddEmoji("🎉"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.showEmojiPicker).isFalse()
    }

    // endregion

    // region Dismiss Tests

    @Test
    fun `Dismiss navigates back when no changes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.Dismiss)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isEqualTo(MemeDetailEffect.NavigateBack)
        }
    }

    @Test
    fun `Dismiss shows warning when unsaved changes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.Dismiss)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)
            assertThat((effect as MemeDetailEffect.ShowSnackbar).message).contains("unsaved changes")
        }
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
