package com.adsamcik.riposte.feature.share.presentation

sealed interface ShareIntent {
    data object Share : ShareIntent

    data object SaveToGallery : ShareIntent

    data object NavigateBack : ShareIntent
}
