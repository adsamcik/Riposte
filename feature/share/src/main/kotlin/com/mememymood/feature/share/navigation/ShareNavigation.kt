package com.mememymood.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mememymood.core.common.navigation.ShareRoute
import com.mememymood.feature.share.presentation.ShareScreen

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
