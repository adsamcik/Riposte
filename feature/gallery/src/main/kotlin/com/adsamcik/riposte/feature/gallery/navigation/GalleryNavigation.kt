package com.adsamcik.riposte.feature.gallery.navigation

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.adsamcik.riposte.core.common.navigation.GalleryRoute
import com.adsamcik.riposte.core.common.navigation.MemeDetailRoute
import com.adsamcik.riposte.feature.gallery.presentation.GalleryScreen
import com.adsamcik.riposte.feature.gallery.presentation.MemeDetailScreen

fun NavController.navigateToGallery(navOptions: NavOptions? = null) {
    navigate(GalleryRoute, navOptions)
}

fun NavController.navigateToMemeDetail(
    memeId: Long,
    navOptions: NavOptions? = null,
) {
    navigate(MemeDetailRoute(memeId), navOptions)
}

fun NavGraphBuilder.galleryScreen(
    onNavigateToMeme: (Long) -> Unit,
    onNavigateToImport: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    composable<GalleryRoute> { backStackEntry ->
        val savedStateHandle = backStackEntry.savedStateHandle
        val emojiFilter =
            savedStateHandle.getStateFlow<String?>(EMOJI_FILTER_KEY, null)
                .collectAsStateWithLifecycle()

        GalleryScreen(
            onNavigateToMeme = onNavigateToMeme,
            onNavigateToImport = onNavigateToImport,
            onNavigateToSettings = onNavigateToSettings,
            initialEmojiFilter = emojiFilter.value,
            onEmojiFilterConsumed = { savedStateHandle.remove<String>(EMOJI_FILTER_KEY) },
        )
    }
}

const val EMOJI_FILTER_KEY = "emoji_filter"

fun NavGraphBuilder.memeDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMeme: (Long) -> Unit = {},
    onNavigateToGalleryWithEmoji: (String) -> Unit = {},
) {
    composable<MemeDetailRoute> {
        MemeDetailScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToMeme = onNavigateToMeme,
            onNavigateToGalleryWithEmoji = onNavigateToGalleryWithEmoji,
        )
    }
}
