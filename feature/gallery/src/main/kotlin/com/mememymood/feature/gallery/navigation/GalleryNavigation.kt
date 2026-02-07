package com.mememymood.feature.gallery.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mememymood.core.common.navigation.GalleryRoute
import com.mememymood.core.common.navigation.MemeDetailRoute
import com.mememymood.feature.gallery.presentation.GalleryScreen
import com.mememymood.feature.gallery.presentation.MemeDetailScreen

fun NavController.navigateToGallery(navOptions: NavOptions? = null) {
    navigate(GalleryRoute, navOptions)
}

fun NavController.navigateToMemeDetail(memeId: Long, navOptions: NavOptions? = null) {
    navigate(MemeDetailRoute(memeId), navOptions)
}

fun NavGraphBuilder.galleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToShare: (Long) -> Unit,
) {
    composable<GalleryRoute> {
        GalleryScreen(
            onNavigateToMeme = onNavigateToMeme,
            onNavigateToImport = onNavigateToImport,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToShare = onNavigateToShare,
        )
    }
}

fun NavGraphBuilder.memeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (Long) -> Unit = {},
    onNavigateToMeme: (Long) -> Unit = {},
) {
    composable<MemeDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<MemeDetailRoute>()
        MemeDetailScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToShare = onNavigateToShare,
            onNavigateToMeme = onNavigateToMeme,
        )
    }
}
