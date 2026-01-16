package com.mememymood.feature.import_feature.presentation

/**
 * One-time side effects for the Import screen.
 */
sealed interface ImportEffect {
    /**
     * Open image picker.
     */
    data object OpenImagePicker : ImportEffect

    /**
     * Import completed successfully.
     */
    data class ImportComplete(val count: Int) : ImportEffect

    /**
     * Navigate back to gallery.
     */
    data object NavigateToGallery : ImportEffect

    /**
     * Show snackbar message.
     */
    data class ShowSnackbar(val message: String) : ImportEffect

    /**
     * Show error message.
     */
    data class ShowError(val message: String) : ImportEffect
}
