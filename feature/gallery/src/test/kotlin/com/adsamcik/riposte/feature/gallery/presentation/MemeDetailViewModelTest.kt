package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.SharingPreferences
import com.adsamcik.riposte.feature.gallery.R
import com.adsamcik.riposte.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetAllMemeIdsUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetSimilarMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.RecordMemeViewUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.SimilarMemesStatus
import com.adsamcik.riposte.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.UpdateMemeUseCase
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
    private lateinit var getAllMemeIdsUseCase: GetAllMemeIdsUseCase
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
        getAllMemeIdsUseCase = mockk()

        // Default mock setup
        coEvery { getMemeByIdUseCase(1L) } returns testMeme
        coEvery { getSimilarMemesUseCase(any(), any()) } returns SimilarMemesStatus.NoCandidates
        coEvery { getAllMemeIdsUseCase() } returns listOf(1L, 2L, 3L)
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
            getAllMemeIdsUseCase = getAllMemeIdsUseCase,
            userActionTracker = mockk(relaxed = true),
            preferencesDataStore =
                mockk(relaxed = true) {
                    every { sharingPreferences } returns
                        flowOf(
                            SharingPreferences.DEFAULT.copy(useNativeShareDialog = true),
                        )
                },
            shareTargetRepository = mockk(relaxed = true),
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state has isLoading true`() =
        runTest {
            viewModel = createViewModel()

            assertThat(viewModel.uiState.value.isLoading).isTrue()
        }

    @Test
    fun `loadMeme sets meme when found`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.meme).isEqualTo(testMeme)
            assertThat(state.isLoading).isFalse()
            assertThat(state.errorMessage).isNull()
        }

    @Test
    fun `loadMeme sets error when meme not found`() =
        runTest {
            coEvery { getMemeByIdUseCase(any()) } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("Meme not found")
            assertThat(state.isLoading).isFalse()
        }

    @Test
    fun `loadMeme sets error for invalid meme ID`() =
        runTest {
            savedStateHandle = SavedStateHandle(mapOf("memeId" to -1L))

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("Invalid meme ID")
        }

    @Test
    fun `loadMeme initializes edit fields from meme`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo(testMeme.title)
            assertThat(state.editedDescription).isEqualTo(testMeme.description)
            assertThat(state.editedEmojis).containsExactly("😀")
        }

    @Test
    fun `loadMeme records view after successful load`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } returns testMeme

            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { recordMemeViewUseCase(1L) }
        }

    @Test
    fun `loadMeme does not record view when meme not found`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify(exactly = 0) { recordMemeViewUseCase(any()) }
        }

    // endregion

    // region Edit Mode Tests

    @Test
    fun `ToggleEditMode enables edit mode`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isEditMode).isTrue()
        }

    @Test
    fun `ToggleEditMode disables edit mode when no changes`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isEditMode).isFalse()
        }

    @Test
    fun `ToggleEditMode with unsaved changes shows snackbar`() =
        runTest {
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
    fun `UpdateTitle updates editedTitle`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedTitle).isEqualTo("New Title")
        }

    @Test
    fun `UpdateDescription updates editedDescription`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.UpdateDescription("New Description"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedDescription).isEqualTo("New Description")
        }

    @Test
    fun `AddEmoji adds emoji to list`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.AddEmoji("🎉"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedEmojis).contains("🎉")
        }

    @Test
    fun `AddEmoji does not add duplicate`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val initialSize = viewModel.uiState.value.editedEmojis.size
            viewModel.onIntent(MemeDetailIntent.AddEmoji("😀")) // Already exists
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedEmojis.size).isEqualTo(initialSize)
        }

    @Test
    fun `RemoveEmoji removes emoji from list`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.RemoveEmoji("😀"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedEmojis).doesNotContain("😀")
        }

    // endregion

    // region Favorite Tests

    @Test
    fun `ToggleFavorite calls use case`() =
        runTest {
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
    fun `ToggleFavorite success shows snackbar`() =
        runTest {
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
    fun `ShowDeleteDialog sets showDeleteDialog true`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ShowDeleteDialog)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showDeleteDialog).isTrue()
        }

    @Test
    fun `DismissDeleteDialog sets showDeleteDialog false`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ShowDeleteDialog)
            viewModel.onIntent(MemeDetailIntent.DismissDeleteDialog)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showDeleteDialog).isFalse()
        }

    @Test
    fun `ConfirmDelete calls delete use case`() =
        runTest {
            coEvery { deleteMemeUseCase(1L) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ConfirmDelete)
            advanceUntilIdle()

            coVerify { deleteMemeUseCase(1L) }
        }

    @Test
    fun `ConfirmDelete success navigates back`() =
        runTest {
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
    fun `Share navigates to share screen`() =
        runTest {
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
    fun `OpenShareScreen emits NavigateToShare effect`() =
        runTest {
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
    fun `SaveChanges calls update use case with modified meme`() =
        runTest {
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
    fun `SaveChanges success exits edit mode`() =
        runTest {
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
    fun `DiscardChanges resets edit fields`() =
        runTest {
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
    fun `ShowEmojiPicker sets showEmojiPicker true`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ShowEmojiPicker)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showEmojiPicker).isTrue()
        }

    @Test
    fun `DismissEmojiPicker sets showEmojiPicker false`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ShowEmojiPicker)
            viewModel.onIntent(MemeDetailIntent.DismissEmojiPicker)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.showEmojiPicker).isFalse()
        }

    @Test
    fun `AddEmoji dismisses emoji picker`() =
        runTest {
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
    fun `Dismiss navigates back when no changes`() =
        runTest {
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
    fun `Dismiss shows warning when unsaved changes`() =
        runTest {
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
    fun `hasUnsavedChanges is false when editedTitle equals empty string and original title is null`() =
        runTest {
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
    fun `hasUnsavedChanges is false when editedDescription equals empty string and original description is null`() =
        runTest {
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
        val state =
            MemeDetailUiState(
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
    fun `when meme has no emoji tags then empty state is exposed`() =
        runTest {
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
    fun `when toggle favorite then favorite state updates`() =
        runTest {
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

    // region Meme Identity Regression Tests

    @Test
    fun `ToggleFavorite uses correct meme ID`() =
        runTest {
            coEvery { toggleFavoriteUseCase(1L) } returns Result.success(Unit)
            coEvery { getMemeByIdUseCase(1L) } returns testMeme.copy(isFavorite = true)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
            advanceUntilIdle()

            coVerify { toggleFavoriteUseCase(1L) }
        }

    @Test
    fun `ConfirmDelete uses correct meme ID`() =
        runTest {
            coEvery { deleteMemeUseCase(1L) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ConfirmDelete)
            advanceUntilIdle()

            coVerify { deleteMemeUseCase(1L) }
        }

    @Test
    fun `Share uses correct meme ID`() =
        runTest {
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
    fun `SaveChanges preserves meme identity`() =
        runTest {
            coEvery { updateMemeUseCase(any()) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("Updated Title"))
            viewModel.onIntent(MemeDetailIntent.SaveChanges)
            advanceUntilIdle()

            coVerify { updateMemeUseCase(match { it.id == 1L }) }
        }

    @Test
    fun `loadMeme uses meme ID from SavedStateHandle`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { getMemeByIdUseCase(1L) }
        }

    @Test
    fun `NavigateToSimilarMeme emits correct meme ID`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.NavigateToSimilarMeme(42L))
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isEqualTo(MemeDetailEffect.NavigateToMeme(42L))
            }
        }

    @Test
    fun `CopyToClipboard uses current meme ID`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.CopyToClipboard)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(MemeDetailEffect.CopyToClipboard::class.java)
                assertThat((effect as MemeDetailEffect.CopyToClipboard).memeId).isEqualTo(1L)
            }
        }

    // endregion

    // region State Integrity Under Meme Change

    @Test
    fun `loading a different meme resets edit state`() =
        runTest {
            val meme2 = createTestMeme(2L).copy(title = "Different Meme", description = "Different description")
            savedStateHandle = SavedStateHandle(mapOf("memeId" to 2L))
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo("Different Meme")
            assertThat(state.editedDescription).isEqualTo("Different description")
        }

    @Test
    fun `loading meme with different emojis updates editedEmojis`() =
        runTest {
            val memeWithEmojis = createTestMeme(1L).copy(
                emojiTags = listOf(
                    EmojiTag(emoji = "🔥", name = "fire"),
                    EmojiTag(emoji = "💀", name = "skull"),
                ),
            )
            coEvery { getMemeByIdUseCase(1L) } returns memeWithEmojis

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.editedEmojis).containsExactly("🔥", "💀")
        }

    @Test
    fun `loading meme clears previous error`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.errorMessage).isNotNull()

            // Now make it return a valid meme and reload
            coEvery { getMemeByIdUseCase(1L) } returns testMeme
            viewModel.onIntent(MemeDetailIntent.LoadMeme)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.errorMessage).isNull()
            assertThat(viewModel.uiState.value.meme).isEqualTo(testMeme)
        }

    @Test
    fun `hasUnsavedChanges resets when meme changes`() =
        runTest {
            val meme2 = createTestMeme(2L)
            savedStateHandle = SavedStateHandle(mapOf("memeId" to 2L))
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("Changed Title"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasUnsavedChanges).isTrue()

            // Discard and reload to simulate meme change
            viewModel.onIntent(MemeDetailIntent.DiscardChanges)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasUnsavedChanges).isFalse()
        }

    // endregion

    // region Edit Mode Isolation Tests

    @Test
    fun `edit mode state is preserved during save operation`() =
        runTest {
            coEvery { updateMemeUseCase(any()) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.isEditMode).isTrue()

            viewModel.onIntent(MemeDetailIntent.SaveChanges)
            advanceUntilIdle()

            // After save succeeds, edit mode is exited
            assertThat(viewModel.uiState.value.isEditMode).isFalse()
            assertThat(viewModel.uiState.value.isSaving).isFalse()
        }

    @Test
    fun `multiple edits accumulate correctly`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("New Title"))
            viewModel.onIntent(MemeDetailIntent.UpdateDescription("New Description"))
            viewModel.onIntent(MemeDetailIntent.AddEmoji("🎉"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo("New Title")
            assertThat(state.editedDescription).isEqualTo("New Description")
            assertThat(state.editedEmojis).contains("🎉")
        }

    @Test
    fun `discard changes restores ALL fields`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("Changed Title"))
            viewModel.onIntent(MemeDetailIntent.UpdateDescription("Changed Description"))
            viewModel.onIntent(MemeDetailIntent.AddEmoji("🔥"))
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.DiscardChanges)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo(testMeme.title)
            assertThat(state.editedDescription).isEqualTo(testMeme.description)
            assertThat(state.editedEmojis).containsExactly("😀")
            assertThat(state.isEditMode).isFalse()
        }

    @Test
    fun `save failure preserves edit state`() =
        runTest {
            coEvery { updateMemeUseCase(any()) } returns Result.failure(RuntimeException("Save failed"))

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("Changed Title"))
            viewModel.onIntent(MemeDetailIntent.SaveChanges)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo("Changed Title")
            assertThat(state.isEditMode).isTrue()
            assertThat(state.isSaving).isFalse()
        }

    @Test
    fun `save success updates meme in state`() =
        runTest {
            coEvery { updateMemeUseCase(any()) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleEditMode)
            viewModel.onIntent(MemeDetailIntent.UpdateTitle("Updated Title"))
            viewModel.onIntent(MemeDetailIntent.SaveChanges)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.meme).isNotNull()
            // Title is saved as null when blank, or the value otherwise
            assertThat(state.meme!!.title).isEqualTo("Updated Title")
        }

    // endregion

    // region Similar Memes Regression Tests

    @Test
    fun `similar memes load after meme loads`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { getSimilarMemesUseCase(1L, any()) }
        }

    @Test
    fun `similar memes error does not crash`() =
        runTest {
            coEvery { getSimilarMemesUseCase(any(), any()) } throws RuntimeException("ML error")

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.meme).isNotNull()
            assertThat(state.similarMemesStatus).isInstanceOf(SimilarMemesStatus.Error::class.java)
            assertThat(state.isLoadingSimilar).isFalse()
        }

    @Test
    fun `NavigateToSimilarMeme does not interfere with current meme state`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val stateBefore = viewModel.uiState.value

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.NavigateToSimilarMeme(99L))
                advanceUntilIdle()

                awaitItem() // consume the NavigateToMeme effect
            }

            val stateAfter = viewModel.uiState.value
            assertThat(stateAfter.meme).isEqualTo(stateBefore.meme)
            assertThat(stateAfter.editedTitle).isEqualTo(stateBefore.editedTitle)
            assertThat(stateAfter.editedDescription).isEqualTo(stateBefore.editedDescription)
        }

    // endregion

    // region Quick Share Regression Tests

    @Test
    fun `Share with native dialog preference navigates to share screen`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.Share)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(MemeDetailEffect.NavigateToShare::class.java)
            }
        }

    @Test
    fun `QuickShareMore navigates to share screen and clears quick share state`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.QuickShareMore)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(MemeDetailEffect.NavigateToShare::class.java)
            }

            val state = viewModel.uiState.value
            assertThat(state.quickShareMeme).isNull()
            assertThat(state.quickShareTargets).isEmpty()
        }

    @Test
    fun `DismissQuickShare clears quick share state`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.DismissQuickShare)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.quickShareMeme).isNull()
            assertThat(state.quickShareTargets).isEmpty()
        }

    @Test
    fun `CopyToClipboard clears quick share state`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.CopyToClipboard)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.quickShareMeme).isNull()
            assertThat(state.quickShareTargets).isEmpty()
        }

    // endregion

    // region Error Handling Regression Tests

    @Test
    fun `ToggleFavorite failure shows error snackbar`() =
        runTest {
            coEvery { toggleFavoriteUseCase(1L) } returns Result.failure(RuntimeException("Network error"))

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)
                assertThat((effect as MemeDetailEffect.ShowSnackbar).message).isEqualTo("Failed to update favorite")
            }
        }

    @Test
    fun `ConfirmDelete failure shows error and resets loading`() =
        runTest {
            coEvery { deleteMemeUseCase(1L) } returns Result.failure(RuntimeException("Delete error"))

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.onIntent(MemeDetailIntent.ConfirmDelete)
                advanceUntilIdle()

                val effect = awaitItem()
                assertThat(effect).isInstanceOf(MemeDetailEffect.ShowSnackbar::class.java)
                assertThat((effect as MemeDetailEffect.ShowSnackbar).message).isEqualTo("Failed to delete meme")
            }

            assertThat(viewModel.uiState.value.isLoading).isFalse()
        }

    @Test
    fun `loadMeme exception sets error message`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } throws RuntimeException("Database crashed")

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.errorMessage).isEqualTo("Database crashed")
            assertThat(state.isLoading).isFalse()
        }

    @Test
    fun `SaveChanges with null meme does nothing`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } returns null

            viewModel = createViewModel()
            advanceUntilIdle()

            // Meme is null at this point
            assertThat(viewModel.uiState.value.meme).isNull()

            viewModel.onIntent(MemeDetailIntent.SaveChanges)
            advanceUntilIdle()

            // Should not crash, and updateMemeUseCase should not be called
            coVerify(exactly = 0) { updateMemeUseCase(any()) }
        }

    // endregion

    // region HorizontalPager Tests

    @Test
    fun `allMemeIds is loaded on initialization`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.allMemeIds).containsExactly(1L, 2L, 3L)
        }

    @Test
    fun `ChangeMeme loads new meme data`() =
        runTest {
            val meme2 = createTestMeme(2L).copy(title = "Second Meme")
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.meme?.id).isEqualTo(1L)

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(2L))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.meme?.id).isEqualTo(2L)
            assertThat(viewModel.uiState.value.meme?.title).isEqualTo("Second Meme")
        }

    @Test
    fun `ChangeMeme resets edit state for new meme`() =
        runTest {
            val meme2 = createTestMeme(2L)
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(2L))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.editedTitle).isEqualTo(meme2.title)
            assertThat(state.editedDescription).isEqualTo(meme2.description)
        }

    @Test
    fun `ChangeMeme to same meme ID is a no-op`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val stateBefore = viewModel.uiState.value

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(1L))
            advanceUntilIdle()

            // getMemeByIdUseCase should only be called once (initial load)
            coVerify(exactly = 1) { getMemeByIdUseCase(1L) }
        }

    @Test
    fun `ChangeMeme reloads similar memes for new meme`() =
        runTest {
            val meme2 = createTestMeme(2L)
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(2L))
            advanceUntilIdle()

            coVerify { getSimilarMemesUseCase(2L, any()) }
        }

    @Test
    fun `ChangeMeme records view for new meme`() =
        runTest {
            val meme2 = createTestMeme(2L)
            coEvery { getMemeByIdUseCase(2L) } returns meme2

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(2L))
            advanceUntilIdle()

            coVerify { recordMemeViewUseCase(2L) }
        }

    @Test
    fun `operations use current meme ID after ChangeMeme`() =
        runTest {
            val meme2 = createTestMeme(2L)
            coEvery { getMemeByIdUseCase(2L) } returns meme2
            coEvery { toggleFavoriteUseCase(2L) } returns Result.success(Unit)

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ChangeMeme(2L))
            advanceUntilIdle()

            viewModel.onIntent(MemeDetailIntent.ToggleFavorite)
            advanceUntilIdle()

            coVerify { toggleFavoriteUseCase(2L) }
        }

    @Test
    fun `allMemeIds failure is non-fatal`() =
        runTest {
            coEvery { getAllMemeIdsUseCase() } throws RuntimeException("DB error")

            viewModel = createViewModel()
            advanceUntilIdle()

            // Meme still loads successfully
            assertThat(viewModel.uiState.value.meme).isNotNull()
            assertThat(viewModel.uiState.value.allMemeIds).isEmpty()
        }

    // endregion

    companion object {
        private fun createTestMeme(id: Long) =
            Meme(
                id = id,
                filePath = "/test/path/meme$id.jpg",
                fileName = "meme$id.jpg",
                mimeType = "image/jpeg",
                width = 1080,
                height = 1920,
                fileSizeBytes = 1024L,
                importedAt = System.currentTimeMillis(),
                emojiTags =
                    listOf(
                        EmojiTag(emoji = "😀", name = "grinning"),
                    ),
                title = "Test Meme $id",
                description = "Test description",
                textContent = "test text",
                isFavorite = false,
            )
    }
}
