package com.mememymood.feature.gallery.presentation

import android.content.Intent

sealed interface MemeDetailEffect {
    data object NavigateBack : MemeDetailEffect
    data class LaunchShareIntent(val intent: Intent) : MemeDetailEffect
    data class ShowSnackbar(val message: String) : MemeDetailEffect
    data class ShowError(val message: String) : MemeDetailEffect
}
