package com.adsamcik.riposte.feature.settings.domain.usecase

import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.EmbeddingModelInfo
import com.adsamcik.riposte.core.ml.EmbeddingStatistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Combined embedding statistics with model info.
 */
data class EmbeddingStatusInfo(
    val statistics: EmbeddingStatistics,
    val modelInfo: EmbeddingModelInfo,
)

/**
 * Use case for observing embedding statistics reactively.
 *
 * Emits updated [EmbeddingStatusInfo] whenever the valid embedding count changes.
 */
class ObserveEmbeddingStatisticsUseCase
    @Inject
    constructor(
        private val embeddingManager: EmbeddingManager,
    ) {
        operator fun invoke(): Flow<EmbeddingStatusInfo> {
            return embeddingManager.observeValidEmbeddingCount()
                .flatMapLatest {
                    flow {
                        emit(
                            EmbeddingStatusInfo(
                                statistics = embeddingManager.getStatistics(),
                                modelInfo = embeddingManager.getModelInfo(),
                            ),
                        )
                    }
                }
        }
    }
