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
class GalleryViewModelShareAndMiscTest {
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
            galleryRepository = galleryRepository,
            defaultDispatcher = mainDispatcherRule.testDispatcher,
            preferencesDataStore = preferencesDataStore,
            eventBus = com.adsamcik.riposte.core.events.EventBus(),
            recoverStaleImportsUseCase = recoverStaleImportsUseCase,
            searchDelegate = searchDelegate,
        )
    }

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

    // region Stale Import Recovery Tests

    @Test
    fun `recoverStaleImports shows notification for each recovered import`() =
        runTest {
            coEvery { recoverStaleImportsUseCase(any()) } returns listOf(
                com.adsamcik.riposte.feature.gallery.domain.usecase.RecoveredImport(
                    completedCount = 7,
                    imageCount = 10,
                ),
            )

            viewModel = createViewModel()
            advanceUntilIdle()

            val notification = viewModel.uiState.value.notification
            assertThat(notification).isInstanceOf(GalleryNotification.ImportFailed::class.java)
        }

    @Test
    fun `recoverStaleImports with empty list shows no notification`() =
        runTest {
            coEvery { recoverStaleImportsUseCase(any()) } returns emptyList()

            viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.notification).isNull()
        }

    @Test
    fun `recoverStaleImports handles exception gracefully`() =
        runTest {
            coEvery { recoverStaleImportsUseCase(any()) } throws RuntimeException("DB error")

            viewModel = createViewModel()
            advanceUntilIdle()

            // ViewModel should not crash - exception is caught and logged
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
