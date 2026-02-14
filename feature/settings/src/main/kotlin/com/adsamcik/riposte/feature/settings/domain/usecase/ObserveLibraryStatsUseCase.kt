package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.database.LibraryStatistics
import com.adsamcik.riposte.core.database.LibraryStatsProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for observing library statistics (total memes, favorites, indexed).
 */
class ObserveLibraryStatsUseCase @Inject constructor(
    private val statsProvider: LibraryStatsProvider,
) {
    operator fun invoke(): Flow<LibraryStatistics> = statsProvider.observeStatistics()
}
