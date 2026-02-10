package com.adsamcik.riposte.feature.gallery.presentation

sealed interface MemeDetailEffect {
    data object NavigateBack : MemeDetailEffect
    data class NavigateToShare(val memeId: Long) : MemeDetailEffect
    data class LaunchQuickShare(val intent: android.content.Intent) : MemeDetailEffect
    data class ShowSnackbar(val message: String) : MemeDetailEffect
    data class ShowError(val message: String) : MemeDetailEffect
    data class NavigateToMeme(val memeId: Long) : MemeDetailEffect
    data class CopyToClipboard(val memeId: Long) : MemeDetailEffect
}
