package com.adsamcik.riposte.feature.import_feature.domain.repository

import android.net.Uri
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.model.ImportRequestItemData

/**
 * Repository interface for importing memes.
 */
interface ImportRepository {
    /**
     * Import a single image from URI.
     *
     * @param uri The content URI of the image to import.
     * @param metadata Optional metadata to associate with the meme.
     * @return The imported meme or an error.
     */
    suspend fun importImage(
        uri: Uri,
        metadata: MemeMetadata? = null,
    ): Result<Meme>

    /**
     * Import multiple images.
     *
     * @param uris List of content URIs to import.
     * @return List of results for each import.
     */
    suspend fun importImages(uris: List<Uri>): List<Result<Meme>>

    /**
     * Extract metadata from an image (if present).
     *
     * @param uri The content URI of the image.
     * @return Extracted metadata or null if none found.
     */
    suspend fun extractMetadata(uri: Uri): MemeMetadata?

    /**
     * Extract text from an image using OCR.
     *
     * @param uri The content URI of the image.
     * @return Extracted text or null if none found.
     */
    suspend fun extractText(uri: Uri): String?

    /**
     * Suggest emojis for an image based on its content.
     *
     * @param uri The content URI of the image.
     * @return List of suggested emoji tags.
     */
    suspend fun suggestEmojis(uri: Uri): List<EmojiTag>

    /**
     * Check if an image already exists in the database.
     */
    suspend fun isDuplicate(uri: Uri): Boolean

    /**
     * Find the existing meme ID for a duplicate image, or null if not a duplicate.
     */
    suspend fun findDuplicateMemeId(uri: Uri): Long?

    /**
     * Update metadata (title, description, emojis, text content) for an existing meme.
     */
    suspend fun updateMemeMetadata(
        memeId: Long,
        metadata: MemeMetadata,
    ): Result<Unit>

    /**
     * Create an import request record for WorkManager-based background import.
     *
     * @param id Unique request identifier.
     * @param imageCount Number of images in this request.
     * @param stagingDir Absolute path to the staging directory.
     */
    suspend fun createImportRequest(
        id: String,
        imageCount: Int,
        stagingDir: String,
    )

    /**
     * Create import request items for a previously created request.
     *
     * @param requestId The import request ID these items belong to.
     * @param items Domain-level representations of each item.
     */
    suspend fun createImportRequestItems(
        requestId: String,
        items: List<ImportRequestItemData>,
    )
}
