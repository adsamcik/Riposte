package com.mememymood.feature.import_feature.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.ml.EmbeddingManager
import com.mememymood.core.ml.TextRecognizer
import com.mememymood.core.ml.XmpMetadataHandler
import com.mememymood.core.model.EmojiTag
import com.mememymood.core.model.Meme
import com.mememymood.core.model.MemeMetadata
import com.mememymood.feature.import_feature.domain.repository.ImportRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class ImportRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val memeDao: MemeDao,
    private val emojiTagDao: EmojiTagDao,
    private val textRecognizer: TextRecognizer,
    private val embeddingManager: EmbeddingManager,
    private val xmpMetadataHandler: XmpMetadataHandler,
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
    ): Result<Meme> = withContext(Dispatchers.IO) {
        try {
            // Extract parameters from metadata
            val emojis = metadata?.emojis ?: emptyList()
            val title = metadata?.title
            val description = metadata?.description
            // Generate unique filename
            val fileName = "${UUID.randomUUID()}.jpg"
            val imageFile = File(memesDir, fileName)
            val thumbnailFile = File(thumbnailsDir, "thumb_$fileName")

            // Copy and process image
            val bitmap = loadAndResizeBitmap(uri)
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

            // Build search text for embedding generation
            val searchText = buildSearchText(title, description, extractedText, emojis)

            // Create XMP metadata and embed in image
            val xmpMetadata = if (emojis.isNotEmpty()) {
                MemeMetadata(
                    schemaVersion = "1.0",
                    emojis = emojis,
                    title = title,
                    description = description,
                    createdAt = Instant.now().toString(),
                    appVersion = "1.0.0",
                )
            } else null
            xmpMetadata?.let { xmpMetadataHandler.writeMetadata(imageFile.absolutePath, it) }

            // Calculate file hash for duplicate detection
            val fileHash = calculateFileHash(imageFile)

            // Create database entity (embedding stored separately in meme_embeddings table)
            val now = System.currentTimeMillis()
            val originalFileName = getFileNameFromUri(uri) ?: "Untitled"
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val memeEntity = MemeEntity(
                filePath = imageFile.absolutePath,
                fileName = originalFileName,
                mimeType = mimeType,
                width = bitmap.width,
                height = bitmap.height,
                fileSizeBytes = imageFile.length(),
                importedAt = now,
                emojiTagsJson = emojis.joinToString(","),
                title = title ?: originalFileName,
                description = description,
                textContent = extractedText,
                embedding = null, // Embeddings now stored in separate table
            )

            // Insert meme
            val memeId = memeDao.insertMeme(memeEntity)

            // Generate and store embedding in the new embeddings table
            if (searchText.isNotBlank()) {
                embeddingManager.generateAndStoreEmbedding(memeId, searchText)
            }

            // Insert emoji tags
            val emojiTagEntities = emojis.map { emoji ->
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
            val meme = Meme(
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
                isFavorite = false,
            )

            Result.success(meme)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun importImages(uris: List<Uri>): List<Result<Meme>> = withContext(Dispatchers.IO) {
        uris.map { uri -> importImage(uri, null) }
    }

    /**
     * Import multiple images with detailed progress tracking.
     * This is an extended version that provides progress callbacks.
     */
    fun importImagesWithProgress(
        uris: List<Uri>,
        metadataByUri: Map<Uri, MemeMetadata?>,
    ): Flow<ImportProgress> = flow {
        val total = uris.size
        val successMemes = mutableListOf<Meme>()
        val failures = mutableListOf<Pair<Uri, Exception>>()

        uris.forEachIndexed { index, uri ->
            emit(ImportProgress(
                current = index,
                total = total,
                currentUri = uri,
                successCount = successMemes.size,
                failureCount = failures.size,
            ))

            val result = importImage(
                uri = uri,
                metadata = metadataByUri[uri],
            )

            result.fold(
                onSuccess = { meme -> successMemes.add(meme) },
                onFailure = { error -> failures.add(uri to (error as? Exception ?: Exception(error))) },
            )
        }

        emit(ImportProgress(
            current = total,
            total = total,
            currentUri = null,
            successCount = successMemes.size,
            failureCount = failures.size,
            isComplete = true,
            importedMemes = successMemes,
            failedUris = failures,
        ))
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
        return xmpMetadataHandler.readMetadata(uri)
    }

    override suspend fun extractText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndResizeBitmap(uri) ?: return@withContext null
            extractTextFromBitmap(bitmap)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun suggestEmojis(uri: Uri): List<EmojiTag> = withContext(Dispatchers.IO) {
        try {
            val bitmap = loadAndResizeBitmap(uri) ?: return@withContext emptyList()

            // Map labels to emojis based on common associations
            val suggestions = mutableListOf<EmojiTag>()

            // Check extracted text for emoji hints
            val extractedText = extractTextFromBitmap(bitmap)?.lowercase() ?: ""

            // Common emojis for suggestion
            val commonEmojiList = listOf(
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
            emptyList()
        }
    }

    override suspend fun isDuplicate(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        // Note: Currently no hash-based duplicate detection available in MemeDao.
        // This would require adding getMemeByHash to MemeDao in core:database module.
        // For now, return false to indicate no duplicate found.
        false
    }

    private suspend fun loadAndResizeBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // First, decode bounds only
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(input, null, options)

                // Calculate sample size
                val sampleSize = calculateInSampleSize(
                    options.outWidth,
                    options.outHeight,
                    MAX_IMAGE_DIMENSION,
                    MAX_IMAGE_DIMENSION,
                )

                // Decode with sample size
                context.contentResolver.openInputStream(uri)?.use { input2 ->
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                    }
                    BitmapFactory.decodeStream(input2, null, decodeOptions)
                }
            }
        } catch (e: Exception) {
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
        val ratio = minOf(
            THUMBNAIL_SIZE.toFloat() / bitmap.width,
            THUMBNAIL_SIZE.toFloat() / bitmap.height,
        )
        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private suspend fun extractTextFromBitmap(bitmap: Bitmap): String? {
        return textRecognizer.recognizeText(bitmap)
    }

    private fun buildSearchText(
        title: String?,
        description: String?,
        extractedText: String?,
        emojis: List<String>,
    ): String {
        return buildString {
            title?.let { append(it).append(" ") }
            description?.let { append(it).append(" ") }
            extractedText?.let { append(it).append(" ") }
            emojis.forEach { emoji ->
                append(EmojiTag.fromEmoji(emoji).name.replace("_", " ")).append(" ")
            }
        }.trim()
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

    private fun getFileNameFromUri(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.substringBeforeLast(".")
                } else null
            } else null
        }
    }
}
