package com.mememymood.feature.import_feature.domain

import android.net.Uri
import com.mememymood.feature.import_feature.data.ExtractedMeme
import com.mememymood.feature.import_feature.data.ZipExtractionEvent
import com.mememymood.feature.import_feature.data.ZipExtractionResult
import kotlinx.coroutines.flow.Flow

/**
 * Interface for extracting memes from .meme.zip bundles.
 */
interface ZipImporter {

    /**
     * Check if a URI points to a .meme.zip bundle.
     */
    fun isMemeZipBundle(uri: Uri): Boolean

    /**
     * Extract images and metadata from a .meme.zip bundle.
     */
    suspend fun extractBundle(zipUri: Uri): ZipExtractionResult

    /**
     * Extract images and metadata from a .meme.zip bundle as a streaming Flow.
     */
    fun extractBundleStream(zipUri: Uri): Flow<ZipExtractionEvent>

    /**
     * Clean up extracted files after import is complete.
     */
    fun cleanupExtractedFiles()
}
