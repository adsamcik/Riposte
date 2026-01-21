package com.mememymood.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mememymood.core.common.navigation.ShareRoute
import com.mememymood.feature.share.presentation.ShareScreen

// Legacy route constants for backward compatibility
@Deprecated("Use ShareRoute for type-safe navigation")
const val SHARE_ROUTE = "share"
const val SHARE_MEME_ID_ARG = "memeId"

fun NavController.navigateToShare(memeId: Long, navOptions: NavOptions? = null) {
    navigate(ShareRoute(memeId), navOptions)
}

fun NavGraphBuilder.shareScreen(
    onNavigateBack: () -> Unit,
) {
    composable<ShareRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ShareRoute>()
        ShareScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
