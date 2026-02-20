package com.adsamcik.riposte.feature.settings.presentation.funstats

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adsamcik.riposte.core.common.di.IoDispatcher
import com.adsamcik.riposte.feature.settings.domain.usecase.GetFunStatisticsUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.GetMilestonesUseCase
import com.adsamcik.riposte.feature.settings.domain.usecase.ObserveLibraryStatsUseCase
import com.adsamcik.riposte.feature.settings.presentation.computeCollectionTitle
import com.adsamcik.riposte.feature.settings.presentation.computeFunFactOfTheDay
import com.adsamcik.riposte.feature.settings.presentation.computeMomentumTrend
import com.adsamcik.riposte.feature.settings.presentation.computeStorageFunFact
import com.adsamcik.riposte.feature.settings.presentation.computeVibeTagline
import com.adsamcik.riposte.feature.settings.presentation.computeWeeklyData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FunStatsViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val getFunStatisticsUseCase: GetFunStatisticsUseCase,
        private val getMilestonesUseCase: GetMilestonesUseCase,
        private val observeLibraryStatsUseCase: ObserveLibraryStatsUseCase,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(FunStatsUiState())
        val uiState: StateFlow<FunStatsUiState> = _uiState.asStateFlow()

        init {
            observeLibraryStats()
            loadFunStatistics()
        }

        private fun observeLibraryStats() {
            viewModelScope.launch {
                observeLibraryStatsUseCase()
                    .collect { stats ->
                        _uiState.update {
                            it.copy(
                                totalMemeCount = stats.totalMemes,
                                favoriteMemeCount = stats.favoriteMemes,
                            )
                        }
                    }
            }
        }

        private fun loadFunStatistics() {
            viewModelScope.launch {
                try {
                    val stats = withContext(ioDispatcher) { getFunStatisticsUseCase() }
                    val milestones = getMilestonesUseCase(stats)
                    val weeklyData = computeWeeklyData(stats)
                    val trend = computeMomentumTrend(weeklyData)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            collectionTitle = computeCollectionTitle(context, stats.totalMemes),
                            totalStorageBytes = stats.totalStorageBytes,
                            storageFunFact = computeStorageFunFact(stats.totalStorageBytes),
                            topVibes = stats.topEmojis,
                            vibeTagline = computeVibeTagline(stats.topEmojis),
                            funFactOfTheDay = computeFunFactOfTheDay(context, stats),
                            weeklyImportCounts = weeklyData,
                            momentumTrend = trend,
                            memesThisWeek = weeklyData.lastOrNull() ?: 0,
                            milestones = milestones,
                            unlockedMilestoneCount = milestones.count { m -> m.isUnlocked },
                            totalMilestoneCount = milestones.size,
                        )
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load fun statistics")
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }
