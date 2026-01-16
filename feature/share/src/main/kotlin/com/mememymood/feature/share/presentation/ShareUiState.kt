package com.mememymood.feature.share.presentation

import android.graphics.Bitmap
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.Meme
import com.mememymood.core.model.ShareConfig

data class ShareUiState(
    val meme: Meme? = null,
    val config: ShareConfig = ShareConfig(),
    val originalPreviewBitmap: Bitmap? = null,
    val processedPreviewBitmap: Bitmap? = null,
    val estimatedFileSize: Long = 0,
    val originalFileSize: Long = 0,
    val isProcessing: Boolean = false,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val compressionRatio: Float
        get() = if (originalFileSize > 0) {
            estimatedFileSize.toFloat() / originalFileSize
        } else 0f

    val formattedEstimatedSize: String
        get() = formatFileSize(estimatedFileSize)

    val formattedOriginalSize: String
        get() = formatFileSize(originalFileSize)

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}
