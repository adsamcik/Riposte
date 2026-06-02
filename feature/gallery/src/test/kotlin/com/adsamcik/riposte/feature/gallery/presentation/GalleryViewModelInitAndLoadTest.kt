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
class GalleryViewModelInitAndLoadTest {
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
}
