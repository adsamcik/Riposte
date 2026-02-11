package com.adsamcik.riposte.feature.share.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.adsamcik.riposte.core.common.AppConstants
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.feature.share.domain.repository.ShareRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject

class ShareRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val memeDao: MemeDao,
        private val preferencesDataStore: PreferencesDataStore,
        private val imageProcessor: ImageProcessor,
        private val xmpMetadataHandler: XmpMetadataHandler,
    ) : ShareRepository {
        private val shareCacheDir: File by lazy {
            File(context.cacheDir, "share_cache").also { it.mkdirs() }
        }

        override suspend fun getMeme(memeId: Long): Meme? =
            withContext(Dispatchers.IO) {
                memeDao.getMemeById(memeId)?.toDomain()
            }

        override suspend fun getDefaultShareConfig(): ShareConfig {
            val prefs = preferencesDataStore.sharingPreferences.first()
            return ShareConfig(
                format = prefs.defaultFormat,
                quality = prefs.defaultQuality,
                maxWidth = prefs.maxWidth,
                maxHeight = prefs.maxHeight,
                stripMetadata = prefs.stripMetadata,
            )
        }

        override suspend fun prepareForSharing(
            meme: Meme,
            config: ShareConfig,
        ): Result<Uri> =
            withContext(Dispatchers.IO) {
                try {
                    // Clear old cache files
                    clearOldCacheFiles()

                    // Create output file
                    val extension =
                        when (config.format) {
                            ImageFormat.JPEG -> "jpg"
                            ImageFormat.PNG -> "png"
                            ImageFormat.WEBP -> "webp"
                            ImageFormat.GIF -> "gif"
                        }
                    val outputFile = File(shareCacheDir, "share_${meme.id}_${System.currentTimeMillis()}.$extension")

                    // Process image
                    val result =
                        imageProcessor.processImage(
                            sourcePath = meme.filePath,
                            config = config,
                            outputFile = outputFile,
                        )

                    when (result) {
                        is ImageProcessor.ProcessResult.Error -> {
                            return@withContext Result.failure(Exception(result.message))
                        }
                        is ImageProcessor.ProcessResult.Success -> {
                            // Embed XMP metadata if not stripping
                            if (!config.stripMetadata) {
                                val metadata =
                                    MemeMetadata(
                                        schemaVersion = AppConstants.METADATA_SCHEMA_VERSION,
                                        emojis = meme.emojiTags.map { it.emoji },
                                        title = meme.title,
                                        description = meme.description,
                                        createdAt = Instant.now().toString(),
                                        appVersion = AppConstants.APP_VERSION,
                                    )
                                xmpMetadataHandler.writeMetadata(outputFile.absolutePath, metadata)
                            }

                            // Create FileProvider URI
                            val uri =
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    outputFile,
                                )

                            Result.success(uri)
                        }
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override fun createShareIntent(
            uri: Uri,
            mimeType: String,
        ): Intent {
            return Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        override suspend fun saveToGallery(
            meme: Meme,
            config: ShareConfig,
        ): Result<Uri> =
            withContext(Dispatchers.IO) {
                try {
                    val extension =
                        when (config.format) {
                            ImageFormat.JPEG -> "jpg"
                            ImageFormat.PNG -> "png"
                            ImageFormat.WEBP -> "webp"
                            ImageFormat.GIF -> "gif"
                        }
                    val fileName = "Riposte_${System.currentTimeMillis()}.$extension"

                    // Use MediaStore to save to gallery
                    val contentValues =
                        android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, config.format.mimeType)
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Riposte")
                        }

                    val resolver = context.contentResolver
                    val imageUri =
                        resolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues,
                        ) ?: return@withContext Result.failure(Exception("Failed to create media entry"))

                    // Write processed image to MediaStore
                    resolver.openOutputStream(imageUri)?.use { output ->
                        // Process and write
                        val tempFile = File(shareCacheDir, "temp_gallery_${System.currentTimeMillis()}")
                        val result = imageProcessor.processImage(meme.filePath, config, tempFile)

                        when (result) {
                            is ImageProcessor.ProcessResult.Error -> {
                                resolver.delete(imageUri, null, null)
                                return@withContext Result.failure(Exception(result.message))
                            }
                            is ImageProcessor.ProcessResult.Success -> {
                                tempFile.inputStream().use { input ->
                                    input.copyTo(output)
                                }
                                tempFile.delete()
                            }
                        }
                    }

                    Result.success(imageUri)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

        override suspend fun estimateFileSize(
            meme: Meme,
            config: ShareConfig,
        ): Long =
            withContext(Dispatchers.IO) {
                // Get original dimensions
                val options =
                    android.graphics.BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                android.graphics.BitmapFactory.decodeFile(meme.filePath, options)

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                // Calculate resized dimensions
                val maxWidth = config.maxWidth ?: originalWidth
                val maxHeight = config.maxHeight ?: originalHeight
                val ratio =
                    minOf(
                        maxWidth.toFloat() / originalWidth,
                        maxHeight.toFloat() / originalHeight,
                        // Don't upscale
                        1f,
                    )
                val newWidth = (originalWidth * ratio).toInt()
                val newHeight = (originalHeight * ratio).toInt()

                imageProcessor.estimateFileSize(newWidth, newHeight, config.format, config.quality)
            }

        private fun clearOldCacheFiles() {
            val now = System.currentTimeMillis()

            shareCacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > CACHE_MAX_AGE_MS) {
                    file.delete()
                }
            }
        }

        companion object {
            /** Maximum age for cached share files before cleanup (24 hours). */
            private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
        }

        /**
         * Parse emoji tags from the stored JSON string.
         * Handles both comma-separated format and potential JSON array format for backwards compatibility.
         */
        private fun parseEmojiTags(emojiTagsJson: String): List<EmojiTag> {
            if (emojiTagsJson.isBlank()) return emptyList()

            return try {
                // Standard format: comma-separated emoji characters
                emojiTagsJson.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { EmojiTag.fromEmoji(it) }
            } catch (e: Exception) {
                // If parsing fails, return empty list rather than crash
                emptyList()
            }
        }

        private fun com.adsamcik.riposte.core.database.entity.MemeEntity.toDomain(): Meme {
            return Meme(
                id = id,
                filePath = filePath,
                fileName = fileName,
                mimeType = mimeType,
                width = width,
                height = height,
                fileSizeBytes = fileSizeBytes,
                importedAt = importedAt,
                emojiTags = parseEmojiTags(emojiTagsJson),
                title = title,
                description = description,
                textContent = textContent,
                isFavorite = isFavorite,
                createdAt = createdAt,
                useCount = useCount,
            )
        }
    }
