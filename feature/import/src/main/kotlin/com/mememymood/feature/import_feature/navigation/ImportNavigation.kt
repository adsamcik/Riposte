package com.mememymood.feature.import_feature.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.mememymood.feature.import_feature.presentation.ImportScreen

const val IMPORT_ROUTE = "import"

fun NavController.navigateToImport(navOptions: NavOptions? = null) {
    navigate(IMPORT_ROUTE, navOptions)
}

fun NavGraphBuilder.importScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit
) {
    composable(route = IMPORT_ROUTE) {
        ImportScreen(
            onNavigateBack = onNavigateBack,
            onImportComplete = onImportComplete
        )
    }
}
