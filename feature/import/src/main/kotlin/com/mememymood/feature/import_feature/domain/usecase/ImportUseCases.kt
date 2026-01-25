package com.mememymood.feature.import_feature.domain.usecase

import android.net.Uri
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.domain.repository.ImportRepository
import javax.inject.Inject

/**
 * Use case for importing a single image.
 */
class ImportImageUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri, metadata: MemeMetadata? = null): Result<Meme> {
        return repository.importImage(uri, metadata)
    }
}

/**
 * Use case for importing multiple images.
 */
class ImportImagesUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uris: List<Uri>): List<Result<Meme>> {
        return repository.importImages(uris)
    }
}

/**
 * Use case for extracting metadata from an image.
 */
class ExtractMetadataUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): MemeMetadata? {
        return repository.extractMetadata(uri)
    }
}

/**
 * Use case for suggesting emojis for an image.
 */
class SuggestEmojisUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): List<EmojiTag> {
        return repository.suggestEmojis(uri)
    }
}

/**
 * Use case for extracting text from an image.
 */
class ExtractTextUseCase @Inject constructor(
    private val repository: ImportRepository
) {
    suspend operator fun invoke(uri: Uri): String? {
        return repository.extractText(uri)
    }
}

/**
 * Result of importing a ZIP bundle.
 *
 * @property successCount Number of successfully imported memes.
 * @property failureCount Number of failed imports.
 * @property importedMemes List of successfully imported memes.
 * @property errors Map of filename to error message for failed imports.
 */
data class ZipImportResult(
    val successCount: Int,
    val failureCount: Int,
    val importedMemes: List<Meme>,
    val errors: Map<String, String>,
)

/**
 * Use case for importing a .meme.zip bundle.
 *
 * Extracts images and their JSON sidecar metadata from a ZIP bundle
 * and imports them into the app.
 */
class ImportZipBundleUseCase @Inject constructor(
    private val repository: ImportRepository,
    private val zipImporter: com.mememymood.feature.import_feature.data.ZipImporter,
) {
    /**
     * Import all images from a .meme.zip bundle.
     *
     * @param zipUri URI pointing to the ZIP file.
     * @return Result containing import statistics and any errors.
     */
    suspend operator fun invoke(zipUri: Uri): ZipImportResult {
        // Check if this is a valid ZIP bundle
        if (!zipImporter.isMemeZipBundle(zipUri)) {
            return ZipImportResult(
                successCount = 0,
                failureCount = 1,
                importedMemes = emptyList(),
                errors = mapOf("bundle" to "Not a valid .meme.zip bundle"),
            )
        }

        // Extract the bundle
        val extractionResult = zipImporter.extractBundle(zipUri)

        // Import each extracted image
        val importedMemes = mutableListOf<Meme>()
        val importErrors = extractionResult.errors.toMutableMap()

        for (extractedMeme in extractionResult.extractedMemes) {
            val result = repository.importImage(
                uri = extractedMeme.imageUri,
                metadata = extractedMeme.metadata,
            )

            result.fold(
                onSuccess = { meme -> importedMemes.add(meme) },
                onFailure = { error ->
                    val fileName = extractedMeme.imageUri.lastPathSegment ?: "unknown"
                    importErrors[fileName] = error.message ?: "Import failed"
                },
            )
        }

        // Clean up extracted files
        zipImporter.cleanupExtractedFiles()

        return ZipImportResult(
            successCount = importedMemes.size,
            failureCount = importErrors.size,
            importedMemes = importedMemes,
            errors = importErrors,
        )
    }
}
