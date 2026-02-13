package com.adsamcik.riposte.feature.gallery.presentation

sealed interface MemeDetailEffect {
    data object NavigateBack : MemeDetailEffect

    data class NavigateToShare(val memeId: Long) : MemeDetailEffect

    data class ShowSnackbar(val message: String) : MemeDetailEffect

    data class ShowError(val message: String) : MemeDetailEffect

    data class NavigateToMeme(val memeId: Long) : MemeDetailEffect

    data class NavigateToGalleryWithEmoji(val emoji: String) : MemeDetailEffect
}
