package com.adsamcik.riposte.feature.import_feature.data

import android.content.Context
import android.net.Uri
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.ZipImporter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
 * Events emitted during streaming ZIP extraction.
 */
sealed interface ZipExtractionEvent {
    /**
     * Progress update with count of processed entries.
     * @property processed Number of entries processed so far.
     * @property currentEntry Name of the entry being processed.
     */
    data class Progress(val processed: Int, val currentEntry: String) : ZipExtractionEvent

    /**
     * A meme was successfully extracted and is ready for import.
     * @property extractedMeme The extracted meme data.
     * @property tempFile The temporary file that should be deleted after import.
     */
    data class MemeExtracted(
        val extractedMeme: ExtractedMeme,
        val tempFile: File,
    ) : ZipExtractionEvent

    /**
     * Error occurred processing a specific entry.
     * @property entryName Name of the ZIP entry that failed.
     * @property message Error description.
     */
    data class Error(val entryName: String, val message: String) : ZipExtractionEvent

    /**
     * Extraction completed.
     * @property totalProcessed Total number of entries processed.
     * @property totalErrors Number of errors encountered.
     */
    data class Complete(val totalProcessed: Int, val totalErrors: Int) : ZipExtractionEvent
}

/**
 * Handles extraction and processing of .meme.zip bundles created by the riposte-cli.
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
class DefaultZipImporter
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ZipImporter {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }

        private val extractDir: File
            get() = File(context.cacheDir, "zip_extract").also { it.mkdirs() }

        companion object {
            private const val MEME_ZIP_EXTENSION = ".meme.zip"
            private val SUPPORTED_IMAGE_EXTENSIONS =
                setOf(
                    ".jpg", ".jpeg", ".png", ".webp", ".gif",
                    ".bmp", ".tiff", ".tif", ".heic", ".heif",
                    ".avif", ".jxl",
                )

            /**
             * Maximum number of entries allowed in a ZIP file (ZIP bomb protection).
             */
            const val MAX_ENTRY_COUNT = 10_000

            private const val BYTES_PER_KB = 1024
            private const val IO_BUFFER_SIZE = 8192

            /**
             * Maximum size for a single extracted file (50 MB).
             */
            const val MAX_SINGLE_FILE_SIZE = 50L * BYTES_PER_KB * BYTES_PER_KB

            /**
             * Maximum size for a JSON sidecar file (1 MB).
             */
            const val MAX_JSON_SIZE = 1L * BYTES_PER_KB * BYTES_PER_KB
        }

        /**
         * Check if a URI points to a .meme.zip bundle.
         *
         * @param uri URI to check.
         * @return True if the URI appears to be a meme bundle.
         */
        override fun isMemeZipBundle(uri: Uri): Boolean {
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
        @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
        override suspend fun extractBundle(zipUri: Uri): ZipExtractionResult =
            withContext(Dispatchers.IO) {
                Timber.d("extractBundle: starting for URI=%s", zipUri)
                val errors = mutableMapOf<String, String>()

                // Maps image filename -> extracted file path
                val extractedImages = mutableMapOf<String, File>()
                // Maps image filename -> parsed metadata
                val metadataMap = mutableMapOf<String, MemeMetadata>()

                // Clear previous extraction
                val existingFiles = extractDir.listFiles()
                Timber.d(
                    "extractBundle: clearing %d existing files from %s",
                    existingFiles?.size ?: 0, extractDir.absolutePath,
                )
                existingFiles?.forEach { it.delete() }

                try {
                    context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                        Timber.d("extractBundle: opened input stream, class=%s", inputStream.javaClass.simpleName)
                        ZipInputStream(inputStream).use { zipInput ->
                            var entryCount = 0
                            var entry = zipInput.nextEntry
                            while (entry != null) {
                                entryCount++

                                // ZIP bomb protection: too many entries
                                if (entryCount > MAX_ENTRY_COUNT) {
                                    errors["bundle"] = "Too many entries in ZIP (limit: $MAX_ENTRY_COUNT)"
                                    break
                                }

                                val entryName = entry.name
                                Timber.d("extractBundle: entry #%d name='%s' size=%d compressed=%d isDir=%b",
                                    entryCount, entryName, entry.size, entry.compressedSize, entry.isDirectory)

                                // Skip directories and hidden files
                                if (entry.isDirectory || entryName.startsWith(".") || entryName.contains("/")) {
                                    Timber.d("extractBundle: SKIPPING entry (dir=%b, dotfile=%b, hasSlash=%b)",
                                        entry.isDirectory, entryName.startsWith("."), entryName.contains("/"))
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }

                                try {
                                    processZipEntry(entryName, entry, zipInput, extractedImages, metadataMap, errors)
                                } catch (
                                    @Suppress("TooGenericExceptionCaught") // I/O + parsing may throw various exceptions
                                    e: Exception,
                                ) {
                                    errors[entryName] = e.message ?: "Unknown error"
                                }

                                zipInput.closeEntry()
                                entry = zipInput.nextEntry
                            }
                            Timber.d("extractBundle: finished iterating, entryCount=%d", entryCount)
                        }
                    } ?: run {
                        errors["bundle"] = "Could not open ZIP file"
                    }
                } catch (
                    // ZIP extraction can throw IO, Security, and other exceptions
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "extractBundle: exception during extraction")
                    errors["bundle"] = "Failed to extract ZIP: ${e.message}"
                }

                Timber.d(
                    "extractBundle: extracted %d images, %d metadata, %d errors",
                    extractedImages.size, metadataMap.size, errors.size,
                )

                ZipExtractionResult(
                    extractedMemes = pairImagesWithMetadata(extractedImages, metadataMap),
                    errors = errors,
                )
            }

        /**
         * Extract images and metadata from a .meme.zip bundle as a streaming Flow.
         *
         * Each meme is emitted as soon as it's extracted, allowing the consumer to:
         * - Import to database immediately
         * - Delete temp files after import
         * - Update UI progress incrementally
         *
         * @param zipUri URI pointing to the ZIP file.
         * @return Flow of extraction events.
         */
        @Suppress("CyclomaticComplexMethod", "LongMethod", "LoopWithTooManyJumpStatements")
        override fun extractBundleStream(zipUri: Uri): Flow<ZipExtractionEvent> =
            flow {
                var processedCount = 0
                var errorCount = 0

                // Maps image filename -> parsed metadata (for JSON arriving before image)
                val pendingMetadata = mutableMapOf<String, MemeMetadata>()
                // Track which images have been emitted (for JSON arriving after image)
                val emittedImages = mutableSetOf<String>()

                try {
                    context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zipInput ->
                            var entryCount = 0
                            var entry = zipInput.nextEntry

                            while (entry != null) {
                                entryCount++

                                // ZIP bomb protection
                                if (entryCount > MAX_ENTRY_COUNT) {
                                    emit(
                                        ZipExtractionEvent.Error(
                                            "bundle",
                                            "Too many entries in ZIP (limit: $MAX_ENTRY_COUNT)",
                                        ),
                                    )
                                    errorCount++
                                    break
                                }

                                val entryName = entry.name
                                emit(ZipExtractionEvent.Progress(processedCount, entryName))

                                // Skip directories and hidden files
                                if (entry.isDirectory || entryName.startsWith(".") || entryName.contains("/")) {
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }

                                try {
                                    val events = processStreamZipEntry(
                                        entryName, entry, zipInput, pendingMetadata, emittedImages,
                                    )
                                    for (event in events) {
                                        emit(event)
                                        when (event) {
                                            is ZipExtractionEvent.MemeExtracted -> processedCount++
                                            is ZipExtractionEvent.Error -> errorCount++
                                            else -> {}
                                        }
                                    }
                                } catch (
                                    @Suppress("TooGenericExceptionCaught") // I/O + parsing may throw various exceptions
                                    e: Exception,
                                ) {
                                    emit(ZipExtractionEvent.Error(entryName, e.message ?: "Unknown error"))
                                    errorCount++
                                }

                                zipInput.closeEntry()
                                entry = zipInput.nextEntry
                            }
                        }
                    } ?: run {
                        emit(ZipExtractionEvent.Error("bundle", "Could not open ZIP file"))
                        errorCount++
                    }
                } catch (
                    // ZIP extraction can throw IO, Security, and other exceptions
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    emit(ZipExtractionEvent.Error("bundle", "Failed to extract ZIP: ${e.message}"))
                    errorCount++
                }

                emit(ZipExtractionEvent.Complete(processedCount, errorCount))
            }.flowOn(Dispatchers.IO)

        /**
         * Clean up extracted files after import is complete.
         */
        override fun cleanupExtractedFiles() {
            extractDir.deleteRecursively()
        }

        /**
         * Process a single ZIP entry for batch extraction, dispatching to JSON or image handling.
         */
        @Suppress("LongParameterList", "NestedBlockDepth")
        private fun processZipEntry(
            entryName: String,
            entry: java.util.zip.ZipEntry,
            zipInput: ZipInputStream,
            extractedImages: MutableMap<String, File>,
            metadataMap: MutableMap<String, MemeMetadata>,
            errors: MutableMap<String, String>,
        ) {
            when {
                entryName.endsWith(".json") -> {
                    val declaredSize = entry.size
                    if (declaredSize > MAX_JSON_SIZE) {
                        errors[entryName] =
                            "JSON size limit exceeded (max: ${MAX_JSON_SIZE / BYTES_PER_KB}KB)"
                    } else {
                        val imageFileName = getSafeFileName(entryName.removeSuffix(".json"))
                        if (imageFileName != null) {
                            val content =
                                readBytesWithLimit(zipInput, MAX_JSON_SIZE, entryName, errors)
                            if (content != null) {
                                val metadata = parseMetadataJson(content.decodeToString())
                                if (metadata != null) {
                                    metadataMap[imageFileName] = metadata
                                }
                            }
                        }
                    }
                }

                isImageFile(entryName) -> {
                    val declaredSize = entry.size
                    if (declaredSize > MAX_SINGLE_FILE_SIZE) {
                        val maxMb = MAX_SINGLE_FILE_SIZE / BYTES_PER_KB / BYTES_PER_KB
                        errors[entryName] =
                            "File size limit exceeded (max: ${maxMb}MB)"
                    } else {
                        val safeFileName = getSafeFileName(entryName)
                        if (safeFileName == null) {
                            errors[entryName] = "Path traversal attempt blocked"
                        } else {
                            val outputFile = getSafeOutputFile(safeFileName)
                            if (outputFile == null) {
                                errors[entryName] = "Path traversal attempt blocked"
                            } else {
                                val written =
                                    copyWithLimit(zipInput, outputFile, MAX_SINGLE_FILE_SIZE)
                                Timber.d("extractBundle: image '%s' -> '%s' wrote %d bytes",
                                    entryName, outputFile.name, written)
                                if (written < 0) {
                                    val maxMb =
                                        MAX_SINGLE_FILE_SIZE / BYTES_PER_KB / BYTES_PER_KB
                                    errors[entryName] =
                                        "File size limit exceeded (max: ${maxMb}MB)"
                                    outputFile.delete()
                                } else {
                                    extractedImages[safeFileName] = outputFile
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * Pair extracted image files with their parsed metadata sidecars.
         */
        private fun pairImagesWithMetadata(
            extractedImages: Map<String, File>,
            metadataMap: Map<String, MemeMetadata>,
        ): List<ExtractedMeme> =
            extractedImages.map { (imageName, imageFile) ->
                ExtractedMeme(
                    imageUri = Uri.fromFile(imageFile),
                    metadata = metadataMap[imageName],
                )
            }

        /**
         * Process a single ZIP entry for streaming extraction, returning events to emit.
         */
        @Suppress("LongMethod", "NestedBlockDepth")
        private fun processStreamZipEntry(
            entryName: String,
            entry: java.util.zip.ZipEntry,
            zipInput: ZipInputStream,
            pendingMetadata: MutableMap<String, MemeMetadata>,
            emittedImages: MutableSet<String>,
        ): List<ZipExtractionEvent> {
            val events = mutableListOf<ZipExtractionEvent>()
            when {
                entryName.endsWith(".json") -> {
                    val declaredSize = entry.size
                    if (declaredSize > MAX_JSON_SIZE) {
                        events.add(ZipExtractionEvent.Error(entryName, "JSON size limit exceeded"))
                    } else {
                        val imageFileName = getSafeFileName(entryName.removeSuffix(".json"))
                        if (imageFileName != null) {
                            val content = readBytesWithLimitStream(zipInput, MAX_JSON_SIZE)
                            if (content != null) {
                                val metadata = parseMetadataJson(content.decodeToString())
                                if (metadata != null) {
                                    pendingMetadata[imageFileName] = metadata
                                }
                            } else {
                                events.add(
                                    ZipExtractionEvent.Error(entryName, "JSON size limit exceeded"),
                                )
                            }
                        }
                    }
                }

                isImageFile(entryName) -> {
                    val declaredSize = entry.size
                    if (declaredSize > MAX_SINGLE_FILE_SIZE) {
                        events.add(ZipExtractionEvent.Error(entryName, "File size limit exceeded"))
                    } else {
                        val safeFileName = getSafeFileName(entryName)
                        if (safeFileName == null) {
                            events.add(
                                ZipExtractionEvent.Error(entryName, "Path traversal attempt blocked"),
                            )
                        } else {
                            val outputFile = getSafeOutputFile(safeFileName)
                            if (outputFile == null) {
                                events.add(
                                    ZipExtractionEvent.Error(entryName, "Path traversal attempt blocked"),
                                )
                            } else {
                                val written =
                                    copyWithLimit(zipInput, outputFile, MAX_SINGLE_FILE_SIZE)
                                if (written < 0) {
                                    events.add(
                                        ZipExtractionEvent.Error(entryName, "File size limit exceeded"),
                                    )
                                    outputFile.delete()
                                } else {
                                    val metadata = pendingMetadata.remove(safeFileName)
                                    emittedImages.add(safeFileName)
                                    events.add(
                                        ZipExtractionEvent.MemeExtracted(
                                            extractedMeme = ExtractedMeme(
                                                imageUri = Uri.fromFile(outputFile),
                                                metadata = metadata,
                                            ),
                                            tempFile = outputFile,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            return events
        }

        /**
         * Read bytes from input stream with a size limit.
         * Returns null and adds error if limit is exceeded.
         */
        private fun readBytesWithLimit(
            input: ZipInputStream,
            maxSize: Long,
            entryName: String,
            errors: MutableMap<String, String>,
        ): ByteArray? {
            val buffer = ByteArray(IO_BUFFER_SIZE)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0L
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSize) {
                    errors[entryName] = "JSON size limit exceeded (max: ${maxSize / BYTES_PER_KB}KB)"
                    return null
                }
                output.write(buffer, 0, bytesRead)
            }

            return output.toByteArray()
        }

        /**
         * Read bytes from input stream with size limit for streaming.
         * Returns null if limit exceeded.
         */
        private fun readBytesWithLimitStream(
            input: ZipInputStream,
            maxSize: Long,
        ): ByteArray? {
            val buffer = ByteArray(IO_BUFFER_SIZE)
            val output = java.io.ByteArrayOutputStream()
            var totalRead = 0L
            var bytesRead: Int

            while (input.read(buffer).also { bytesRead = it } != -1) {
                totalRead += bytesRead
                if (totalRead > maxSize) {
                    return null
                }
                output.write(buffer, 0, bytesRead)
            }

            return output.toByteArray()
        }

        /**
         * Copy from input stream to file with a size limit.
         * Returns bytes written, or -1 if limit exceeded.
         */
        private fun copyWithLimit(
            input: ZipInputStream,
            outputFile: File,
            maxSize: Long,
        ): Long {
            val buffer = ByteArray(IO_BUFFER_SIZE)
            var totalWritten = 0L

            FileOutputStream(outputFile).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    totalWritten += bytesRead
                    if (totalWritten > maxSize) {
                        return -1
                    }
                    output.write(buffer, 0, bytesRead)
                }
            }

            return totalWritten
        }

        /**
         * Sanitize a file name to prevent path traversal attacks.
         * Returns only the file name component, blocking any path components.
         *
         * @param entryName The original entry name from the ZIP.
         * @return The sanitized file name, or null if the name is invalid.
         */
        private fun getSafeFileName(entryName: String): String? {
            val fileName = File(entryName).name
            if (fileName.isEmpty() || fileName.startsWith(".") || fileName.contains("..")) {
                return null
            }
            return fileName
        }

        /**
         * Create a file handle for output, validating that it's within the extract directory.
         * Prevents ZIP Slip path traversal attacks.
         *
         * @param fileName The sanitized file name.
         * @return A File within extractDir, or null if path would escape.
         */
        private fun getSafeOutputFile(fileName: String): File? {
            val outputFile = File(extractDir, fileName)
            val canonicalExtractDir = extractDir.canonicalPath
            val canonicalOutputPath = outputFile.canonicalPath

            // Ensure the output path is within the extract directory
            return if (canonicalOutputPath.startsWith(canonicalExtractDir + File.separator)) {
                outputFile
            } else {
                null
            }
        }

        private fun isImageFile(fileName: String): Boolean {
            val lowerName = fileName.lowercase()
            return SUPPORTED_IMAGE_EXTENSIONS.any { lowerName.endsWith(it) }
        }

        private fun parseMetadataJson(content: String): MemeMetadata? {
            return try {
                json.decodeFromString<MemeMetadata>(content)
            } catch (e: SerializationException) {
                Timber.e(e, "Failed to parse meme metadata JSON")
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
