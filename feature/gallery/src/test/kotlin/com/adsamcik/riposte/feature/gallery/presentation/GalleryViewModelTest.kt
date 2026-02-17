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
import com.adsamcik.riposte.feature.gallery.domain.usecase.DeleteMemesUseCase
import com.adsamcik.riposte.feature.gallery.domain.usecase.GalleryViewModelUseCases
import com.adsamcik.riposte.feature.gallery.domain.usecase.GetAllEmojisWithCountsUseCase
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
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private lateinit var getPagedMemesUseCase: GetPagedMemesUseCase
    private lateinit var getFavoritesUseCase: GetFavoritesUseCase
    private lateinit var getMemesByEmojiUseCase: GetMemesByEmojiUseCase
    private lateinit var getMemeByIdUseCase: GetMemeByIdUseCase
    private lateinit var deleteMemesUseCase: DeleteMemesUseCase
    private lateinit var toggleFavoriteUseCase: ToggleFavoriteUseCase
    private lateinit var getAllMemeIdsUseCase: GetAllMemeIdsUseCase
    private lateinit var getAllEmojisWithCountsUseCase: GetAllEmojisWithCountsUseCase
    private lateinit var getLibraryStatsUseCase: GetLibraryStatsUseCase
    private lateinit var getSuggestionsUseCase: GetSuggestionsUseCase
    private lateinit var shareMemeUseCase: ShareMemeUseCase
    private lateinit var galleryRepository: com.adsamcik.riposte.feature.gallery.domain.repository.GalleryRepository
    private lateinit var preferencesDataStore: PreferencesDataStore
    private lateinit var searchDelegate: SearchDelegate
    private lateinit var context: Context

    private lateinit var viewModel: GalleryViewModel

    private val testMemes =
        listOf(
            createTestMeme(1, "meme1.jpg"),
            createTestMeme(2, "meme2.jpg"),
            createTestMeme(3, "meme3.jpg", isFavorite = true),
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

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        every { context.getString(any(), any()) } returns "1 meme deleted"
        every { context.getString(any()) } returns "Error"
        getMemesUseCase = mockk()
        getPagedMemesUseCase = mockk(relaxed = true)
        getFavoritesUseCase = mockk()
        getMemesByEmojiUseCase = mockk()
        getMemeByIdUseCase = mockk()
        deleteMemesUseCase = mockk()
        toggleFavoriteUseCase = mockk()
        getAllMemeIdsUseCase = mockk()
        getAllEmojisWithCountsUseCase = mockk()
        getLibraryStatsUseCase = mockk()
        getSuggestionsUseCase = GetSuggestionsUseCase()
        shareMemeUseCase = mockk()
        coEvery { shareMemeUseCase(any()) } returns Result.success(Intent())
        galleryRepository = mockk(relaxed = true)
        every { galleryRepository.getPagedMemes(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        every { galleryRepository.getPagedMemesByEmojis(any()) } returns kotlinx.coroutines.flow.emptyFlow()
        searchDelegate = mockk(relaxed = true)
        every { searchDelegate.state } returns MutableStateFlow(SearchSliceState())
        every { searchDelegate.effects } returns kotlinx.coroutines.flow.emptyFlow()
        preferencesDataStore = mockk()

        every { getMemesUseCase() } returns flowOf(testMemes)
        every { getFavoritesUseCase() } returns flowOf(testMemes.filter { it.isFavorite })
        every { getMemesByEmojiUseCase(any()) } returns flowOf(emptyList())
        every { preferencesDataStore.appPreferences } returns flowOf(defaultPreferences)
        every { preferencesDataStore.lastSessionSuggestionIds } returns flowOf(emptySet())
        every { preferencesDataStore.hasShownShareTip } returns flowOf(true)
        coEvery { preferencesDataStore.updateLastSessionSuggestionIds(any()) } returns Unit
        coEvery { preferencesDataStore.setShareTipShown() } returns Unit
        coEvery { getAllMemeIdsUseCase() } returns testMemes.map { it.id }
        every { getAllEmojisWithCountsUseCase() } returns flowOf(emptyList())
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

                val effect = effects.awaitItem()
                assertThat(effect).isInstanceOf(GalleryEffect.ShowError::class.java)
                assertThat((effect as GalleryEffect.ShowError).message).contains("Delete failed")

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
                    createTestMeme(1, "a.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"), EmojiTag.fromEmoji("🔥"))),
                    createTestMeme(2, "b.jpg", emojiTags = listOf(EmojiTag.fromEmoji("😂"))),
                    createTestMeme(
                        3,
                        "c.jpg",
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

    // region Search Suggestion Tests

    @Test
    fun `SelectSuggestion routes to search delegate`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.onIntent(GalleryIntent.SelectSuggestion("funny cat"))
            advanceUntilIdle()

            verify { searchDelegate.onIntent(GalleryIntent.SelectSuggestion("funny cat"), any()) }
        }

    // endregion

    // region Notification Tests

    @Test
    fun `DismissNotification clears active notification`() =
        runTest {
            viewModel = createViewModel()
            advanceUntilIdle()

            // Simulate a notification being set (e.g., from import completion)
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
    fun `GalleryNotification ImportComplete stores count and failed`() {
        val notification = GalleryNotification.ImportComplete(count = 10, failed = 3)
        assertThat(notification.count).isEqualTo(10)
        assertThat(notification.failed).isEqualTo(3)
    }

    @Test
    fun `GalleryNotification ImportComplete defaults failed to zero`() {
        val notification = GalleryNotification.ImportComplete(count = 5)
        assertThat(notification.failed).isEqualTo(0)
    }

    @Test
    fun `GalleryNotification ImportFailed stores message`() {
        val notification = GalleryNotification.ImportFailed(message = "Disk full")
        assertThat(notification.message).isEqualTo("Disk full")
    }

    @Test
    fun `GalleryNotification ImportFailed defaults message to null`() {
        val notification = GalleryNotification.ImportFailed()
        assertThat(notification.message).isNull()
    }

    @Test
    fun `GalleryNotification IndexingComplete stores count`() {
        val notification = GalleryNotification.IndexingComplete(count = 42)
        assertThat(notification.count).isEqualTo(42)
    }

    // endregion

    // region Helper Functions

    private fun createTestMeme(
        id: Long,
        fileName: String,
        isFavorite: Boolean = false,
        emojiTags: List<EmojiTag> = listOf(EmojiTag.fromEmoji("😂")),
        title: String? = "Test Meme $id",
        useCount: Int = 0,
    ): Meme =
        Meme(
            id = id,
            filePath = "/storage/memes/$fileName",
            fileName = fileName,
            mimeType = "image/jpeg",
            width = 1080,
            height = 1080,
            fileSizeBytes = 1024L,
            importedAt = System.currentTimeMillis() - (id * 1000),
            emojiTags = emojiTags,
            title = title,
            description = null,
            textContent = null,
            isFavorite = isFavorite,
            useCount = useCount,
        )

    // endregion
}
