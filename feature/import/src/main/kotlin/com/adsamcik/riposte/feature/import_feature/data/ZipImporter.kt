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
import java.io.IOException
import java.util.UUID
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
            /**
             * Maximum number of entries allowed in a ZIP file (ZIP bomb protection).
             */
            const val MAX_ENTRY_COUNT = 10_000

            private const val BYTES_PER_KB = 1024

            /**
             * Maximum size for a single extracted file (50 MB).
             */
            const val MAX_SINGLE_FILE_SIZE = 50L * BYTES_PER_KB * BYTES_PER_KB

            /**
             * Maximum size for a JSON sidecar file (1 MB).
             */
            const val MAX_JSON_SIZE = 1L * BYTES_PER_KB * BYTES_PER_KB

            /**
             * Maximum total extraction size across all entries (2 GB).
             */
            const val MAX_TOTAL_EXTRACTION_SIZE = 2_000_000_000L
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

                // Use unique subdirectory to avoid conflicts with parallel extractions
                val uniqueExtractDir = File(extractDir, UUID.randomUUID().toString())
                uniqueExtractDir.mkdirs()
                var totalExtractedBytes = 0L

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
                                Timber.d(
                                    "extractBundle: entry #%d name='%s' size=%d compressed=%d isDir=%b",
                                    entryCount,
                                    entryName,
                                    entry.size,
                                    entry.compressedSize,
                                    entry.isDirectory,
                                )

                                // Skip directories and hidden files
                                if (entry.isDirectory || entryName.startsWith(".") || entryName.contains("/")) {
                                    Timber.d(
                                        "extractBundle: SKIPPING entry (dir=%b, dotfile=%b, hasSlash=%b)",
                                        entry.isDirectory,
                                        entryName.startsWith("."),
                                        entryName.contains("/"),
                                    )
                                    zipInput.closeEntry()
                                    entry = zipInput.nextEntry
                                    continue
                                }

                                try {
                                    val bytesExtracted = processZipEntry(
                                        entryName, entry, zipInput, extractedImages,
                                        metadataMap, errors, uniqueExtractDir,
                                    )
                                    totalExtractedBytes += bytesExtracted
                                    if (totalExtractedBytes > MAX_TOTAL_EXTRACTION_SIZE) {
                                        errors["bundle"] = totalExtractionSizeLimitMessage()
                                        break
                                    }
                                } catch (
                                    @Suppress("TooGenericExceptionCaught") // I/O + parsing may throw various exceptions
                                    e: Exception,
                                ) {
                                    Timber.w(e, "extractBundle: failed to process entry '%s'", entryName)
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

                // Use unique subdirectory to avoid conflicts with parallel extractions
                val uniqueExtractDir = File(extractDir, UUID.randomUUID().toString())
                uniqueExtractDir.mkdirs()
                var totalExtractedBytes = 0L

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
                                    val (events, bytesExtracted) = processStreamZipEntry(
                                        ZipEntryContext(entryName, entry, zipInput, uniqueExtractDir),
                                        StreamExtractionState(pendingMetadata, emittedImages),
                                    )
                                    totalExtractedBytes += bytesExtracted
                                    for (event in events) {
                                        emit(event)
                                        when (event) {
                                            is ZipExtractionEvent.MemeExtracted -> processedCount++
                                            is ZipExtractionEvent.Error -> errorCount++
                                            else -> {}
                                        }
                                    }
                                    if (totalExtractedBytes > MAX_TOTAL_EXTRACTION_SIZE) {
                                        emit(
                                            ZipExtractionEvent.Error(
                                                "bundle",
                                                "Total extraction size limit exceeded",
                                            ),
                                        )
                                        errorCount++
                                        break
                                    }
                                } catch (
                                    @Suppress("TooGenericExceptionCaught") // I/O + parsing may throw various exceptions
                                    e: Exception,
                                ) {
                                    Timber.w(e, "extractBundleStream: failed to process entry '%s'", entryName)
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
        @Suppress("LongParameterList")
        private fun processZipEntry(
            entryName: String,
            entry: java.util.zip.ZipEntry,
            zipInput: ZipInputStream,
            extractedImages: MutableMap<String, File>,
            metadataMap: MutableMap<String, MemeMetadata>,
            errors: MutableMap<String, String>,
            baseDir: File,
        ): Long {
            val context = ZipEntryContext(entryName, entry, zipInput, baseDir)
            val state = BatchExtractionState(extractedImages, metadataMap, errors)
            return when {
                entryName.endsWith(".json") -> {
                    processMetadataEntry(context, state)
                    0L
                }
                isImageFile(entryName) -> processImageEntry(context, state)
                else -> 0L
            }
        }

        private data class ZipEntryContext(
            val entryName: String,
            val entry: java.util.zip.ZipEntry,
            val zipInput: ZipInputStream,
            val baseDir: File,
        )

        private data class BatchExtractionState(
            val extractedImages: MutableMap<String, File>,
            val metadataMap: MutableMap<String, MemeMetadata>,
            val errors: MutableMap<String, String>,
        )

        private fun processMetadataEntry(
            context: ZipEntryContext,
            state: BatchExtractionState,
        ) {
            if (context.entry.size > MAX_JSON_SIZE) {
                state.errors[context.entryName] = jsonSizeLimitMessage(MAX_JSON_SIZE)
            } else {
                val imageFileName = getSafeFileName(context.entryName.removeSuffix(".json"))
                val content = imageFileName?.let {
                    readBytesWithLimit(context.zipInput, MAX_JSON_SIZE, context.entryName, state.errors)
                }
                val metadata = content?.let { parseMetadataJson(it.decodeToString()) }
                if (imageFileName != null && metadata != null) {
                    state.metadataMap[imageFileName] = metadata
                }
            }
        }

        private fun processImageEntry(
            context: ZipEntryContext,
            state: BatchExtractionState,
        ): Long =
            if (context.entry.size > MAX_SINGLE_FILE_SIZE) {
                state.errors[context.entryName] = fileSizeLimitMessage()
                0L
            } else {
                copySafeImageEntry(context, state)
            }

        private fun copySafeImageEntry(
            context: ZipEntryContext,
            state: BatchExtractionState,
        ): Long {
            val safeFileName = getSafeFileName(context.entryName)
            val outputFile = safeFileName?.let { getSafeOutputFile(it, context.baseDir) }
            return if (safeFileName == null || outputFile == null) {
                state.errors[context.entryName] = "Path traversal attempt blocked"
                0L
            } else {
                recordCopiedImage(context, state, safeFileName, outputFile)
            }
        }

        private fun recordCopiedImage(
            context: ZipEntryContext,
            state: BatchExtractionState,
            safeFileName: String,
            outputFile: File,
        ): Long {
            val written = copyWithLimit(context.zipInput, outputFile, MAX_SINGLE_FILE_SIZE)
            Timber.d(
                "extractBundle: image '%s' -> '%s' wrote %d bytes",
                context.entryName,
                outputFile.name,
                written,
            )
            return when {
                written < 0 -> {
                    state.errors[context.entryName] = fileSizeLimitMessage()
                    outputFile.delete()
                    0L
                }
                written == 0L -> {
                    state.errors[context.entryName] = "Empty file (possible interrupted download)"
                    outputFile.delete()
                    0L
                }
                else -> {
                    state.extractedImages[safeFileName] = outputFile
                    written
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
        private fun processStreamZipEntry(
            context: ZipEntryContext,
            state: StreamExtractionState,
        ): Pair<List<ZipExtractionEvent>, Long> {
            val events = mutableListOf<ZipExtractionEvent>()
            val bytesExtracted = when {
                context.entryName.endsWith(".json") -> {
                    processStreamMetadataEntry(context, state, events)
                    0L
                }
                isImageFile(context.entryName) -> processStreamImageEntry(context, state, events)
                else -> 0L
            }
            return Pair(events, bytesExtracted)
        }

        private data class StreamExtractionState(
            val pendingMetadata: MutableMap<String, MemeMetadata>,
            val emittedImages: MutableSet<String>,
        )

        private fun processStreamMetadataEntry(
            context: ZipEntryContext,
            state: StreamExtractionState,
            events: MutableList<ZipExtractionEvent>,
        ) {
            if (context.entry.size > MAX_JSON_SIZE) {
                events.add(ZipExtractionEvent.Error(context.entryName, "JSON size limit exceeded"))
            } else {
                val imageFileName = getSafeFileName(context.entryName.removeSuffix(".json"))
                val content = imageFileName?.let { readBytesWithLimitStream(context.zipInput, MAX_JSON_SIZE) }
                val metadata = content?.let { parseMetadataJson(it.decodeToString()) }
                if (content == null && imageFileName != null) {
                    events.add(ZipExtractionEvent.Error(context.entryName, "JSON size limit exceeded"))
                } else if (imageFileName != null && metadata != null) {
                    state.pendingMetadata[imageFileName] = metadata
                }
            }
        }

        private fun processStreamImageEntry(
            context: ZipEntryContext,
            state: StreamExtractionState,
            events: MutableList<ZipExtractionEvent>,
        ): Long =
            if (context.entry.size > MAX_SINGLE_FILE_SIZE) {
                events.add(ZipExtractionEvent.Error(context.entryName, "File size limit exceeded"))
                0L
            } else {
                copySafeStreamImageEntry(context, state, events)
            }

        private fun copySafeStreamImageEntry(
            context: ZipEntryContext,
            state: StreamExtractionState,
            events: MutableList<ZipExtractionEvent>,
        ): Long {
            val safeFileName = getSafeFileName(context.entryName)
            val outputFile = safeFileName?.let { getSafeOutputFile(it, context.baseDir) }
            return if (safeFileName == null || outputFile == null) {
                events.add(ZipExtractionEvent.Error(context.entryName, "Path traversal attempt blocked"))
                0L
            } else {
                recordStreamImage(context, state, events, safeFileName, outputFile)
            }
        }

        private fun recordStreamImage(
            context: ZipEntryContext,
            state: StreamExtractionState,
            events: MutableList<ZipExtractionEvent>,
            safeFileName: String,
            outputFile: File,
        ): Long {
            val written = copyWithLimit(context.zipInput, outputFile, MAX_SINGLE_FILE_SIZE)
            return when {
                written < 0 -> {
                    events.add(ZipExtractionEvent.Error(context.entryName, "File size limit exceeded"))
                    outputFile.delete()
                    0L
                }
                written == 0L -> {
                    events.add(
                        ZipExtractionEvent.Error(
                            context.entryName,
                            "Empty file (possible interrupted download)",
                        ),
                    )
                    outputFile.delete()
                    0L
                }
                else -> {
                    state.emittedImages.add(safeFileName)
                    events.add(
                        ZipExtractionEvent.MemeExtracted(
                            extractedMeme = ExtractedMeme(
                                imageUri = Uri.fromFile(outputFile),
                                metadata = state.pendingMetadata.remove(safeFileName),
                            ),
                            tempFile = outputFile,
                        ),
                    )
                    written
                }
            }
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
