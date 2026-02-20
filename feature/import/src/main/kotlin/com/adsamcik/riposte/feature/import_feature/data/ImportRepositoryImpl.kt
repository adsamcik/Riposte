package com.adsamcik.riposte.feature.import_feature.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.adsamcik.riposte.core.common.AppConstants
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.ml.EmbeddingManager
import com.adsamcik.riposte.core.ml.TextRecognizer
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.model.ImportRequestItemData
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Bundle of ML service dependencies used by [ImportRepositoryImpl].
 */
data class ImportMlServices
    @Inject
    constructor(
        val textRecognizer: TextRecognizer,
        val embeddingManager: EmbeddingManager,
        val xmpMetadataHandler: XmpMetadataHandler,
    )

class ImportRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val memeDao: MemeDao,
        private val emojiTagDao: EmojiTagDao,
        private val importRequestDao: ImportRequestDao,
        private val mlServices: ImportMlServices,
    ) : ImportRepository {
        private val memesDir: File by lazy {
            File(context.filesDir, "memes").also { it.mkdirs() }
        }

        private val thumbnailsDir: File by lazy {
            File(context.filesDir, "thumbnails").also { it.mkdirs() }
        }

        companion object {
            private const val THUMBNAIL_SIZE = 256
            private const val MAX_IMAGE_DIMENSION = 2048
        }

        override suspend fun importImage(
            uri: Uri,
            metadata: MemeMetadata?,
        ): Result<Meme> =
            withContext(Dispatchers.IO) {
                try {
                    // Extract parameters from metadata
                    val emojis = metadata?.emojis ?: emptyList()
                    val title = metadata?.title
                    val description = metadata?.description
                    // Generate unique filename
                    val fileName = "${UUID.randomUUID()}.jpg"
                    val imageFile = File(memesDir, fileName)
                    val thumbnailFile = File(thumbnailsDir, "thumb_$fileName")

                    // Hash original source bytes for deterministic duplicate detection
                    val fileHash = calculateUriHash(uri)

                    // Copy and process image
                    val bitmap =
                        loadAndResizeBitmap(uri)
                            ?: return@withContext Result.failure(Exception("Failed to load image"))

                    // Save full image
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    // Generate thumbnail
                    val thumbnail = createThumbnail(bitmap)
                    FileOutputStream(thumbnailFile).use { out ->
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 80, out)
                    }

                    // Use pre-extracted text from metadata if available, otherwise run OCR
                    val extractedText = metadata?.textContent ?: extractTextFromBitmap(bitmap)

                    // Parse search phrases from metadata
                    val searchPhrases = metadata?.searchPhrases ?: emptyList()

                    // Create XMP metadata and embed in image
                    val xmpMetadata =
                        if (emojis.isNotEmpty()) {
                            MemeMetadata(
                                schemaVersion = AppConstants.METADATA_SCHEMA_VERSION,
                                emojis = emojis,
                                title = title,
                                description = description,
                                createdAt = Instant.now().toString(),
                                appVersion = AppConstants.APP_VERSION,
                            )
                        } else {
                            null
                        }
                    xmpMetadata?.let { mlServices.xmpMetadataHandler.writeMetadata(imageFile.absolutePath, it) }

                    // Create database entity (embedding stored separately in meme_embeddings table)
                    val now = System.currentTimeMillis()
                    val originalFileName = getFileNameFromUri(uri) ?: "Untitled"
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val memeEntity =
                        MemeEntity(
                            filePath = imageFile.absolutePath,
                            fileName = originalFileName,
                            mimeType = mimeType,
                            width = bitmap.width,
                            height = bitmap.height,
                            fileSizeBytes = imageFile.length(),
                            importedAt = now,
                            emojiTagsJson = kotlinx.serialization.json.Json.encodeToString(emojis),
                            title = title ?: originalFileName,
                            description = description,
                            textContent = extractedText,
                            searchPhrasesJson =
                                if (searchPhrases.isNotEmpty()) {
                                    kotlinx.serialization.json.Json.encodeToString(searchPhrases)
                                } else {
                                    null
                                },
                            // Embeddings now stored in separate table
                            embedding = null,
                            fileHash = fileHash,
                            basedOn = metadata?.basedOn,
                            primaryLanguage = metadata?.primaryLanguage,
                            localizationsJson =
                                metadata?.localizations?.takeIf { it.isNotEmpty() }?.let {
                                    kotlinx.serialization.json.Json.encodeToString(it)
                                },
                        )

                    // Insert meme
                    val memeId = memeDao.insertMeme(memeEntity)

                    // Schedule background embedding generation
                    mlServices.embeddingManager.scheduleBackgroundGeneration()

                    // Insert emoji tags
                    val emojiTagEntities =
                        emojis.map { emoji ->
                            val emojiTag = EmojiTag.fromEmoji(emoji)
                            EmojiTagEntity(
                                memeId = memeId,
                                emoji = emoji,
                                emojiName = emojiTag.name,
                            )
                        }
                    if (emojiTagEntities.isNotEmpty()) {
                        emojiTagDao.insertEmojiTags(emojiTagEntities)
                    }

                    // Return domain model
                    val meme =
                        Meme(
                            id = memeId,
                            filePath = imageFile.absolutePath,
                            fileName = memeEntity.fileName,
                            mimeType = memeEntity.mimeType,
                            width = bitmap.width,
                            height = bitmap.height,
                            fileSizeBytes = memeEntity.fileSizeBytes,
                            importedAt = now,
                            emojiTags = emojis.map { EmojiTag.fromEmoji(it) },
                            title = memeEntity.title,
                            description = description,
                            textContent = extractedText,
                            searchPhrases = searchPhrases,
                            basedOn = metadata?.basedOn,
                            isFavorite = false,
                        )

                    Result.success(meme)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun importImages(uris: List<Uri>): List<Result<Meme>> =
            withContext(Dispatchers.IO) {
                uris.map { uri -> importImage(uri, null) }
            }

        /**
         * Import multiple images with detailed progress tracking.
         * This is an extended version that provides progress callbacks.
         */
        fun importImagesWithProgress(
            uris: List<Uri>,
            metadataByUri: Map<Uri, MemeMetadata?>,
        ): Flow<ImportProgress> =
            flow {
                val total = uris.size
                val successMemes = mutableListOf<Meme>()
                val failures = mutableListOf<Pair<Uri, Exception>>()

                uris.forEachIndexed { index, uri ->
                    emit(
                        ImportProgress(
                            current = index,
                            total = total,
                            currentUri = uri,
                            successCount = successMemes.size,
                            failureCount = failures.size,
                        ),
                    )

                    val result =
                        importImage(
                            uri = uri,
                            metadata = metadataByUri[uri],
                        )

                    result.fold(
                        onSuccess = { meme -> successMemes.add(meme) },
                        onFailure = { error -> failures.add(uri to (error as? Exception ?: Exception(error))) },
                    )
                }

                emit(
                    ImportProgress(
                        current = total,
                        total = total,
                        currentUri = null,
                        successCount = successMemes.size,
                        failureCount = failures.size,
                        isComplete = true,
                        importedMemes = successMemes,
                        failedUris = failures,
                    ),
                )
            }

        /**
         * Progress data for batch imports.
         */
        data class ImportProgress(
            val current: Int,
            val total: Int,
            val currentUri: Uri?,
            val successCount: Int,
            val failureCount: Int,
            val isComplete: Boolean = false,
            val importedMemes: List<Meme> = emptyList(),
            val failedUris: List<Pair<Uri, Exception>> = emptyList(),
        )

        override suspend fun extractMetadata(uri: Uri): MemeMetadata? {
            return mlServices.xmpMetadataHandler.readMetadata(uri)
        }

        override suspend fun extractText(uri: Uri): String? =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = loadAndResizeBitmap(uri) ?: return@withContext null
                    extractTextFromBitmap(bitmap)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract text from URI")
                    null
                }
            }

        override suspend fun suggestEmojis(uri: Uri): List<EmojiTag> =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap = loadAndResizeBitmap(uri) ?: return@withContext emptyList()

                    // Map labels to emojis based on common associations
                    val suggestions = mutableListOf<EmojiTag>()

                    // Check extracted text for emoji hints
                    val extractedText = extractTextFromBitmap(bitmap)?.lowercase() ?: ""

                    // Common emojis for suggestion
                    val commonEmojiList =
                        listOf(
                            EmojiTag("😂", "face_with_tears_of_joy"),
                            EmojiTag("🤣", "rolling_on_the_floor_laughing"),
                            EmojiTag("😊", "smiling_face_with_smiling_eyes"),
                            EmojiTag("😍", "smiling_face_with_heart_eyes"),
                            EmojiTag("🥺", "pleading_face"),
                            EmojiTag("😭", "loudly_crying_face"),
                            EmojiTag("😤", "face_with_steam_from_nose"),
                            EmojiTag("😡", "pouting_face"),
                            EmojiTag("🤔", "thinking_face"),
                            EmojiTag("😏", "smirking_face"),
                            EmojiTag("👀", "eyes"),
                            EmojiTag("💀", "skull"),
                            EmojiTag("🔥", "fire"),
                            EmojiTag("💯", "hundred_points"),
                            EmojiTag("❤️", "red_heart"),
                            EmojiTag("👍", "thumbs_up"),
                            EmojiTag("🙏", "folded_hands"),
                            EmojiTag("🎉", "party_popper"),
                            EmojiTag("✨", "sparkles"),
                        )

                    // Add emojis based on text content
                    commonEmojiList.forEach { emojiTag ->
                        val keywords = emojiTag.name.split("_")
                        if (keywords.any { extractedText.contains(it) }) {
                            suggestions.add(emojiTag)
                        }
                    }

                    // Limit suggestions
                    suggestions.take(5)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to suggest emojis for URI")
                    emptyList()
                }
            }

        override suspend fun isDuplicate(uri: Uri): Boolean =
            withContext(Dispatchers.IO) {
                try {
                    val hash = calculateUriHash(uri) ?: return@withContext false
                    memeDao.memeExistsByHash(hash)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to check for duplicate URI")
                    false
                }
            }

        override suspend fun findDuplicateMemeId(uri: Uri): Long? =
            withContext(Dispatchers.IO) {
                try {
                    val hash = calculateUriHash(uri) ?: return@withContext null
                    memeDao.getMemeByHash(hash)?.id
                } catch (e: Exception) {
                    Timber.e(e, "Failed to find duplicate meme ID for URI")
                    null
                }
            }

        override suspend fun updateMemeMetadata(
            memeId: Long,
            metadata: MemeMetadata,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val existing =
                        memeDao.getMemeById(memeId)
                            ?: return@withContext Result.failure(Exception("Meme not found"))

                    // Update entity fields
                    val searchPhrasesJson =
                        if (metadata.searchPhrases.isNotEmpty()) {
                            kotlinx.serialization.json.Json.encodeToString(metadata.searchPhrases)
                        } else {
                            existing.searchPhrasesJson
                        }
                    val updated =
                        existing.copy(
                            emojiTagsJson = kotlinx.serialization.json.Json.encodeToString(metadata.emojis),
                            title = metadata.title ?: existing.title,
                            description = metadata.description ?: existing.description,
                            textContent = metadata.textContent ?: existing.textContent,
                            searchPhrasesJson = searchPhrasesJson,
                            basedOn = metadata.basedOn ?: existing.basedOn,
                            primaryLanguage = metadata.primaryLanguage ?: existing.primaryLanguage,
                            localizationsJson =
                                metadata.localizations.takeIf { it.isNotEmpty() }?.let {
                                    kotlinx.serialization.json.Json.encodeToString(it)
                                } ?: existing.localizationsJson,
                        )
                    memeDao.updateMeme(updated)

                    // Replace emoji tags
                    emojiTagDao.deleteEmojiTagsForMeme(memeId)
                    val emojiTagEntities =
                        metadata.emojis.map { emoji ->
                            val emojiTag = EmojiTag.fromEmoji(emoji)
                            EmojiTagEntity(
                                memeId = memeId,
                                emoji = emoji,
                                emojiName = emojiTag.name,
                            )
                        }
                    if (emojiTagEntities.isNotEmpty()) {
                        emojiTagDao.insertEmojiTags(emojiTagEntities)
                    }

                    // Mark embeddings for regeneration in background
                    mlServices.embeddingManager.markForRegeneration(memeId)
                    mlServices.embeddingManager.scheduleBackgroundGeneration()

                    // Update XMP metadata in image file
                    val xmpMetadata =
                        MemeMetadata(
                            schemaVersion = AppConstants.METADATA_SCHEMA_VERSION,
                            emojis = metadata.emojis,
                            title = metadata.title,
                            description = metadata.description,
                            appVersion = AppConstants.APP_VERSION,
                        )
                    mlServices.xmpMetadataHandler.writeMetadata(existing.filePath, xmpMetadata)

                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        private suspend fun loadAndResizeBitmap(uri: Uri): Bitmap? =
            withContext(Dispatchers.IO) {
                try {
                    // For file:// URIs, use BitmapFactory.decodeFile which handles buffering correctly
                    val filePath = if (uri.scheme == "file") uri.path else null

                    // First pass: decode bounds only
                    val outWidth: Int
                    val outHeight: Int
                    if (filePath != null) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(filePath, options)
                        outWidth = options.outWidth
                        outHeight = options.outHeight
                    } else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            BitmapFactory.decodeStream(java.io.BufferedInputStream(input), null, options)
                        }
                        outWidth = options.outWidth
                        outHeight = options.outHeight
                    }

                    if (outWidth <= 0 || outHeight <= 0) {
                        Timber.w("Failed to decode image bounds: %dx%d for %s", outWidth, outHeight, uri)
                        return@withContext null
                    }

                    // Calculate sample size
                    val sampleSize = calculateInSampleSize(
                        outWidth, outHeight, MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION,
                    )

                    // Second pass: actual decode (stream fully closed from first pass)
                    val bitmap = if (filePath != null) {
                        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        BitmapFactory.decodeFile(filePath, decodeOptions)
                    } else {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                            BitmapFactory.decodeStream(java.io.BufferedInputStream(input), null, decodeOptions)
                        }
                    }

                    if (bitmap != null) {
                        Timber.d(
                            "Decoded image: %dx%d (sample=%d) from %s",
                            bitmap.width, bitmap.height, sampleSize, uri,
                        )
                    } else {
                        Timber.w("BitmapFactory returned null for %s (bounds were %dx%d)", uri, outWidth, outHeight)
                    }

                    bitmap
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load and resize bitmap from %s", uri)
                    null
                }
            }

        private fun calculateInSampleSize(
            width: Int,
            height: Int,
            reqWidth: Int,
            reqHeight: Int,
        ): Int {
            var sampleSize = 1
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while (halfHeight / sampleSize >= reqHeight && halfWidth / sampleSize >= reqWidth) {
                    sampleSize *= 2
                }
            }
            return sampleSize
        }

        private fun createThumbnail(bitmap: Bitmap): Bitmap {
            val ratio =
                minOf(
                    THUMBNAIL_SIZE.toFloat() / bitmap.width,
                    THUMBNAIL_SIZE.toFloat() / bitmap.height,
                )
            val width = (bitmap.width * ratio).toInt()
            val height = (bitmap.height * ratio).toInt()
            return Bitmap.createScaledBitmap(bitmap, width, height, true)
        }

        private suspend fun extractTextFromBitmap(bitmap: Bitmap): String? {
            return mlServices.textRecognizer.recognizeText(bitmap)
        }

        private fun calculateFileHash(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /**
         * Hash the raw bytes from a content URI for deterministic duplicate detection.
         * Unlike [calculateFileHash], this hashes the original source bytes before any
         * bitmap decode/re-encode, ensuring the same source file always produces the
         * same hash regardless of image format or device-specific decoding behavior.
         */
        private fun calculateUriHash(uri: Uri): String? {
            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            } ?: return null
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun getFileNameFromUri(uri: Uri): String? {
            return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.substringBeforeLast(".")
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }

        override suspend fun createImportRequest(
            id: String,
            imageCount: Int,
            stagingDir: String,
        ) {
            val now = System.currentTimeMillis()
            val request =
                ImportRequestEntity(
                    id = id,
                    status = ImportRequestEntity.STATUS_PENDING,
                    imageCount = imageCount,
                    stagingDir = stagingDir,
                    createdAt = now,
                    updatedAt = now,
                )
            importRequestDao.insertRequest(request)
        }

        override suspend fun createImportRequestItems(
            requestId: String,
            items: List<ImportRequestItemData>,
        ) {
            val entities =
                items.map { item ->
                    ImportRequestItemEntity(
                        id = item.id,
                        requestId = requestId,
                        stagedFilePath = item.stagedFilePath,
                        originalFileName = item.originalFileName,
                        emojis = item.emojis,
                        title = item.title,
                        description = item.description,
                        extractedText = item.extractedText,
                        metadataJson = item.metadataJson,
                    )
                }
            importRequestDao.insertItems(entities)
        }
    }
