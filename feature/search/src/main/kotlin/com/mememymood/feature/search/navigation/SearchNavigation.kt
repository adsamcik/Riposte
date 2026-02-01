package com.mememymood.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.mememymood.core.common.navigation.SearchRoute
import com.mememymood.feature.search.presentation.SearchScreen

fun NavController.navigateToSearch(navOptions: NavOptions? = null) {
    navigate(SearchRoute, navOptions)
}

fun NavGraphBuilder.searchScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit,
    onStartVoiceSearch: (() -> Unit)? = null,
) {
    composable<SearchRoute> {
        SearchScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMeme = onNavigateToMeme,
            onStartVoiceSearch = onStartVoiceSearch,
        )
    }
}
