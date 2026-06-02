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
class GalleryViewModelFilterAndEmojiTest {
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
}
