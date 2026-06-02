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
     *
     * The returned URI is backed by MediaStore (not FileProvider) so receivers can
     * read it via their own READ_MEDIA_IMAGES permission, avoiding the transient-
     * grant relay issues that crash apps like Discord's React Native ShareActivity.
     */
    suspend fun prepareForSharing(
        meme: Meme,
        config: ShareConfig,
    ): Result<Uri>

    /**
     * Prepare multiple memes for sharing in a single batch. Returns one URI per meme
     * in input order. If any meme fails to process, the entire batch is rolled back
     * (no half-published MediaStore entries are left behind).
     */
    suspend fun prepareMultipleForSharing(
        memes: List<Meme>,
        config: ShareConfig,
    ): Result<List<Uri>>

    /**
     * Bulk-delete every transient share file this app has created in MediaStore.
     * Safe to call from app start, before each new share, or as a periodic backstop.
     * Returns the number of entries removed (0 on failure or when nothing to clean).
     */
    suspend fun cleanupStaleShares(): Int

    /**
     * Create a chooser intent with messaging apps prioritized.
     */
    fun createShareIntent(
        uri: Uri,
        mimeType: String,
    ): Intent

    /**
     * Create a chooser intent for sharing multiple images via ACTION_SEND_MULTIPLE.
     * Used by the gallery's multi-select share flow.
     */
    fun createMultipleShareIntent(
        uris: List<Uri>,
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
