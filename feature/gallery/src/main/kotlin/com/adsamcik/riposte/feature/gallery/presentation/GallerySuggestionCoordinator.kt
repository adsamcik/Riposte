package com.adsamcik.riposte.feature.gallery.presentation

import com.adsamcik.riposte.core.common.di.DefaultDispatcher
import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.common.suggestion.SuggestionContext
import com.adsamcik.riposte.core.common.suggestion.Surface
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.feature.gallery.domain.usecase.GalleryViewModelUseCases
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class GallerySuggestionCoordinator(
    private val useCases: GalleryViewModelUseCases,
    private val getSuggestionsUseCase: GetSuggestionsUseCase,
    private val preferencesDataStore: PreferencesDataStore,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    private val uiState: MutableStateFlow<GalleryUiState>,
    private val effects: kotlinx.coroutines.channels.Channel<GalleryEffect>,
) {
    private var lastSessionSuggestionIds: Set<Long> = emptySet()

    fun loadSuggestions(scope: CoroutineScope) {
        observePersistedSuggestionIds(scope)
        scope.launch {
            useCases.getMemes().collectLatest { allMemes ->
                updateSuggestions(allMemes)
            }
        }
    }

    fun checkShareTip(scope: CoroutineScope) {
        scope.launch {
            useCases.getMemes().collectLatest { allMemes ->
                if (allMemes.isNotEmpty() && !preferencesDataStore.hasShownShareTip.first()) {
                    preferencesDataStore.setShareTipShown()
                    effects.send(
                        GalleryEffect.ShowSnackbar(
                            "\uD83D\uDCA1 Tip: Long-press any meme to quickly share it!",
                        ),
                    )
                }
            }
        }
    }

    fun refreshSuggestions(scope: CoroutineScope) {
        scope.launch {
            updateSuggestions(useCases.getMemes().first())
        }
    }

    private fun observePersistedSuggestionIds(scope: CoroutineScope) {
        scope.launch {
            preferencesDataStore.lastSessionSuggestionIds.collectLatest { persistedIds ->
                lastSessionSuggestionIds = persistedIds
            }
        }
    }

    private suspend fun updateSuggestions(allMemes: List<com.adsamcik.riposte.core.model.Meme>) {
        val suggestions = withContext(defaultDispatcher) {
            val context = SuggestionContext(
                surface = Surface.GALLERY,
                lastSessionSuggestionIds = lastSessionSuggestionIds,
            )
            getSuggestionsUseCase(allMemes, context)
        }
        lastSessionSuggestionIds = suggestions.map { it.id }.toSet()
        preferencesDataStore.updateLastSessionSuggestionIds(lastSessionSuggestionIds)
        uiState.update { it.copy(suggestions = suggestions) }
    }
}
