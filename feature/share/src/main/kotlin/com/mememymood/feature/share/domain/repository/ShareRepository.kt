package com.mememymood.feature.share.domain.repository

import android.content.Intent
import android.net.Uri
import com.mememymood.core.model.Meme
import com.mememymood.core.model.ShareConfig

/**
 * Repository interface for sharing memes.
 */
interface ShareRepository {

    /**
     * Get a meme by ID for preview.
     */
    suspend fun getMeme(memeId: Long): Meme?

    /**
     * Get the default share configuration from user preferences.
     */
    suspend fun getDefaultShareConfig(): ShareConfig

    /**
     * Prepare a meme for sharing by applying the share configuration.
     * 
     * @param meme The meme to share.
     * @param config The sharing configuration.
     * @return A content URI that can be shared with other apps.
     */
    suspend fun prepareForSharing(meme: Meme, config: ShareConfig): Result<Uri>

    /**
     * Create a share intent for the given URI.
     */
    fun createShareIntent(uri: Uri, mimeType: String): Intent

    /**
     * Save a meme to the device gallery.
     */
    suspend fun saveToGallery(meme: Meme, config: ShareConfig): Result<Uri>

    /**
     * Estimate the file size after applying share config.
     * 
     * @param meme The meme.
     * @param config The sharing configuration.
     * @return Estimated file size in bytes.
     */
    suspend fun estimateFileSize(meme: Meme, config: ShareConfig): Long
}
