package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetSimilarMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.RecordMemeViewUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus
import com.adsamcik.riposte.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.UpdateMemeUseCase
import com.adsamcik.riposte.feature.gallery.R
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
    private lateinit var recordMemeViewUseCase: RecordMemeViewUseCase
    private lateinit var getSimilarMemesUseCase: GetSimilarMemesUseCase
    private lateinit var context: Context
    private lateinit var viewModel: MemeDetailViewModel

    private val testMeme = createTestMeme(1L)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        savedStateHandle = SavedStateHandle(mapOf("memeId" to 1L))
        context = mockk(relaxed = true)
        // Mock string resources - generic fallbacks first, then specific overrides
        every { context.getString(any()) } returns "Error occurred"
        every { context.getString(any(), any()) } returns "Action completed"
        // Error message mocks
        every { context.getString(R.string.gallery_error_meme_not_found) } returns "Meme not found"
        every { context.getString(R.string.gallery_error_invalid_meme_id) } returns "Invalid meme ID"
        // Snackbar message mocks (must come after generic to take precedence)
        every { context.getString(R.string.gallery_snackbar_added_to_favorites) } returns "Added to favorites"
        every { context.getString(R.string.gallery_snackbar_removed_from_favorites) } returns "Removed from favorites"
        every { context.getString(R.string.gallery_snackbar_unsaved_changes) } returns "You have unsaved changes"
        every { context.getString(R.string.gallery_snackbar_meme_deleted) } returns "Meme deleted"
        every { context.getString(R.string.gallery_snackbar_delete_failed) } returns "Failed to delete meme"
        every { context.getString(R.string.gallery_snackbar_changes_saved) } returns "Changes saved"
        every { context.getString(R.string.gallery_snackbar_save_failed) } returns "Failed to save changes"
        every { context.getString(R.string.gallery_snackbar_favorite_failed) } returns "Failed to update favorite"
        every { context.getString(R.string.gallery_snackbar_save_or_discard) } returns "Save or discard changes first"
        getMemeByIdUseCase = mockk()
        updateMemeUseCase = mockk()
        deleteMemeUseCase = mockk()
        toggleFavoriteUseCase = mockk()
        recordMemeViewUseCase = mockk(relaxUnitFun = true)
        getSimilarMemesUseCase = mockk()

        // Default mock setup
        coEvery { getMemeByIdUseCase(1L) } returns testMeme
        coEvery { getSimilarMemesUseCase(any(), any()) } returns SimilarMemesStatus.NoCandidates
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MemeDetailViewModel {
        return MemeDetailViewModel(
            context = context,
            savedStateHandle = savedStateHandle,
            getMemeByIdUseCase = getMemeByIdUseCase,
            updateMemeUseCase = updateMemeUseCase,
            deleteMemeUseCase = deleteMemeUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            recordMemeViewUseCase = recordMemeViewUseCase,
            getSimilarMemesUseCase = getSimilarMemesUseCase,
            userActionTracker = mockk(relaxed = true),
            preferencesDataStore = mockk(relaxed = true) {
            every { sharingPreferences } returns flowOf(
                SharingPreferences.DEFAULT.copy(useNativeShareDialog = true),
            )
        },
            shareTargetRepository = mockk(relaxed = true),
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

    @Test
    fun `loadMeme records view after successful load`() = runTest {
        coEvery { getMemeByIdUseCase(1L) } returns testMeme

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify { recordMemeViewUseCase(1L) }
    }

    @Test
    fun `loadMeme does not record view when meme not found`() = runTest {
        coEvery { getMemeByIdUseCase(1L) } returns null

        viewModel = createViewModel()
        advanceUntilIdle()

        coVerify(exactly = 0) { recordMemeViewUseCase(any()) }
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
    fun `Share navigates to share screen`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(MemeDetailIntent.Share)
            advanceUntilIdle()

            val effect = awaitItem()
            assertThat(effect).isInstanceOf(MemeDetailEffect.NavigateToShare::class.java)
            assertThat((effect as MemeDetailEffect.NavigateToShare).memeId).isEqualTo(1L)
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

    // region hasUnsavedChanges Tests

    @Test
    fun `hasUnsavedChanges is false when editedTitle equals empty string and original title is null`() = runTest {
        val memeWithNullTitle = createTestMeme(1L).copy(title = null)
        coEvery { getMemeByIdUseCase(1L) } returns memeWithNullTitle

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedTitle).isEqualTo("")
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `hasUnsavedChanges is false when editedDescription equals empty string and original description is null`() = runTest {
        val memeWithNullDescription = createTestMeme(1L).copy(description = null)
        coEvery { getMemeByIdUseCase(1L) } returns memeWithNullDescription

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editedDescription).isEqualTo("")
        assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
    }

    @Test
    fun `save button is disabled when isSaving`() {
        // Verify that isSaving prevents hasUnsavedChanges from enabling save
        val meme = createTestMeme(1L)
        val state = MemeDetailUiState(
            meme = meme,
            isEditMode = true,
            editedTitle = "New Title",
            editedDescription = meme.description ?: "",
            editedEmojis = meme.emojiTags.map { it.emoji },
            isSaving = true,
        )

        assertThat(state.hasUnsavedChanges).isTrue()
        assertThat(state.isSaving).isTrue()
        // Save button enabled = hasUnsavedChanges && !isSaving
        assertThat(state.hasUnsavedChanges && !state.isSaving).isFalse()
    }

    // endregion

    // region Adaptive Peek Height Tests

    @Test
    fun `computeAdaptivePeekHeight returns 25 percent of screen height`() {
        val result = computeAdaptivePeekHeight(800f)
        assertThat(result).isEqualTo(200f)
    }

    @Test
    fun `computeAdaptivePeekHeight clamps to minimum 120dp for small screens`() {
        val result = computeAdaptivePeekHeight(400f)
        assertThat(result).isEqualTo(120f)
    }

    @Test
    fun `computeAdaptivePeekHeight clamps to maximum 280dp for large screens`() {
        val result = computeAdaptivePeekHeight(1600f)
        assertThat(result).isEqualTo(280f)
    }

    @Test
    fun `computeAdaptivePeekHeight returns exact minimum at boundary`() {
        // 480 * 0.25 = 120
        val result = computeAdaptivePeekHeight(480f)
        assertThat(result).isEqualTo(120f)
    }

    @Test
    fun `computeAdaptivePeekHeight returns exact maximum at boundary`() {
        // 1120 * 0.25 = 280
        val result = computeAdaptivePeekHeight(1120f)
        assertThat(result).isEqualTo(280f)
    }

    // endregion

    // region Regression: Empty Emoji State and Favorite Toggle (p2-ux)

    @Test
    fun `when meme has no emoji tags then empty state is exposed`() = runTest {
        val memeWithNoEmojis = createTestMeme(1L).copy(emojiTags = emptyList())
        coEvery { getMemeByIdUseCase(1L) } returns memeWithNoEmojis

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.meme).isNotNull()
        assertThat(state.meme!!.emojiTags).isEmpty()
        assertThat(state.editedEmojis).isEmpty()
    }

    @Test
    fun `when toggle favorite then favorite state updates`() = runTest {
        val updatedMeme = testMeme.copy(isFavorite = true)
        coEvery { toggleFavoriteUseCase(1L) } returns Result.success(Unit)
        coEvery { getMemeByIdUseCase(1L) } returnsMany listOf(testMeme, updatedMeme)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.meme!!.isFavorite).isFalse()

        viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
        advanceUntilIdle()

        coVerify { toggleFavoriteUseCase(1L) }
        assertThat(viewModel.uiState.value.meme!!.isFavorite).isTrue()
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
