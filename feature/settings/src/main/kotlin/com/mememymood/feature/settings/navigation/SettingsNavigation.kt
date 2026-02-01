package com.mememymood.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.mememymood.core.common.navigation.SettingsRoute
import com.mememymood.feature.settings.presentation.SettingsScreen

fun NavController.navigateToSettings(navOptions: NavOptions? = null) {
    navigate(SettingsRoute, navOptions)
}

fun NavGraphBuilder.settingsScreen(
    onNavigateBack: () -> Unit
) {
    composable<SettingsRoute> {
        SettingsScreen(onNavigateBack = onNavigateBack)
    }
}
