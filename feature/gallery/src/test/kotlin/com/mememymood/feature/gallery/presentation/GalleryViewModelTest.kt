package com.mememymood.feature.gallery.presentation

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.datastore.AppPreferences
import com.mememymood.core.datastore.DarkMode
import com.mememymood.core.datastore.PreferencesDataStore
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetFavoritesUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesByEmojiUseCase
import com.mememymood.feature.gallery.domain.usecase.GetMemesUseCase
import com.mememymood.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var getMemesUseCase: GetMemesUseCase
    private lateinit var getFavoritesUseCase: GetFavoritesUseCase
    private lateinit var getMemesByEmojiUseCase: GetMemesByEmojiUseCase
    private lateinit var deleteMemesUseCase: DeleteMemesUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var preferencesDataStore: PreferencesDataStore

    private lateinit var viewModel: GalleryViewModel

    private val testMemes = listOf(
        createTestMeme(1, "meme1.jpg"),
        createTestMeme(2, "meme2.jpg"),
        createTestMeme(3, "meme3.jpg", isFavorite = true)
    )

    private val defaultPreferences = AppPreferences(
        darkMode = DarkMode.SYSTEM,
        dynamicColors = true,
        gridColumns = 2,
        showEmojiNames = false,
        enableSemanticSearch = true,
        autoExtractText = true,
        saveSearchHistory = true
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        getMemesUseCase = mockk()
        getFavoritesUseCase = mockk()
        getMemesByEmojiUseCase = mockk()
        deleteMemesUseCase = mockk()
        toggleFavoriteUseCase = mockk()
        preferencesDataStore = mockk()

        every { getMemesUseCase() } returns flowOf(testMemes)
        every { getFavoritesUseCase() } returns flowOf(testMemes.filter { it.isFavorite })
        every { getMemesByEmojiUseCase(any()) } returns flowOf(emptyList())
        every { preferencesDataStore.appPreferences } returns flowOf(defaultPreferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): GalleryViewModel {
        return GalleryViewModel(
            getMemesUseCase = getMemesUseCase,
            getFavoritesUseCase = getFavoritesUseCase,
            getMemesByEmojiUseCase = getMemesByEmojiUseCase,
            deleteMemeUseCase = deleteMemesUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            preferencesDataStore = preferencesDataStore
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state shows loading`() = runTest {
        viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isTrue()
        assertThat(state.memes).isEmpty()
    }

    @Test
    fun `loads memes on initialization`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.memes).hasSize(3)
        assertThat(state.error).isNull()
    }

    @Test
    fun `loads grid columns from preferences`() = runTest {
        val customPrefs = defaultPreferences.copy(gridColumns = 3)
        every { preferencesDataStore.appPreferences } returns flowOf(customPrefs)

        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.gridColumns).isEqualTo(3)
    }

    // endregion

    // region LoadMemes Intent Tests

    @Test
    fun `LoadMemes intent refreshes memes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.LoadMemes)
        advanceUntilIdle()

        verify(atLeast = 2) { getMemesUseCase() }
    }

    @Test
    fun `LoadMemes with empty list results in empty state`() = runTest {
        every { getMemesUseCase() } returns flowOf(emptyList())
        
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.memes).isEmpty()
        assertThat(state.isEmpty).isTrue()
    }

    // endregion

    // region Selection Mode Tests

    @Test
    fun `StartSelection enters selection mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.StartSelection(1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isTrue()
        assertThat(state.selectedMemeIds).containsExactly(1L)
    }

    @Test
    fun `ToggleSelection adds meme to selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))

        viewModel.onIntent(GalleryIntent.ToggleSelection(2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedMemeIds).containsExactly(1L, 2L)
    }

    @Test
    fun `ToggleSelection removes meme from selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.ToggleSelection(2))

        viewModel.onIntent(GalleryIntent.ToggleSelection(1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedMemeIds).containsExactly(2L)
    }

    @Test
    fun `ToggleSelection exits selection mode when last item deselected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))

        viewModel.onIntent(GalleryIntent.ToggleSelection(1))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.selectedMemeIds).isEmpty()
    }

    @Test
    fun `ClearSelection exits selection mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.ToggleSelection(2))

        viewModel.onIntent(GalleryIntent.ClearSelection)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.selectedMemeIds).isEmpty()
    }

    @Test
    fun `SelectAll selects all memes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.SelectAll)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isTrue()
        assertThat(state.selectedMemeIds).containsExactly(1L, 2L, 3L)
        assertThat(state.selectionCount).isEqualTo(3)
    }

    // endregion

    // region OpenMeme Intent Tests

    @Test
    fun `OpenMeme emits NavigateToMeme effect when not in selection mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            
            viewModel.onIntent(GalleryIntent.OpenMeme(1))
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.NavigateToMeme::class.java)
            assertThat((effect as GalleryEffect.NavigateToMeme).memeId).isEqualTo(1)
            
            effects.cancel()
        }
    }

    @Test
    fun `OpenMeme toggles selection when in selection mode`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))

        viewModel.onIntent(GalleryIntent.OpenMeme(2))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.selectedMemeIds).containsExactly(1L, 2L)
    }

    // endregion

    // region ToggleFavorite Intent Tests

    @Test
    fun `ToggleFavorite calls use case`() = runTest {
        coEvery { toggleFavoriteUseCase(any()) } returns Result.success(Unit)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.ToggleFavorite(1))
        advanceUntilIdle()

        coVerify { toggleFavoriteUseCase(1) }
    }

    @Test
    fun `ToggleFavorite failure emits error effect`() = runTest {
        coEvery { toggleFavoriteUseCase(any()) } returns Result.failure(Exception("Failed"))
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            
            viewModel.onIntent(GalleryIntent.ToggleFavorite(1))
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.ShowError::class.java)
            assertThat((effect as GalleryEffect.ShowError).message).contains("Failed")
            
            effects.cancel()
        }
    }

    // endregion

    // region Delete Intent Tests

    @Test
    fun `DeleteSelected emits ShowDeleteConfirmation effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.ToggleSelection(2))

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            
            viewModel.onIntent(GalleryIntent.DeleteSelected)
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.ShowDeleteConfirmation::class.java)
            assertThat((effect as GalleryEffect.ShowDeleteConfirmation).count).isEqualTo(2)
            
            effects.cancel()
        }
    }

    @Test
    fun `ConfirmDelete deletes selected memes and shows snackbar`() = runTest {
        coEvery { deleteMemesUseCase(any<Set<Long>>()) } returns Result.success(Unit)
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.DeleteSelected)
        advanceUntilIdle()

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            // Skip the ShowDeleteConfirmation effect
            effects.awaitItem()

            viewModel.onIntent(GalleryIntent.ConfirmDelete)
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.ShowSnackbar::class.java)
            assertThat((effect as GalleryEffect.ShowSnackbar).message).contains("deleted")
            
            effects.cancel()
        }

        coVerify { deleteMemesUseCase(setOf(1L)) }
        
        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isFalse()
        assertThat(state.selectedMemeIds).isEmpty()
    }

    @Test
    fun `ConfirmDelete failure emits error effect`() = runTest {
        coEvery { deleteMemesUseCase(any<Set<Long>>()) } returns Result.failure(Exception("Delete failed"))
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.DeleteSelected)
        advanceUntilIdle()

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            // Skip ShowDeleteConfirmation
            effects.awaitItem()

            viewModel.onIntent(GalleryIntent.ConfirmDelete)
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.ShowError::class.java)
            assertThat((effect as GalleryEffect.ShowError).message).contains("Delete failed")
            
            effects.cancel()
        }
    }

    @Test
    fun `CancelDelete clears pending delete`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.DeleteSelected)
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.CancelDelete)
        advanceUntilIdle()

        // Selection should still be active
        val state = viewModel.uiState.value
        assertThat(state.isSelectionMode).isTrue()
    }

    // endregion

    // region Filter Intent Tests

    @Test
    fun `SetFilter to All loads all memes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.All))
        advanceUntilIdle()

        verify(atLeast = 2) { getMemesUseCase() }
        assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.All)
    }

    @Test
    fun `SetFilter to Favorites loads favorites`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
        advanceUntilIdle()

        verify { getFavoritesUseCase() }
        assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.Favorites)
        assertThat(viewModel.uiState.value.memes).hasSize(1)
    }

    @Test
    fun `SetFilter to ByEmoji loads memes by emoji`() = runTest {
        val emojiMemes = listOf(createTestMeme(5, "emoji.jpg"))
        every { getMemesByEmojiUseCase("😂") } returns flowOf(emojiMemes)
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.ByEmoji("😂")))
        advanceUntilIdle()

        verify { getMemesByEmojiUseCase("😂") }
        assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.ByEmoji("😂"))
        assertThat(viewModel.uiState.value.memes).hasSize(1)
    }

    // endregion

    // region Grid Columns Intent Tests

    @Test
    fun `SetGridColumns updates preferences`() = runTest {
        coEvery { preferencesDataStore.setGridColumns(any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onIntent(GalleryIntent.SetGridColumns(3))
        advanceUntilIdle()

        coVerify { preferencesDataStore.setGridColumns(3) }
    }

    // endregion

    // region Share Intent Tests

    @Test
    fun `ShareSelected emits OpenShareSheet effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.StartSelection(1))
        viewModel.onIntent(GalleryIntent.ToggleSelection(2))

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            
            viewModel.onIntent(GalleryIntent.ShareSelected)
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isInstanceOf(GalleryEffect.OpenShareSheet::class.java)
            assertThat((effect as GalleryEffect.OpenShareSheet).memeIds).containsExactly(1L, 2L)
            
            effects.cancel()
        }
    }

    // endregion

    // region Navigate Intent Tests

    @Test
    fun `NavigateToImport emits NavigateToImport effect`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        turbineScope {
            val effects = viewModel.effects.testIn(backgroundScope)
            
            viewModel.onIntent(GalleryIntent.NavigateToImport)
            advanceUntilIdle()

            val effect = effects.awaitItem()
            assertThat(effect).isEqualTo(GalleryEffect.NavigateToImport)
            
            effects.cancel()
        }
    }

    // endregion

    // region Preferences Flow Tests

    @Test
    fun `gridColumns updates when preferences change`() = runTest {
        val prefsFlow = MutableStateFlow(defaultPreferences)
        every { preferencesDataStore.appPreferences } returns prefsFlow
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.gridColumns).isEqualTo(2)

        prefsFlow.value = defaultPreferences.copy(gridColumns = 4)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.gridColumns).isEqualTo(4)
    }

    // endregion

    // region UI State Computed Properties Tests

    @Test
    fun `hasSelection returns true when memes selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasSelection).isFalse()

        viewModel.onIntent(GalleryIntent.StartSelection(1))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasSelection).isTrue()
    }

    @Test
    fun `selectionCount returns correct count`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onIntent(GalleryIntent.SelectAll)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectionCount).isEqualTo(3)
    }

    @Test
    fun `isEmpty returns true when no memes and not loading`() = runTest {
        every { getMemesUseCase() } returns flowOf(emptyList())
        viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isEmpty).isTrue()
    }

    // endregion

    // region Helper Functions

    private fun createTestMeme(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false
    ): Meme = Meme(
        id = id,
        filePath = "/storage/memes/$fileName",
        fileName = fileName,
        mimeType = "image/jpeg",
        width = 1080,
        height = 1080,
        fileSizeBytes = 1024L,
        importedAt = System.currentTimeMillis(),
        emojiTags = listOf(EmojiTag.fromEmoji("😂")),
        title = "Test Meme $id",
        description = null,
        textContent = null,
        isFavorite = isFavorite
    )

    // endregion
}
