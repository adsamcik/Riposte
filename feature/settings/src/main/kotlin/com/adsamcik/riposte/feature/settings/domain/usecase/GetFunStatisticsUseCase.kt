package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.database.FunStatistics
import com.adsamcik.riposte.core.database.FunStatsProvider
import javax.inject.Inject

/**
 * Use case for loading aggregated fun statistics.
 */
class GetFunStatisticsUseCase
    @Inject
    constructor(
        private val funStatsProvider: FunStatsProvider,
    ) {
        suspend operator fun invoke(): FunStatistics = funStatsProvider.getStatistics()
    }
