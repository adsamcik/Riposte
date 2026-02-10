package com.adsamcik.riposte.feature.import_feature.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.adsamcik.riposte.core.common.navigation.ImportRoute
import com.adsamcik.riposte.feature.import_feature.presentation.ImportScreen

fun NavController.navigateToImport(navOptions: NavOptions? = null) {
    navigate(ImportRoute, navOptions)
}

fun NavGraphBuilder.importScreen(
    onNavigateBack: () -> Unit,
) {
    composable<ImportRoute> {
        ImportScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
