package com.adsamcik.riposte.feature.share.presentation

import com.adsamcik.riposte.core.model.ImageFormat

sealed interface ShareIntent {
    data class SetFormat(val format: ImageFormat) : ShareIntent
    data class SetQuality(val quality: Int) : ShareIntent
    data class SetMaxDimension(val dimension: Int) : ShareIntent
    data class SetStripMetadata(val strip: Boolean) : ShareIntent
    data object Share : ShareIntent
    data object SaveToGallery : ShareIntent
    data object RefreshPreview : ShareIntent
    data object NavigateBack : ShareIntent
}
