package com.adsamcik.riposte.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.adsamcik.riposte.core.common.navigation.SettingsRoute
import com.adsamcik.riposte.feature.settings.presentation.SettingsScreen

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

fun NavGraphBuilder.settingsScreen(onNavigateBack: () -> Unit) {
    composable<SettingsRoute> {
        SettingsScreen(onNavigateBack = onNavigateBack)
    }
}
