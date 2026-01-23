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

// Legacy route constants for backward compatibility
@Deprecated("Use GalleryRoute object for type-safe navigation")
const val GALLERY_ROUTE = "gallery"
@Deprecated("Use MemeDetailRoute for type-safe navigation")
const val MEME_DETAIL_ROUTE = "gallery/{memeId}"
const val MEME_ID_ARG = "memeId"

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
    onNavigateToSettings: () -> Unit
) {
    composable<GalleryRoute> {
        GalleryScreen(
            onNavigateToMeme = onNavigateToMeme,
            onNavigateToImport = onNavigateToImport,
            onNavigateToSearch = onNavigateToSearch,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

fun NavGraphBuilder.memeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToShare: (Long) -> Unit = {},
) {
    composable<MemeDetailRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<MemeDetailRoute>()
        MemeDetailScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToShare = onNavigateToShare,
        )
    }
}
