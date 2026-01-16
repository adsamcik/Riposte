package com.mememymood.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.mememymood.feature.gallery.navigation.GALLERY_ROUTE
import com.mememymood.feature.gallery.navigation.galleryScreen
import com.mememymood.feature.gallery.navigation.memeDetailScreen
import com.mememymood.feature.gallery.navigation.navigateToMemeDetail
import com.mememymood.feature.import_feature.navigation.importScreen
import com.mememymood.feature.import_feature.navigation.navigateToImport
import com.mememymood.feature.search.navigation.navigateToSearch
import com.mememymood.feature.search.navigation.searchScreen
import com.mememymood.feature.settings.navigation.navigateToSettings
import com.mememymood.feature.settings.navigation.settingsScreen
import com.mememymood.feature.share.navigation.navigateToShare
import com.mememymood.feature.share.navigation.shareScreen

/**
 * Main navigation host for Meme My Mood.
 * Defines all navigation routes and their connections.
 */
@Composable
fun MemeMoodNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = GALLERY_ROUTE
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Gallery screen - main screen
        galleryScreen(
            onNavigateToMeme = { memeId ->
                navController.navigateToMemeDetail(memeId)
            },
            onNavigateToImport = {
                navController.navigateToImport()
            },
            onNavigateToSearch = {
                navController.navigateToSearch()
            },
            onNavigateToSettings = {
                navController.navigateToSettings()
            }
        )

        // Meme detail screen
        memeDetailScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToShare = { memeId ->
                navController.navigateToShare(memeId)
            }
        )

        // Import screen
        importScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onImportComplete = {
                navController.popBackStack()
            }
        )

        // Search screen
        searchScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMeme = { memeId ->
                navController.navigateToMemeDetail(memeId)
            }
        )

        // Share screen
        shareScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Settings screen
        settingsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}
