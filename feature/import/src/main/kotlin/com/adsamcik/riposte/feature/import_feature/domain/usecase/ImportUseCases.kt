package com.adsamcik.riposte.feature.import_feature.domain.usecase

import android.net.Uri
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.data.ZipExtractionEvent
import com.adsamcik.riposte.feature.import_feature.domain.ZipImporter
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for importing a single image.
 */
class ImportImageUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(
            uri: Uri,
            metadata: MemeMetadata? = null,
        ): Result<Meme> {
            return repository.importImage(uri, metadata)
        }
    }

/**
 * Use case for importing multiple images.
 */
class ImportImagesUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uris: List<Uri>): List<Result<Meme>> {
            return repository.importImages(uris)
        }
    }

/**
 * Use case for extracting metadata from an image.
 */
class ExtractMetadataUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uri: Uri): MemeMetadata? {
            return repository.extractMetadata(uri)
        }
    }

/**
 * Use case for suggesting emojis for an image.
 */
class SuggestEmojisUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uri: Uri): List<EmojiTag> {
            return repository.suggestEmojis(uri)
        }
    }

/**
 * Use case for extracting text from an image.
 */
class ExtractTextUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uri: Uri): String? {
            return repository.extractText(uri)
        }
    }

/**
 * Use case for checking if an image is a duplicate.
 */
class CheckDuplicateUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uri: Uri): Boolean {
            return repository.isDuplicate(uri)
        }
    }

/**
 * Use case for finding the existing meme ID of a duplicate image.
 */
class FindDuplicateMemeIdUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(uri: Uri): Long? {
            return repository.findDuplicateMemeId(uri)
        }
    }

/**
 * Use case for updating metadata on an existing meme.
 */
class UpdateMemeMetadataUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
    ) {
        suspend operator fun invoke(
            memeId: Long,
            metadata: MemeMetadata,
        ): Result<Unit> {
            return repository.updateMemeMetadata(memeId, metadata)
        }
    }

/**
 * Use case for cleaning up temporary files created during ZIP extraction.
 */
class CleanupExtractedFilesUseCase
    @Inject
    constructor(
        private val zipImporter: ZipImporter,
    ) {
        operator fun invoke() {
            zipImporter.cleanupExtractedFiles()
        }
    }

/**
 * Use case for extracting a ZIP bundle for preview without importing.
 */
class ExtractZipForPreviewUseCase
    @Inject
    constructor(
        private val zipImporter: ZipImporter,
    ) {
        suspend operator fun invoke(zipUri: Uri): com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult {
            if (!zipImporter.isMemeZipBundle(zipUri)) {
                return com.adsamcik.riposte.feature.import_feature.data.ZipExtractionResult(
                    extractedMemes = emptyList(),
                    errors = mapOf("bundle" to "Not a valid .meme.zip bundle"),
                )
            }
            return zipImporter.extractBundle(zipUri)
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
class ImportZipBundleUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
        private val zipImporter: ZipImporter,
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

            // Import each extracted image, ensuring temp files are always cleaned up
            val importedMemes = mutableListOf<Meme>()
            val importErrors = mutableMapOf<String, String>()

            try {
                // Extract the bundle
                val extractionResult = zipImporter.extractBundle(zipUri)
                importErrors.putAll(extractionResult.errors)

                for (extractedMeme in extractionResult.extractedMemes) {
                    val result =
                        repository.importImage(
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
            } finally {
                // Clean up extracted files even if an exception occurs
                zipImporter.cleanupExtractedFiles()
            }

            return ZipImportResult(
                successCount = importedMemes.size,
                failureCount = importErrors.size,
                importedMemes = importedMemes,
                errors = importErrors,
            )
        }
    }

/**
 * Events emitted during streaming ZIP import.
 */
sealed interface ZipImportEvent {
    /** Progress update. */
    data class Progress(
        val imported: Int,
        val errors: Int,
        val currentEntry: String,
    ) : ZipImportEvent

    /** A single meme was successfully imported. */
    data class MemeImported(val meme: Meme) : ZipImportEvent

    /** Error importing a specific file. */
    data class Error(val fileName: String, val message: String) : ZipImportEvent

    /** Import completed. */
    data class Complete(val result: ZipImportResult) : ZipImportEvent
}

/**
 * Bundle of use cases consumed by
 * [ImportViewModel][com.adsamcik.riposte.feature.import_feature.presentation.ImportViewModel].
 */
data class ImportViewModelUseCases
    @Inject
    constructor(
        val importImage: ImportImageUseCase,
        val suggestEmojis: SuggestEmojisUseCase,
        val extractText: ExtractTextUseCase,
        val extractZipForPreview: ExtractZipForPreviewUseCase,
        val checkDuplicate: CheckDuplicateUseCase,
        val findDuplicateMemeId: FindDuplicateMemeIdUseCase,
        val updateMemeMetadata: UpdateMemeMetadataUseCase,
        val cleanupExtractedFiles: CleanupExtractedFilesUseCase,
    )

class ImportZipBundleStreamingUseCase
    @Inject
    constructor(
        private val repository: ImportRepository,
        private val zipImporter: ZipImporter,
    ) {
        /**
         * Import all images from a .meme.zip bundle with streaming progress.
         *
         * @param zipUri URI pointing to the ZIP file.
         * @return Flow of import events.
         */
        operator fun invoke(zipUri: Uri): Flow<ZipImportEvent> =
            flow {
                // Check if this is a valid ZIP bundle
                if (!zipImporter.isMemeZipBundle(zipUri)) {
                    emit(ZipImportEvent.Error("bundle", "Not a valid .meme.zip bundle"))
                    emit(
                        ZipImportEvent.Complete(
                            ZipImportResult(
                                successCount = 0,
                                failureCount = 1,
                                importedMemes = emptyList(),
                                errors = mapOf("bundle" to "Not a valid .meme.zip bundle"),
                            ),
                        ),
                    )
                    return@flow
                }

                val importedMemes = mutableListOf<Meme>()
                val importErrors = mutableMapOf<String, String>()

                try {
                    zipImporter.extractBundleStream(zipUri).collect { event ->
                        when (event) {
                            is ZipExtractionEvent.Progress -> {
                                emit(
                                    ZipImportEvent.Progress(
                                        imported = importedMemes.size,
                                        errors = importErrors.size,
                                        currentEntry = event.currentEntry,
                                    ),
                                )
                            }

                            is ZipExtractionEvent.MemeExtracted -> {
                                val result =
                                    repository.importImage(
                                        uri = event.extractedMeme.imageUri,
                                        metadata = event.extractedMeme.metadata,
                                    )

                                result.fold(
                                    onSuccess = { meme ->
                                        importedMemes.add(meme)
                                        emit(ZipImportEvent.MemeImported(meme))
                                    },
                                    onFailure = { error ->
                                        val fileName = event.extractedMeme.imageUri.lastPathSegment ?: "unknown"
                                        val message = error.message ?: "Import failed"
                                        importErrors[fileName] = message
                                        emit(ZipImportEvent.Error(fileName, message))
                                    },
                                )

                                // Clean up temp file immediately after import
                                event.tempFile.delete()
                            }

                            is ZipExtractionEvent.Error -> {
                                importErrors[event.entryName] = event.message
                                emit(ZipImportEvent.Error(event.entryName, event.message))
                            }

                            is ZipExtractionEvent.Complete -> {
                                emit(
                                    ZipImportEvent.Complete(
                                        ZipImportResult(
                                            successCount = importedMemes.size,
                                            failureCount = importErrors.size,
                                            importedMemes = importedMemes.toList(),
                                            errors = importErrors.toMap(),
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                } finally {
                    // Clean up the extraction directory even if an exception occurs
                    zipImporter.cleanupExtractedFiles()
                }
            }
    }
