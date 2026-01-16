package com.mememymood.feature.share.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mememymood.feature.share.presentation.ShareScreen

const val SHARE_ROUTE = "share"
const val SHARE_MEME_ID_ARG = "memeId"

fun NavController.navigateToShare(memeId: Long, navOptions: NavOptions? = null) {
    navigate("$SHARE_ROUTE/$memeId", navOptions)
}

fun NavGraphBuilder.shareScreen(
    onNavigateBack: () -> Unit,
) {
    composable(
        route = "$SHARE_ROUTE/{$SHARE_MEME_ID_ARG}",
        arguments = listOf(
            navArgument(SHARE_MEME_ID_ARG) { type = NavType.LongType }
        )
    ) {
        ShareScreen(
            onNavigateBack = onNavigateBack,
        )
    }
}
