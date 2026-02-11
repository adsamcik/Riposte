package com.adsamcik.riposte.sharing

import com.adsamcik.riposte.core.common.suggestion.GetSuggestionsUseCase
import com.adsamcik.riposte.core.common.suggestion.SuggestionContext
import com.adsamcik.riposte.core.common.suggestion.Surface
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper.toDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes meme changes and keeps sharing shortcuts in sync
 * with the latest suggestions from the StickerHand algorithm.
 *
 * Should be started once at app startup via [start].
 */
@Singleton
class SharingShortcutUpdater
    @Inject
    constructor(
        private val memeDao: MemeDao,
        private val getSuggestionsUseCase: GetSuggestionsUseCase,
        private val sharingShortcutManager: SharingShortcutManager,
    ) {
        /**
         * Start observing meme changes and updating sharing shortcuts.
         *
         * @param scope Application-scoped coroutine scope.
         */
        fun start(scope: CoroutineScope) {
            scope.launch {
                memeDao.getAllMemes()
                    .map { entities -> entities.map { it.toDomain() } }
                    .collectLatest { allMemes ->
                        val context = SuggestionContext(surface = Surface.GALLERY)
                        val suggestions = getSuggestionsUseCase(allMemes, context)
                        sharingShortcutManager.updateShortcuts(suggestions)
                    }
            }
        }
    }
