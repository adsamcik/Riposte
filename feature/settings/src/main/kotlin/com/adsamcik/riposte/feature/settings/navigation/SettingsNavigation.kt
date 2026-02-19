package com.adsamcik.riposte.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.adsamcik.riposte.core.common.navigation.FunStatsRoute
import com.adsamcik.riposte.core.common.navigation.DuplicateDetectionRoute
import com.adsamcik.riposte.core.common.navigation.LicensesRoute
import com.adsamcik.riposte.core.common.navigation.SettingsRoute
import com.adsamcik.riposte.feature.settings.presentation.LicensesScreen
import com.adsamcik.riposte.feature.settings.presentation.SettingsScreen
import com.adsamcik.riposte.feature.settings.presentation.funstats.FunStatsScreen
import com.adsamcik.riposte.feature.settings.presentation.duplicatedetection.DuplicateDetectionScreen

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

fun NavController.navigateToLicenses(navOptions: NavOptions? = null) {
    navigate(LicensesRoute, navOptions)
}

fun NavController.navigateToFunStats(navOptions: NavOptions? = null) {
    navigate(FunStatsRoute, navOptions)
}

fun NavController.navigateToDuplicateDetection(navOptions: NavOptions? = null) {
    navigate(DuplicateDetectionRoute, navOptions)
}

fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToFunStats: () -> Unit,
    onNavigateToDuplicateDetection: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToLicenses = onNavigateToLicenses,
            onNavigateToFunStats = onNavigateToFunStats,
            onNavigateToDuplicateDetection = onNavigateToDuplicateDetection,
        )
    }
}

fun NavGraphBuilder.funStatsScreen(onNavigateBack: () -> Unit) {
    composable<FunStatsRoute> {
        FunStatsScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavGraphBuilder.licensesScreen(onNavigateBack: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreen(onNavigateBack = onNavigateBack)
    }
}

fun NavGraphBuilder.duplicateDetectionScreen(onNavigateBack: () -> Unit) {
    composable<DuplicateDetectionRoute> {
        DuplicateDetectionScreen(onNavigateBack = onNavigateBack)
    }
}
