package com.adsamcik.riposte.feature.settings.presentation.funstats

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.core.ui.theme.RiposteTheme
import com.adsamcik.riposte.feature.settings.R
import com.adsamcik.riposte.feature.settings.domain.model.MilestoneState
import com.adsamcik.riposte.feature.settings.domain.model.MomentumTrend
import com.adsamcik.riposte.feature.settings.presentation.funFactSection
import com.adsamcik.riposte.feature.settings.presentation.memeOMeterSection
import com.adsamcik.riposte.feature.settings.presentation.milestonesSection
import com.adsamcik.riposte.feature.settings.presentation.momentumSection
import com.adsamcik.riposte.feature.settings.presentation.vibeCheckSection

/**
 * Stateful FunStatsScreen that manages ViewModel.
 */
@Composable
fun FunStatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: FunStatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FunStatsScreen(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
    )
}

/**
 * Stateless FunStatsScreen for testing and previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FunStatsScreen(
    uiState: FunStatsUiState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_fun_stats_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_navigate_back),
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                memeOMeterSection(uiState = uiState)
                vibeCheckSection(uiState = uiState)
                funFactSection(uiState = uiState)
                momentumSection(uiState = uiState)
                milestonesSection(uiState = uiState)
            }
        }
    }
}

// region Previews

@Preview(name = "Fun Stats", showBackground = true)
@Preview(name = "Fun Stats Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FunStatsScreenPreview() {
    RiposteTheme {
        FunStatsScreen(
            uiState = FunStatsUiState(
                isLoading = false,
                totalMemeCount = 247,
                favoriteMemeCount = 42,
                collectionTitle = "Meme Warlord",
                totalStorageBytes = 52_428_800,
                storageFunFact = "≈ 35 floppy disks of pure culture",
                topVibes = listOf(
                    EmojiUsageStats("😂", "face with tears of joy", 147),
                    EmojiUsageStats("💀", "skull", 89),
                    EmojiUsageStats("🔥", "fire", 62),
                    EmojiUsageStats("😭", "loudly crying face", 41),
                    EmojiUsageStats("🗿", "moai", 28),
                ),
                vibeTagline = "40% unhinged humor. Chronically online energy.",
                funFactOfTheDay = "Your memes have been viewed 1,234 times total. Popular collection!",
                weeklyImportCounts = listOf(5, 12, 8, 15),
                momentumTrend = MomentumTrend.GROWING,
                memesThisWeek = 15,
                milestones = listOf(
                    MilestoneState("first_steps", "👶", true, 1706140800000),
                    MilestoneState("century_club", "💯", true, 1707350400000),
                    MilestoneState("the_archivist", "📚", false),
                ),
                unlockedMilestoneCount = 2,
                totalMilestoneCount = 13,
            ),
            onNavigateBack = {},
        )
    }
}

// endregion
