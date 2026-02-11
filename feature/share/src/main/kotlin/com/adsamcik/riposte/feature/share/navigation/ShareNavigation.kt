package com.adsamcik.riposte.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.adsamcik.riposte.core.common.navigation.ShareRoute
import com.adsamcik.riposte.feature.share.presentation.ShareScreen

fun NavController.navigateToShare(
    memeId: Long,
    navOptions: NavOptions? = null,
) {
    navigate(ShareRoute(memeId), navOptions)
}

fun NavGraphBuilder.shareScreen(onNavigateBack: () -> Unit) {
    composable<ShareRoute> { backStackEntry ->
        val route = backStackEntry.toRoute<ShareRoute>()
        ShareScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
