package com.adsamcik.riposte.feature.share.presentation

import android.content.Intent
import android.net.Uri

sealed interface ShareEffect {
    data class LaunchShareIntent(val intent: Intent) : ShareEffect

    data class ShowSnackbar(val message: String) : ShareEffect

    data class SavedToGallery(val uri: Uri) : ShareEffect

    data object NavigateBack : ShareEffect

    data class ShowError(val message: String) : ShareEffect
}
