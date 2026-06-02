package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import android.content.Intent
import app.cash.turbine.turbineScope
import com.adsamcik.riposte.core.common.share.ShareMemeUseCase
import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.model.AppPreferences
import com.adsamcik.riposte.core.model.DarkMode
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.UserDensityPreference
import com.adsamcik.riposte.core.testing.MainDispatcherRule
import com.adsamcik.riposte.core.testing.TestDataFactory
import com.adsamcik.riposte.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GalleryViewModelUseCases
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetAllEmojisWithCountsUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetAllEmojisWithTagCountsUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetAllMemeIdsUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetFavoritesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetLibraryStatsUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemeByIdUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemesByEmojiUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetPagedMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.ToggleFavoriteUseCase
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelSelectionAndDeleteTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private lateinit var getMemesUseCase: GetMemesUseCase
    private val getPagedMemesUseCase: GetPagedMemesUseCase = mockk(relaxed = true)
    private lateinit var getFavoritesUseCase: GetFavoritesUseCase
    private lateinit var getMemesByEmojiUseCase: GetMemesByEmojiUseCase
    private val getMemeByIdUseCase: GetMemeByIdUseCase = mockk(relaxed = true)
    private val deleteMemesUseCase: DeleteMemesUseCase = mockk(relaxed = true)
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase = mockk(relaxed = true)
    private lateinit var getAllMemeIdsUseCase: GetAllMemeIdsUseCase
    private lateinit var getAllEmojisWithCountsUseCase: GetAllEmojisWithCountsUseCase
    private lateinit var getAllEmojisWithTagCountsUseCase: GetAllEmojisWithTagCountsUseCase
    private lateinit var getLibraryStatsUseCase: GetLibraryStatsUseCase
    private val getSuggestionsUseCase: GetSuggestionsUseCase = GetSuggestionsUseCase()
    private lateinit var shareMemeUseCase: ShareMemeUseCase
    private lateinit var galleryRepository: com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var searchDelegate: SearchDelegate
    private lateinit var recoverStaleImportsUseCase: com.adsamcik.riposte.feature.gallery.domain.usecase.RecoverStaleImportsUseCase
    private lateinit var context: Context

    private lateinit var viewModel: GalleryViewModel

    private val testMemes =
        listOf(
            TestDataFactory.createMeme(id = 1, fileName = "meme1.jpg", filePath = "/storage/memes/meme1.jpg"),
            TestDataFactory.createMeme(id = 2, fileName = "meme2.jpg", filePath = "/storage/memes/meme2.jpg"),
            TestDataFactory.createMeme(id = 3, fileName = "meme3.jpg", filePath = "/storage/memes/meme3.jpg", isFavorite = true),
        )

    private val defaultPreferences =
        AppPreferences(
            darkMode = DarkMode.SYSTEM,
            dynamicColors = true,
            gridColumns = 2,
            showEmojiNames = false,
            enableSemanticSearch = true,
            autoExtractText = true,
            saveSearchHistory = true,
        )

    private val preferencesFlow = MutableStateFlow(defaultPreferences)

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.getString(any(), any()) } returns "1 meme deleted"
        every { context.getString(any()) } returns "Error"
        getMemesUseCase = mockk()
        getFavoritesUseCase = mockk()
        getMemesByEmojiUseCase = mockk()
        getAllMemeIdsUseCase = mockk()
        getAllEmojisWithCountsUseCase = mockk()
        getAllEmojisWithTagCountsUseCase = mockk()
        getLibraryStatsUseCase = mockk()
        shareMemeUseCase = mockk()
        coEvery { shareMemeUseCase(any()) } returns Result.success(Intent())
        galleryRepository = mockk(relaxed = true)
        every { galleryRepository.getPagedMemes(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        every { galleryRepository.getPagedMemesByEmojis(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        searchDelegate = mockk(relaxed = true)
        every { searchDelegate.state } returns MutableStateFlow(SearchSliceState())
        every { searchDelegate.effects } returns kotlinx.coroutines.flow.emptyFlow()
        recoverStaleImportsUseCase = mockk(relaxed = true)
        coEvery { recoverStaleImportsUseCase(any()) } returns emptyList()
        preferencesDataStore = mockk()

        every { getMemesUseCase() } returns flowOf(testMemes)
        every { getFavoritesUseCase() } returns flowOf(testMemes.filter { it.isFavorite })
        every { getMemesByEmojiUseCase(any()) } returns flowOf(emptyList())
        every { preferencesDataStore.appPreferences } returns preferencesFlow
        every { preferencesDataStore.lastSessionSuggestionIds } returns flowOf(emptySet())
        every { preferencesDataStore.hasShownShareTip } returns flowOf(true)
        coEvery { preferencesDataStore.updateLastSessionSuggestionIds(any()) } returns Unit
        coEvery { preferencesDataStore.setShareTipShown() } returns Unit
        coEvery { getAllMemeIdsUseCase() } returns testMemes.map { it.id }
        every { getAllEmojisWithCountsUseCase() } returns flowOf(emptyList())
        every { getAllEmojisWithTagCountsUseCase() } returns flowOf(emptyList())
        every { getLibraryStatsUseCase() } returns flowOf(LibraryStatistics(totalMemes = 3, favoriteMemes = 1))
    }

    @After
    fun tearDown() {
        io.mockk.clearAllMocks()
    }

    private fun createViewModel(): GalleryViewModel {
        val useCases =
            GalleryViewModelUseCases(
                getMemes = getMemesUseCase,
                getPagedMemes = getPagedMemesUseCase,
                getFavorites = getFavoritesUseCase,
                getMemesByEmoji = getMemesByEmojiUseCase,
                getMemeById = getMemeByIdUseCase,
                deleteMemes = deleteMemesUseCase,
                toggleFavorite = toggleFavoriteUseCase,
                getAllMemeIds = getAllMemeIdsUseCase,
                getAllEmojisWithCounts = getAllEmojisWithCountsUseCase,
                getAllEmojisWithTagCounts = getAllEmojisWithTagCountsUseCase,
                getLibraryStats = getLibraryStatsUseCase,
            )
        return GalleryViewModel(
            context = context,
            useCases = useCases,
            getSuggestionsUseCase = getSuggestionsUseCase,
            shareMemeUseCase = shareMemeUseCase,
            shareRepository = mockk(relaxed = true),
            galleryRepository = galleryRepository,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
            preferencesDataStore = preferencesDataStore,
            eventBus = com.adsamcik.riposte.core.events.EventBus(),
            recoverStaleImportsUseCase = recoverStaleImportsUseCase,
            searchDelegate = searchDelegate,
        )
    }

    // region Selection Mode Tests

    @Test
    fun `StartSelection enters selection mode`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.StartSelection(1))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isSelectionMode).isTrue()
            assertThat(state.selectedMemeIds).containsExactly(1L)
        }

    @Test
    fun `EnterSelectionMode enters selection mode without pre-selecting`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.EnterSelectionMode)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isSelectionMode).isTrue()
            assertThat(state.selectedMemeIds).isEmpty()
        }

    @Test
    fun `ToggleSelection adds meme to selection`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.StartSelection(1))

            viewModel.onIntent(GalleryIntent.ToggleSelection(2))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.selectedMemeIds).containsExactly(1L, 2L)
        }

    @Test
    fun `ToggleSelection removes meme from selection`() =
        runTest {
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
    fun `ToggleSelection exits selection mode when last item deselected`() =
        runTest {
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
    fun `ClearSelection exits selection mode`() =
        runTest {
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
    fun `SelectAll selects all memes`() =
        runTest {
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

    // region Delete Intent Tests

    @Test
    fun `DeleteSelected emits ShowDeleteConfirmation effect`() =
        runTest {
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
    fun `ConfirmDelete deletes selected memes and shows snackbar`() =
        runTest {
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
                assertThat(effect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)
                assertThat((effect as GalleryEffect.ShowUndoDeleteSnackbar).message).contains("deleted")

                // Selection should be cleared immediately (before delay)
                val state = viewModel.uiState.value
                assertThat(state.isSelectionMode).isFalse()
                assertThat(state.selectedMemeIds).isEmpty()

                // Advance past undo timeout to trigger actual deletion
                advanceTimeBy(5_001)
                advanceUntilIdle()

                effects.cancel()
            }

            coVerify { deleteMemesUseCase(setOf(1L)) }

            val state = viewModel.uiState.value
            assertThat(state.isSelectionMode).isFalse()
            assertThat(state.selectedMemeIds).isEmpty()
        }

    @Test
    fun `ConfirmDelete failure emits error effect`() =
        runTest {
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

                // First: undo snackbar
                val undoEffect = effects.awaitItem()
                assertThat(undoEffect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)

                // Advance past undo timeout to trigger actual deletion
                advanceTimeBy(5_001)
                advanceUntilIdle()

                // Then: error effect
                val errorEffect = effects.awaitItem()
                assertThat(errorEffect).isInstanceOf(GalleryEffect.ShowError::class.java)
                assertThat((errorEffect as GalleryEffect.ShowError).message).contains("Delete failed")

                effects.cancel()
            }
        }

    @Test
    fun `CancelDelete clears pending delete`() =
        runTest {
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

    @Test
    fun `UndoDelete cancels pending deletion`() =
        runTest {
            coEvery { deleteMemesUseCase(any<Set<Long>>()) } returns Result.success(Unit)
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
                // Only advance 1s — enough for ShowUndoDeleteSnackbar, but before 5s deletion timeout
                advanceTimeBy(1_000)

                val undoEffect = effects.awaitItem()
                assertThat(undoEffect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)

                // Undo before the timeout
                viewModel.onIntent(GalleryIntent.UndoDelete)
                advanceTimeBy(6_000)
                advanceUntilIdle()

                effects.cancel()
            }

            // deleteMemes should NOT have been called
            coVerify(exactly = 0) { deleteMemesUseCase(any<Set<Long>>()) }
        }

    @Test
    fun `ConfirmDelete failure after timeout emits error effect`() =
        runTest {
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

                // First: undo snackbar
                val undoEffect = effects.awaitItem()
                assertThat(undoEffect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)

                // Advance past undo timeout to trigger actual deletion
                advanceTimeBy(5_001)
                advanceUntilIdle()

                // Then: error effect
                val errorEffect = effects.awaitItem()
                assertThat(errorEffect).isInstanceOf(GalleryEffect.ShowError::class.java)
                assertThat((errorEffect as GalleryEffect.ShowError).message).contains("Delete failed")

                effects.cancel()
            }
        }

    @Test
    fun `ConfirmDelete adds meme ids to pendingDeleteIds in state`() =
        runTest {
            coEvery { deleteMemesUseCase(any<Set<Long>>()) } returns Result.success(Unit)
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.StartSelection(1))
            viewModel.onIntent(GalleryIntent.ToggleSelection(2))
            viewModel.onIntent(GalleryIntent.DeleteSelected)
            advanceUntilIdle()

            turbineScope {
                val effects = viewModel.effects.testIn(backgroundScope)
                // Skip ShowDeleteConfirmation
                effects.awaitItem()

                viewModel.onIntent(GalleryIntent.ConfirmDelete)
                advanceTimeBy(1_000)

                val undoEffect = effects.awaitItem()
                assertThat(undoEffect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)

                // pendingDeleteIds should be populated in uiState
                val state = viewModel.uiState.value
                assertThat(state.pendingDeleteIds).containsExactly(1L, 2L)

                effects.cancel()
            }
        }

    @Test
    fun `UndoDelete clears pendingDeleteIds from state`() =
        runTest {
            coEvery { deleteMemesUseCase(any<Set<Long>>()) } returns Result.success(Unit)
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
                advanceTimeBy(1_000)

                val undoEffect = effects.awaitItem()
                assertThat(undoEffect).isInstanceOf(GalleryEffect.ShowUndoDeleteSnackbar::class.java)

                // pendingDeleteIds should be populated before undo
                assertThat(viewModel.uiState.value.pendingDeleteIds).isNotEmpty()

                viewModel.onIntent(GalleryIntent.UndoDelete)
                advanceUntilIdle()

                // pendingDeleteIds should be cleared after undo
                assertThat(viewModel.uiState.value.pendingDeleteIds).isEmpty()

                effects.cancel()
            }

            coVerify(exactly = 0) { deleteMemesUseCase(any<Set<Long>>()) }
        }

    // endregion
}
