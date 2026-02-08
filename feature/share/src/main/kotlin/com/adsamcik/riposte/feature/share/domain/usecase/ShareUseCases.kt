package com.adsamcik.riposte.feature.share.domain.usecase

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.feature.share.domain.repository.ShareRepository
import javax.inject.Inject

/**
 * Aggregated use cases for sharing functionality.
 */
class ShareUseCases @Inject constructor(
    private val repository: ShareRepository,
) {
    /**
     * Get a meme by ID for sharing preview.
     */
    suspend fun getMeme(memeId: Long): Meme? {
        return repository.getMeme(memeId)
    }

    /**
     * Get default share configuration from preferences.
     */
    suspend fun getDefaultConfig(): ShareConfig {
        return repository.getDefaultShareConfig()
    }

    /**
     * Prepare a meme for sharing.
     */
    suspend fun prepareForSharing(meme: Meme, config: ShareConfig): Result<Uri> {
        return repository.prepareForSharing(meme, config)
    }

    /**
     * Create a share intent.
     */
    fun createShareIntent(uri: Uri, mimeType: String): Intent {
        return repository.createShareIntent(uri, mimeType)
    }

    /**
     * Save a meme to the device gallery.
     */
    suspend fun saveToGallery(meme: Meme, config: ShareConfig): Result<Uri> {
        return repository.saveToGallery(meme, config)
    }

    /**
     * Estimate file size after applying share config.
     */
    suspend fun estimateFileSize(meme: Meme, config: ShareConfig): Long {
        return repository.estimateFileSize(meme, config)
    }
}
