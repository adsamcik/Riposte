package com.adsamcik.riposte.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.adsamcik.riposte.core.common.navigation.LicensesRoute
import com.adsamcik.riposte.core.common.navigation.SettingsRoute
import com.adsamcik.riposte.feature.settings.presentation.LicensesScreen
import com.adsamcik.riposte.feature.settings.presentation.SettingsScreen

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

fun NavController.navigateToLicenses(navOptions: NavOptions? = null) {
    navigate(LicensesRoute, navOptions)
}

fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
) {
    composable<SettingsRoute> {
        SettingsScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToLicenses = onNavigateToLicenses,
        )
    }
}

fun NavGraphBuilder.licensesScreen(onNavigateBack: () -> Unit) {
    composable<LicensesRoute> {
        LicensesScreen(onNavigateBack = onNavigateBack)
    }
}
