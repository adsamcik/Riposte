package com.mememymood.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.mememymood.feature.search.presentation.SearchScreen

const val SEARCH_ROUTE = "search"

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(SEARCH_ROUTE, navOptions)
}

fun NavGraphBuilder.searchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit
) {
    composable(route = SEARCH_ROUTE) {
        SearchScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMeme = onNavigateToMeme
        )
    }
}
