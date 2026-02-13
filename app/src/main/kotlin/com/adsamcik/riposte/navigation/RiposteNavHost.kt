package com.adsamcik.riposte.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.adsamcik.riposte.core.common.navigation.GalleryRoute
import com.adsamcik.riposte.core.common.navigation.MemeDetailRoute
import com.adsamcik.riposte.feature.gallery.navigation.EMOJI_FILTER_KEY
import com.adsamcik.riposte.feature.gallery.navigation.galleryScreen
import com.adsamcik.riposte.feature.gallery.navigation.memeDetailScreen
import com.adsamcik.riposte.feature.gallery.navigation.navigateToMemeDetail
import com.adsamcik.riposte.feature.import_feature.navigation.importScreen
import com.adsamcik.riposte.feature.import_feature.navigation.navigateToImport
import com.adsamcik.riposte.feature.settings.navigation.navigateToSettings
import com.adsamcik.riposte.feature.settings.navigation.settingsScreen
import com.adsamcik.riposte.feature.share.navigation.navigateToShare
import com.adsamcik.riposte.feature.share.navigation.shareScreen

/**
 * Main navigation host for Riposte.
 * Defines all navigation routes and their connections.
 * Uses type-safe navigation with Kotlin serialization.
 */
@Composable
fun RiposteNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: Any = GalleryRoute,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        // Gallery screen - main screen (includes inline search)
        galleryScreen(
            onNavigateToMeme = { memeId ->
                navController.navigateToMemeDetail(memeId)
            },
            onNavigateToImport = {
                navController.navigateToImport()
            },
            onNavigateToSettings = {
                navController.navigateToSettings()
            },
            onNavigateToShare = { memeId ->
                navController.navigateToShare(memeId)
            },
        )

        // Meme detail screen
        memeDetailScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToShare = { memeId ->
                navController.navigateToShare(memeId)
            },
            onNavigateToMeme = { memeId ->
                navController.navigateToMemeDetail(
                    memeId,
                    navOptions {
                        launchSingleTop = true
                        popUpTo<MemeDetailRoute> { inclusive = true }
                    },
                )
            },
            onNavigateToGalleryWithEmoji = { emoji ->
                navController.getBackStackEntry(GalleryRoute)
                    .savedStateHandle[EMOJI_FILTER_KEY] = emoji
                navController.popBackStack(GalleryRoute, inclusive = false)
            },
        )

        // Share screen
        shareScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
        )

        // Import screen
        importScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
        )

        // Settings screen
        settingsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
        )
    }
}
