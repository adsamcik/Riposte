package com.mememymood.feature.import_feature.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.mememymood.core.common.navigation.ImportRoute
import com.mememymood.feature.import_feature.presentation.ImportScreen

fun NavController.navigateToImport(navOptions: NavOptions? = null) {
    navigate(ImportRoute, navOptions)
}

fun NavGraphBuilder.importScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    composable<ImportRoute> {
        ImportScreen(
            onNavigateBack = onNavigateBack,
            onImportComplete = onImportComplete
        )
    }
}
