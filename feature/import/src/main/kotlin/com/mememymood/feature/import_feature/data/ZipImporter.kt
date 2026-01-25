package com.mememymood.feature.import_feature.data

import android.content.Context
import android.net.Uri
import com.mememymood.core.model.MemeMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Result of extracting a meme bundle from a ZIP file.
 *
 * @property imageUri URI pointing to the extracted image file.
 * @property metadata Pre-generated metadata from the JSON sidecar, if present.
 */
data class ExtractedMeme(
    val imageUri: Uri,
    val metadata: MemeMetadata?,
)

/**
 * Result of processing a .meme.zip bundle.
 *
 * @property extractedMemes Successfully extracted images with their metadata.
 * @property errors Errors encountered during extraction, keyed by entry name.
 */
data class ZipExtractionResult(
    val extractedMemes: List<ExtractedMeme>,
    val errors: Map<String, String>,
)

/**
 * Handles extraction and processing of .meme.zip bundles created by the meme-my-mood-cli.
 *
 * ZIP bundle format:
 * - `image.jpg` - Image file
 * - `image.jpg.json` - JSON sidecar with metadata for the image
 *
 * The JSON sidecar follows the MemeMetadata schema:
 * ```json
 * {
 *   "schemaVersion": "1.0",
 *   "emojis": ["😂", "🔥"],
 *   "title": "Meme title",
 *   "description": "Meme description",
 *   "tags": ["funny", "programming"],
 *   "textContent": "Extracted text from image"
 * }
 * ```
 */
class ZipImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val extractDir: File by lazy {
        File(context.cacheDir, "zip_extract").also { it.mkdirs() }
    }

    companion object {
        private const val MEME_ZIP_EXTENSION = ".meme.zip"
        private val SUPPORTED_IMAGE_EXTENSIONS = setOf(
            ".jpg", ".jpeg", ".png", ".webp", ".gif",
            ".bmp", ".tiff", ".tif", ".heic", ".heif",
            ".avif", ".jxl",
        )
    }

    /**
     * Check if a URI points to a .meme.zip bundle.
     *
     * @param uri URI to check.
     * @return True if the URI appears to be a meme bundle.
     */
    fun isMemeZipBundle(uri: Uri): Boolean {
        val fileName = getFileNameFromUri(uri)?.lowercase() ?: return false
        return fileName.endsWith(MEME_ZIP_EXTENSION) ||
            (fileName.endsWith(".zip") && context.contentResolver.getType(uri) == "application/zip")
    }

    /**
     * Extract images and metadata from a .meme.zip bundle.
     *
     * @param zipUri URI pointing to the ZIP file.
     * @return Extraction result with images and any errors.
     */
    suspend fun extractBundle(zipUri: Uri): ZipExtractionResult = withContext(Dispatchers.IO) {
        val extractedMemes = mutableListOf<ExtractedMeme>()
        val errors = mutableMapOf<String, String>()

        // Maps image filename -> extracted file path
        val extractedImages = mutableMapOf<String, File>()
        // Maps image filename -> parsed metadata
        val metadataMap = mutableMapOf<String, MemeMetadata>()

        // Clear previous extraction
        extractDir.listFiles()?.forEach { it.delete() }

        try {
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val entryName = entry.name

                        // Skip directories and hidden files
                        if (entry.isDirectory || entryName.startsWith(".") || entryName.contains("/")) {
                            zipInput.closeEntry()
                            entry = zipInput.nextEntry
                            continue
                        }

                        try {
                            when {
                                // JSON sidecar file (e.g., "image.jpg.json")
                                entryName.endsWith(".json") -> {
                                    val imageFileName = entryName.removeSuffix(".json")
                                    val content = zipInput.readBytes().decodeToString()
                                    val metadata = parseMetadataJson(content)
                                    if (metadata != null) {
                                        metadataMap[imageFileName] = metadata
                                    }
                                }

                                // Image file
                                isImageFile(entryName) -> {
                                    val outputFile = File(extractDir, entryName)
                                    FileOutputStream(outputFile).use { output ->
                                        zipInput.copyTo(output)
                                    }
                                    extractedImages[entryName] = outputFile
                                }
                            }
                        } catch (e: Exception) {
                            errors[entryName] = e.message ?: "Unknown error"
                        }

                        zipInput.closeEntry()
                        entry = zipInput.nextEntry
                    }
                }
            } ?: run {
                errors["bundle"] = "Could not open ZIP file"
            }
        } catch (e: Exception) {
            errors["bundle"] = "Failed to extract ZIP: ${e.message}"
        }

        // Pair images with their metadata
        for ((imageName, imageFile) in extractedImages) {
            val metadata = metadataMap[imageName]
            extractedMemes.add(
                ExtractedMeme(
                    imageUri = Uri.fromFile(imageFile),
                    metadata = metadata,
                ),
            )
        }

        ZipExtractionResult(
            extractedMemes = extractedMemes,
            errors = errors,
        )
    }

    /**
     * Clean up extracted files after import is complete.
     */
    fun cleanupExtractedFiles() {
        extractDir.listFiles()?.forEach { it.delete() }
    }

    private fun isImageFile(fileName: String): Boolean {
        val lowerName = fileName.lowercase()
        return SUPPORTED_IMAGE_EXTENSIONS.any { lowerName.endsWith(it) }
    }

    private fun parseMetadataJson(content: String): MemeMetadata? {
        return try {
            json.decodeFromString<MemeMetadata>(content)
        } catch (e: Exception) {
            null
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex)
                    } else {
                        null
                    }
                }
            }
            "file" -> uri.lastPathSegment
            else -> uri.lastPathSegment
        }
    }
}
