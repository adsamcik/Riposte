package com.mememymood.feature.gallery.presentation

sealed interface MemeDetailEffect {
    data object NavigateBack : MemeDetailEffect
    data class NavigateToShare(val memeId: Long) : MemeDetailEffect
    data class ShowSnackbar(val message: String) : MemeDetailEffect
    data class ShowError(val message: String) : MemeDetailEffect
}
