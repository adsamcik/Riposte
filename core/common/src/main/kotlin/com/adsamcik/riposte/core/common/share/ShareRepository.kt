package com.adsamcik.riposte.core.common.share

import android.content.Intent
import android.net.Uri
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig

/**
 * Repository interface for sharing memes.
 * Defined in core/common so it can be consumed by any feature module.
 * Implementation lives in feature/share.
 */
interface ShareRepository {
    suspend fun getMeme(memeId: Long): Meme?

    suspend fun getDefaultShareConfig(): ShareConfig

    /**
     * Prepare a meme for sharing by processing the image and creating a content URI.
     */
    suspend fun prepareForSharing(
        meme: Meme,
        config: ShareConfig,
    ): Result<Uri>

    /**
     * Create a chooser intent with messaging apps prioritized.
     */
    fun createShareIntent(
        uri: Uri,
        mimeType: String,
    ): Intent

    /**
     * Save a meme to the device gallery.
     */
    suspend fun saveToGallery(
        meme: Meme,
        config: ShareConfig,
    ): Result<Uri>

    /**
     * Estimate the file size after applying share config.
     */
    suspend fun estimateFileSize(
        meme: Meme,
        config: ShareConfig,
    ): Long
}
