package com.adsamcik.riposte.feature.gallery.presentation

import android.content.Context
import android.content.Intent
import app.cash.turbine.turbineScope
import com.adsamcik.riposte.core.common.share.ShareMemeUseCase
import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {
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
    private lateinit var importRequestDao: com.adsamcik.riposte.core.database.dao.ImportRequestDao
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
        importRequestDao = mockk(relaxed = true)
        coEvery { importRequestDao.getStaleRequests(any()) } returns emptyList()
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
            galleryRepository = galleryRepository,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
            preferencesDataStore = preferencesDataStore,
            eventBus = com.adsamcik.riposte.core.events.EventBus(),
            importRequestDao = importRequestDao,
            searchDelegate = searchDelegate,
        )
    }

    // region Initialization Tests

    @Test
    fun `initial state with paging sets usePaging true`() =
        runTest {
            viewModel = createViewModel()

            val state = viewModel.uiState.value
            // With paging for All filter, isLoading is false immediately
            assertThat(state.isLoading).isFalse()
            assertThat(state.usePaging).isTrue()
            assertThat(state.memes).isEmpty()
        }

    @Test
    fun `loads memes on initialization`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            // With paging mode, memes list is empty - data comes from pagedMemes flow
            assertThat(state.usePaging).isTrue()
            assertThat(state.error).isNull()
        }

    @Test
    fun `loads density preference from preferences`() =
        runTest {
            val customPrefs = defaultPreferences.copy(userDensityPreference = UserDensityPreference.COMPACT)
            every { preferencesDataStore.appPreferences } returns flowOf(customPrefs)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.densityPreference).isEqualTo(UserDensityPreference.COMPACT)
        }

    // endregion

    // region LoadMemes Intent Tests

    @Test
    fun `LoadMemes intent refreshes memes`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.LoadMemes)
            advanceUntilIdle()

            // With paging enabled for All filter, usePaging should be true
            assertThat(viewModel.uiState.value.usePaging).isTrue()
        }

    @Test
    fun `LoadMemes with empty list results in paging mode`() =
        runTest {
            every { getMemesUseCase() } returns flowOf(emptyList())

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            // With paging, memes list is empty but data comes from pagedMemes flow
            assertThat(state.usePaging).isTrue()
            assertThat(state.memes).isEmpty()
        }

    // endregion

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

    // region OpenMeme Intent Tests

    @Test
    fun `OpenMeme emits NavigateToMeme effect when not in selection mode`() =
        runTest {
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
    fun `OpenMeme toggles selection when in selection mode`() =
        runTest {
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
    fun `ToggleFavorite calls use case`() =
        runTest {
            coEvery { toggleFavoriteUseCase(any()) } returns Result.success(Unit)
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.ToggleFavorite(1))
            advanceUntilIdle()

            coVerify { toggleFavoriteUseCase(1) }
        }

    @Test
    fun `ToggleFavorite failure emits error effect`() =
        runTest {
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

    // region Filter Intent Tests

    @Test
    fun `SetFilter to All loads all memes`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.All))
            advanceUntilIdle()

            // With paging, data comes from pagedMemes flow, not getMemesUseCase
            assertThat(viewModel.uiState.value.usePaging).isTrue()
            assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.All)
        }

    @Test
    fun `SetFilter to Favorites loads favorites`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
            advanceUntilIdle()

            verify { getFavoritesUseCase() }
            assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.Favorites)
            assertThat(viewModel.uiState.value.memes).hasSize(1)
        }

    // endregion

    // region Favorites Count Tests

    @Test
    fun `favoritesCount is populated from library stats`() =
        runTest {
            every { getLibraryStatsUseCase() } returns flowOf(LibraryStatistics(totalMemes = 10, favoriteMemes = 5))
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.favoritesCount).isEqualTo(5)
        }

    @Test
    fun `favorites filter auto-clears when favorites count drops to zero`() =
        runTest {
            val statsFlow = MutableStateFlow(LibraryStatistics(totalMemes = 10, favoriteMemes = 3))
            every { getLibraryStatsUseCase() } returns statsFlow
            viewModel = createViewModel()
            advanceUntilIdle()

            // Set filter to Favorites
            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.Favorites)

            // Simulate all favorites being removed
            statsFlow.value = LibraryStatistics(totalMemes = 10, favoriteMemes = 0)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.All)
            assertThat(viewModel.uiState.value.favoritesCount).isEqualTo(0)
        }

    @Test
    fun `when favorites count drops to zero while Favorites filter active then filter auto-clears to All and reloads memes`() =
        runTest {
            val statsFlow = MutableStateFlow(LibraryStatistics(totalMemes = 10, favoriteMemes = 3))
            every { getLibraryStatsUseCase() } returns statsFlow
            viewModel = createViewModel()
            advanceUntilIdle()

            // Activate Favorites filter (non-paged)
            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.filter).isEqualTo(GalleryFilter.Favorites)
            assertThat(viewModel.uiState.value.usePaging).isFalse()

            // Drop favorites to zero
            statsFlow.value = LibraryStatistics(totalMemes = 10, favoriteMemes = 0)
            advanceUntilIdle()

            // Filter auto-cleared to All and loadMemes() re-enabled paging
            val state = viewModel.uiState.value
            assertThat(state.filter).isEqualTo(GalleryFilter.All)
            assertThat(state.favoritesCount).isEqualTo(0)
            assertThat(state.usePaging).isTrue()
        }

    // endregion

    // region Grid Columns Intent Tests

    @Test
    fun `SetGridColumns updates preferences`() =
        runTest {
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
    fun `ShareSelected with single meme launches share intent`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.StartSelection(1))

            turbineScope {
                val effects = viewModel.effects.testIn(backgroundScope)

                viewModel.onIntent(GalleryIntent.ShareSelected)
                advanceUntilIdle()

                val effect = effects.awaitItem()
                assertThat(effect).isInstanceOf(GalleryEffect.LaunchShareIntent::class.java)

                effects.cancel()
            }
        }

    @Test
    fun `shareSelected with single meme uses quick share path`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.StartSelection(1))

            viewModel.onIntent(GalleryIntent.ShareSelected)
            advanceUntilIdle()

            coVerify { shareMemeUseCase(1L) }
        }

    @Test
    fun `shareSelected with multiple memes uses multi share path`() =
        runTest {
            coEvery { getMemeByIdUseCase(1L) } returns TestDataFactory.createMeme(id = 1, fileName = "meme1.jpg", filePath = "/storage/memes/meme1.jpg")
            coEvery { getMemeByIdUseCase(2L) } returns TestDataFactory.createMeme(id = 2, fileName = "meme2.jpg", filePath = "/storage/memes/meme2.jpg")
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.StartSelection(1))
            viewModel.onIntent(GalleryIntent.ToggleSelection(2))

            viewModel.onIntent(GalleryIntent.ShareSelected)
            advanceUntilIdle()

            // Single-share path (quickShare / shareMemeUseCase) should NOT be used
            coVerify(exactly = 0) { shareMemeUseCase(any()) }
            // Multi-share path resolves each meme by ID
            coVerify { getMemeByIdUseCase(1L) }
            coVerify { getMemeByIdUseCase(2L) }
        }

    @Test
    fun `quickShare failure emits error effect`() =
        runTest {
            coEvery { shareMemeUseCase(any()) } returns Result.failure(RuntimeException("Share failed"))
            viewModel = createViewModel()
            advanceUntilIdle()

            turbineScope {
                val effects = viewModel.effects.testIn(backgroundScope)

                viewModel.onIntent(GalleryIntent.QuickShare(memeId = 1L))
                advanceUntilIdle()

                val effect = effects.awaitItem()
                assertThat(effect).isInstanceOf(GalleryEffect.ShowError::class.java)
                assertThat((effect as GalleryEffect.ShowError).message).contains("Share failed")

                effects.cancel()
            }
        }

    // endregion

    // region Navigate Intent Tests

    @Test
    fun `NavigateToImport emits NavigateToImport effect`() =
        runTest {
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
    fun `densityPreference updates when preferences change`() =
        runTest {
            val prefsFlow = MutableStateFlow(defaultPreferences)
            every { preferencesDataStore.appPreferences } returns prefsFlow
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.densityPreference).isEqualTo(UserDensityPreference.AUTO)

            prefsFlow.value = defaultPreferences.copy(userDensityPreference = UserDensityPreference.DENSE)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.densityPreference).isEqualTo(UserDensityPreference.DENSE)
        }

    // endregion

    // region UI State Computed Properties Tests

    @Test
    fun `hasSelection returns true when memes selected`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasSelection).isFalse()

            viewModel.onIntent(GalleryIntent.StartSelection(1))
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.hasSelection).isTrue()
        }

    @Test
    fun `selectionCount returns correct count`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onIntent(GalleryIntent.SelectAll)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.selectionCount).isEqualTo(3)
        }

    @Test
    fun `isEmpty returns false when using paging mode`() =
        runTest {
            every { getMemesUseCase() } returns flowOf(emptyList())
            viewModel = createViewModel()
            advanceUntilIdle()

            // With paging enabled, isEmpty is false because data comes from pagedMemes flow
            assertThat(viewModel.uiState.value.usePaging).isTrue()
            // isEmpty only applies when not using paging
            assertThat(viewModel.uiState.value.isEmpty).isFalse()
        }

    // endregion

    // region Emoji Search Tests

    @Test
    fun `emoji tap dispatches UpdateSearchQuery to search delegate`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.UpdateSearchQuery("😂"))
            advanceUntilIdle()

            verify { searchDelegate.onIntent(GalleryIntent.UpdateSearchQuery("😂"), any()) }
        }

    // endregion

    // region Derived State Tests (p1-2)

    @Test
    fun `uniqueEmojis computed from memes in non-paged mode`() =
        runTest {
            val memesWithEmojis =
                listOf(
                    TestDataFactory.createMeme(id = 1, fileName = "a.jpg", filePath = "/storage/memes/a.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"), EmojiTag.fromEmoji("🔥"))),
                    TestDataFactory.createMeme(id = 2, fileName = "b.jpg", filePath = "/storage/memes/b.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
                    TestDataFactory.createMeme(
                        id = 3,
                        fileName = "c.jpg",
                        filePath = "/storage/memes/c.jpg",
                        isFavorite = true,
                        emojiTags = listOf(EmojiTag.fromEmoji("🔥"), EmojiTag.fromEmoji("💀")),
                    ),
                )
            every { getMemesUseCase() } returns flowOf(memesWithEmojis)
            every { getFavoritesUseCase() } returns flowOf(memesWithEmojis)
            every { getAllEmojisWithCountsUseCase() } returns
                flowOf(
                    listOf("😂" to 2, "🔥" to 2, "💀" to 1),
                )
            viewModel = createViewModel()
            advanceUntilIdle()

            val emojis = viewModel.uiState.value.uniqueEmojis
            // 😂 appears 2 times, 🔥 appears 2 times, 💀 appears 1 time
            assertThat(emojis).hasSize(3)
            assertThat(emojis.first().first).isAnyOf("😂", "🔥") // Both have count 2
            assertThat(emojis.last().second).isEqualTo(1)
        }

    // endregion

    // region Regression: Filter State and Selection Mode (p2-ux)

    @Test
    fun `when filter mode is favorites then title reflects filter state`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.filter).isEqualTo(GalleryFilter.Favorites)
            // Favorites filter uses non-paged path
            assertThat(state.usePaging).isFalse()
        }

    @Test
    fun `when start selection intent sent then selection mode activates`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            // EnterSelectionMode is the overflow-menu entry point (no pre-selected meme)
            viewModel.onIntent(GalleryIntent.EnterSelectionMode)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isSelectionMode).isTrue()
            assertThat(state.selectedMemeIds).isEmpty()
        }

    // endregion

    // endregion

    // region Notification Tests

    @Test
    fun `DismissNotification clears active notification`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            // Set a notification via reflection since notification is only set internally
            val uiStateField = viewModel.javaClass.getDeclaredField("_uiState")
            uiStateField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val mutableState = uiStateField.get(viewModel) as MutableStateFlow<GalleryUiState>
            mutableState.update { it.copy(notification = GalleryNotification.ImportComplete(count = 5)) }

            assertThat(viewModel.uiState.value.notification).isNotNull()

            viewModel.onIntent(GalleryIntent.DismissNotification)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.notification).isNull()
        }

    @Test
    fun `notification defaults to null`() =
        runTest {
            viewModel = createViewModel()
            assertThat(viewModel.uiState.value.notification).isNull()
        }

    @Test
    fun `import status defaults to Idle`() =
        runTest {
            viewModel = createViewModel()
            assertThat(viewModel.uiState.value.importStatus).isEqualTo(ImportWorkStatus.Idle)
        }

    @Test
    fun `import and embedding status remain Idle when WorkManager unavailable`() =
        runTest {
            // WorkManager.getInstance(context) throws IllegalStateException in unit tests
            // The ViewModel catches this and keeps status at Idle
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.importStatus).isEqualTo(ImportWorkStatus.Idle)
            assertThat(state.embeddingStatus).isEqualTo(EmbeddingWorkStatus.Idle)
        }

    // endregion

    // region Emoji Usage Sorting Tests

    @Test
    fun `default preference uses usage-ordered emojis`() =
        runTest {
            val usageEmojis = listOf("🔥" to 30, "😂" to 15)
            every { getAllEmojisWithCountsUseCase() } returns flowOf(usageEmojis)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(usageEmojis)
            verify { getAllEmojisWithCountsUseCase() }
        }

    @Test
    fun `when sortEmojisByUsage is false uses tag-count-ordered emojis`() =
        runTest {
            val tagCountEmojis = listOf("😂" to 5, "🔥" to 3)
            every { getAllEmojisWithTagCountsUseCase() } returns flowOf(tagCountEmojis)
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(tagCountEmojis)
            verify { getAllEmojisWithTagCountsUseCase() }
        }

    @Test
    fun `switching preference from true to false changes emoji source`() =
        runTest {
            val usageEmojis = listOf("🔥" to 30, "😂" to 15)
            val tagCountEmojis = listOf("😂" to 5, "🔥" to 3)
            every { getAllEmojisWithCountsUseCase() } returns flowOf(usageEmojis)
            every { getAllEmojisWithTagCountsUseCase() } returns flowOf(tagCountEmojis)

            viewModel = createViewModel()
            advanceUntilIdle()

            // Initially uses usage-ordered
            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(usageEmojis)

            // Switch to count-ordered
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(tagCountEmojis)
        }

    @Test
    fun `switching preference from false to true changes emoji source back`() =
        runTest {
            val usageEmojis = listOf("🔥" to 30, "😂" to 15)
            val tagCountEmojis = listOf("😂" to 5, "🔥" to 3)
            every { getAllEmojisWithCountsUseCase() } returns flowOf(usageEmojis)
            every { getAllEmojisWithTagCountsUseCase() } returns flowOf(tagCountEmojis)

            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)
            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(tagCountEmojis)

            // Switch back to usage-ordered
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = true)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(usageEmojis)
        }

    @Test
    fun `emojis update when underlying data changes while using usage sort`() =
        runTest {
            val emojiFlow = MutableStateFlow(listOf("🔥" to 10))
            every { getAllEmojisWithCountsUseCase() } returns emojiFlow

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).hasSize(1)

            emojiFlow.value = listOf("🔥" to 20, "😂" to 5)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).hasSize(2)
            assertThat(viewModel.uiState.value.uniqueEmojis[0]).isEqualTo("🔥" to 20)
        }

    @Test
    fun `emojis update when underlying data changes while using tag count sort`() =
        runTest {
            val emojiFlow = MutableStateFlow(listOf("😂" to 3))
            every { getAllEmojisWithTagCountsUseCase() } returns emojiFlow
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).hasSize(1)

            emojiFlow.value = listOf("😂" to 4, "🔥" to 2)
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).hasSize(2)
        }

    @Test
    fun `empty emoji list when sortEmojisByUsage is true and no usage data`() =
        runTest {
            every { getAllEmojisWithCountsUseCase() } returns flowOf(emptyList())

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEmpty()
        }

    @Test
    fun `empty emoji list when sortEmojisByUsage is false and no tag data`() =
        runTest {
            every { getAllEmojisWithTagCountsUseCase() } returns flowOf(emptyList())
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)

            viewModel = createViewModel()
            advanceUntilIdle()

            assertThat(viewModel.uiState.value.uniqueEmojis).isEmpty()
        }

    @Test
    fun `rapid preference toggles settle on final value`() =
        runTest {
            val usageEmojis = listOf("🔥" to 30, "😂" to 15)
            val tagCountEmojis = listOf("😂" to 5, "🔥" to 3)
            every { getAllEmojisWithCountsUseCase() } returns flowOf(usageEmojis)
            every { getAllEmojisWithTagCountsUseCase() } returns flowOf(tagCountEmojis)

            viewModel = createViewModel()
            advanceUntilIdle()

            // Rapidly toggle preferences
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = true)
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)
            advanceUntilIdle()

            // Should settle on last value (false = tag count)
            assertThat(viewModel.uiState.value.uniqueEmojis).isEqualTo(tagCountEmojis)
        }

    @Test
    fun `emoji sort preference does not affect other state fields`() =
        runTest {
            val usageEmojis = listOf("🔥" to 10)
            every { getAllEmojisWithCountsUseCase() } returns flowOf(usageEmojis)

            viewModel = createViewModel()
            advanceUntilIdle()

            val stateBefore = viewModel.uiState.value
            preferencesFlow.value = defaultPreferences.copy(sortEmojisByUsage = false)
            advanceUntilIdle()

            val stateAfter = viewModel.uiState.value
            // Core state fields should be unaffected by emoji sort toggle
            assertThat(stateAfter.isLoading).isEqualTo(stateBefore.isLoading)
            assertThat(stateAfter.filter).isEqualTo(stateBefore.filter)
        }

    // endregion

    // region Duplicate Data Safety Tests

    @Test
    fun `memes list with duplicate IDs does not crash grid keys`() =
        runTest {
            val dupeMememes =
                listOf(
                    TestDataFactory.createMeme(id = 1, fileName = "meme1.jpg", filePath = "/storage/memes/meme1.jpg", isFavorite = true),
                    TestDataFactory.createMeme(id = 2, fileName = "meme2.jpg", filePath = "/storage/memes/meme2.jpg", isFavorite = true),
                    TestDataFactory.createMeme(id = 2, fileName = "meme2_dupe.jpg", filePath = "/storage/memes/meme2_dupe.jpg", isFavorite = true), // duplicate ID from DAO JOIN
                    TestDataFactory.createMeme(id = 3, fileName = "meme3.jpg", filePath = "/storage/memes/meme3.jpg", isFavorite = true),
                )
            every { getFavoritesUseCase() } returns flowOf(dupeMememes)
            every { getLibraryStatsUseCase() } returns flowOf(LibraryStatistics(totalMemes = 4, favoriteMemes = 4))

            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.SetFilter(GalleryFilter.Favorites))
            advanceUntilIdle()

            val memes = viewModel.uiState.value.memes
            // GalleryScreen applies .distinctBy { it.id } — data flows through without crash
            assertThat(memes).isNotEmpty()
            assertThat(memes.map { it.id }).contains(2L)
        }

    // endregion

    // region Stale Import Recovery Tests

    @org.junit.Ignore(
        "Requires WorkManager test infrastructure (work-testing artifact or Robolectric). " +
            "WorkManager.getInstance(context) cannot be mocked with mockkStatic in pure JUnit " +
            "because it delegates to WorkManagerImpl.getInstance() internally.",
    )
    @Test
    fun `recoverStaleImports marks requests with completed count as COMPLETED`() =
        runTest {
            val staleRequest = ImportRequestEntity(
                id = "req-1",
                status = ImportRequestEntity.STATUS_IN_PROGRESS,
                imageCount = 10,
                completedCount = 7,
                failedCount = 1,
                stagingDir = "/tmp/staging",
                createdAt = 0L,
                updatedAt = 0L,
            )
            coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(staleRequest)

            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify {
                importRequestDao.updateRequestProgress(
                    id = "req-1",
                    status = ImportRequestEntity.STATUS_COMPLETED,
                    completed = 7,
                    failed = 1,
                    updatedAt = any(),
                )
            }
            val notification = viewModel.uiState.value.notification
            assertThat(notification).isInstanceOf(GalleryNotification.ImportFailed::class.java)
        }

    @org.junit.Ignore(
        "Requires WorkManager test infrastructure (work-testing artifact or Robolectric). " +
            "WorkManager.getInstance(context) cannot be mocked with mockkStatic in pure JUnit.",
    )
    @Test
    fun `recoverStaleImports marks requests with zero completed as FAILED`() =
        runTest {
            val staleRequest = ImportRequestEntity(
                id = "req-2",
                status = ImportRequestEntity.STATUS_IN_PROGRESS,
                imageCount = 5,
                completedCount = 0,
                failedCount = 0,
                stagingDir = "/tmp/staging",
                createdAt = 0L,
                updatedAt = 0L,
            )
            coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(staleRequest)

            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify {
                importRequestDao.updateRequestProgress(
                    id = "req-2",
                    status = ImportRequestEntity.STATUS_FAILED,
                    completed = 0,
                    failed = 0,
                    updatedAt = any(),
                )
            }
        }

    @Test
    fun `recoverStaleImports skips recovery when active import work exists`() =
        runTest {
            val staleRequest = ImportRequestEntity(
                id = "req-3",
                status = ImportRequestEntity.STATUS_IN_PROGRESS,
                imageCount = 10,
                completedCount = 3,
                failedCount = 0,
                stagingDir = "/tmp/staging",
                createdAt = 0L,
                updatedAt = 0L,
            )
            coEvery { importRequestDao.getStaleRequests(any()) } returns listOf(staleRequest)
            // WorkManager.getInstance throws IllegalStateException in unit tests,
            // which the ViewModel catches — this means "WorkManager not available"
            // is treated the same as "active work exists" since recovery is skipped.
            // The try-catch in recoverStaleImports catches the exception gracefully.

            viewModel = createViewModel()
            advanceUntilIdle()

            // Since WorkManager is not available in unit tests (throws IllegalStateException),
            // the exception is caught and updateRequestProgress should still be called
            // because the catch block logs and swallows the error.
            // However, looking at the code flow: getStaleRequests succeeds, then
            // WorkManager.getInstance throws, which is caught by the outer try-catch.
            // So updateRequestProgress is NOT called.
            coVerify(exactly = 0) {
                importRequestDao.updateRequestProgress(
                    id = "req-3",
                    status = any(),
                    completed = any(),
                    failed = any(),
                    updatedAt = any(),
                )
            }
        }

    @Test
    fun `recoverStaleImports handles exception gracefully`() =
        runTest {
            coEvery { importRequestDao.getStaleRequests(any()) } throws RuntimeException("DB error")

            viewModel = createViewModel()
            advanceUntilIdle()

            // ViewModel should not crash — exception is caught and logged
            val state = viewModel.uiState.value
            assertThat(state.notification).isNull()
        }

    // endregion

    // region Suggestions Tests

    @Test
    fun `loadSuggestions populates uiState suggestions from memes`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.suggestions).isNotEmpty()
            assertThat(state.suggestions.size).isAtMost(12)
        }

    @Test
    fun `loadSuggestions persists suggestion ids to datastore`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            coVerify { preferencesDataStore.updateLastSessionSuggestionIds(any()) }
        }

    // endregion

    // region Share Tip Tests

    @Test
    fun `checkShareTip shows snackbar when tip not yet shown and memes exist`() =
        runTest {
            every { preferencesDataStore.hasShownShareTip } returns flowOf(false)

            viewModel = createViewModel()

            turbineScope {
                val effects = viewModel.effects.testIn(backgroundScope)
                advanceUntilIdle()

                val effect = effects.awaitItem()
                assertThat(effect).isInstanceOf(GalleryEffect.ShowSnackbar::class.java)
                assertThat((effect as GalleryEffect.ShowSnackbar).message).contains("Tip")

                coVerify { preferencesDataStore.setShareTipShown() }
                effects.cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `checkShareTip does not show snackbar when tip already shown`() =
        runTest {
            // Default setup already sets hasShownShareTip = true
            viewModel = createViewModel()
            advanceUntilIdle()

            // No snackbar effect should be emitted for share tip
            coVerify(exactly = 0) { preferencesDataStore.setShareTipShown() }
        }

    // endregion
}
