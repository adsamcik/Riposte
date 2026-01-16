package com.mememymood.feature.gallery.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mememymood.feature.gallery.presentation.GalleryScreen
import com.mememymood.feature.gallery.presentation.MemeDetailScreen

const val GALLERY_ROUTE = "gallery"
const val MEME_DETAIL_ROUTE = "gallery/{memeId}"
const val MEME_ID_ARG = "memeId"

fun NavController.navigateToGallery(navOptions: NavOptions? = null) {
    navigate(GALLERY_ROUTE, navOptions)
}

fun NavController.navigateToMemeDetail(memeId: Long, navOptions: NavOptions? = null) {
    navigate("gallery/$memeId", navOptions)
}

fun NavGraphBuilder.galleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    composable(route = GALLERY_ROUTE) {
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
    onNavigateToShare: (Long) -> Unit
) {
    composable(
        route = MEME_DETAIL_ROUTE,
        arguments = listOf(
            navArgument(MEME_ID_ARG) { type = NavType.LongType }
        )
    ) {
        MemeDetailScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToShare = onNavigateToShare
        )
    }
}
