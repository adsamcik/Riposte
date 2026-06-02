package com.adsamcik.riposte.feature.share.data

import android.content.ClipData
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.adsamcik.riposte.core.common.share.ShareRepository
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.mapper.MemeMapper
import com.adsamcik.riposte.core.datastore.PreferencesDataStore
import com.adsamcik.riposte.core.ml.XmpMetadataHandler
import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.feature.share.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class ShareRepositoryImpl
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val memeDao: MemeDao,
        private val preferencesDataStore: PreferencesDataStore,
        private val imageProcessor: ImageProcessor,
        @Suppress("UnusedPrivateProperty") // retained for non-share code paths (e.g. bundle export)
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
                cleanupStaleShares()
                processToMediaStore(meme, config)
            }

        override suspend fun prepareMultipleForSharing(
            memes: List<Meme>,
            config: ShareConfig,
        ): Result<List<Uri>> =
            withContext(Dispatchers.IO) {
                if (memes.isEmpty()) return@withContext Result.success(emptyList())
                cleanupStaleShares()
                val uris = mutableListOf<Uri>()
                for (meme in memes) {
                    val result = processToMediaStore(meme, config)
                    val uri = result.getOrElse { error ->
                        // Roll back any URIs we've successfully published in this batch
                        uris.forEach { deleteMediaStoreUri(it) }
                        return@withContext Result.failure(error)
                    }
                    uris.add(uri)
                }
                Result.success(uris)
            }

        override suspend fun cleanupStaleShares(): Int =
            withContext(Dispatchers.IO) {
                cleanupCacheDir()
                cleanupMediaStoreShares()
            }

        /**
         * Process a meme into a MediaStore entry under [SHARE_RELATIVE_PATH] and return
         * the public content:// URI. The image is written while IS_PENDING=1 so it's
         * invisible to other apps, then published (IS_PENDING=0) once the bytes are on
         * disk so receivers can read it via their own READ_MEDIA_IMAGES permission —
         * which is what avoids Discord's grantUriPermission SecurityException.
         */
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        private fun processToMediaStore(
            meme: Meme,
            config: ShareConfig,
        ): Result<Uri> {
            val extension =
                when (config.format) {
                    ImageFormat.JPEG -> "jpg"
                    ImageFormat.PNG -> "png"
                    ImageFormat.WEBP -> "webp"
                    ImageFormat.GIF -> "gif"
                }
            val tempFile = File(shareCacheDir, "share_${meme.id}_${System.currentTimeMillis()}.$extension")

            val processResult =
                try {
                    imageProcessor.processImage(meme.filePath, config, tempFile)
                } catch (e: Exception) {
                    tempFile.delete()
                    return Result.failure(e)
                }

            if (processResult is ImageProcessor.ProcessResult.Error) {
                tempFile.delete()
                return Result.failure(Exception(processResult.message))
            }

            val displayName = "riposte_share_${meme.id}_${System.currentTimeMillis()}.$extension"
            val resolver = context.contentResolver
            val pendingValues =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, config.format.mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH, SHARE_RELATIVE_PATH)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

            val mediaUri =
                try {
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pendingValues)
                } catch (e: Exception) {
                    Timber.e(e, "MediaStore insert failed")
                    null
                }
            if (mediaUri == null) {
                tempFile.delete()
                return Result.failure(Exception("Failed to create MediaStore entry for sharing"))
            }

            return try {
                resolver.openOutputStream(mediaUri).use { output ->
                    requireNotNull(output) { "Failed to open MediaStore output stream" }
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                // Publish — IS_PENDING=0 means receivers with READ_MEDIA_IMAGES can read
                // it without relying on transient activity grants. This is what bypasses
                // the Discord ShareActivity crash.
                val publishValues = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                resolver.update(mediaUri, publishValues, null, null)
                Result.success(mediaUri)
            } catch (e: Exception) {
                Timber.e(e, "Failed to write share content to MediaStore")
                deleteMediaStoreUri(mediaUri)
                Result.failure(e)
            } finally {
                tempFile.delete()
            }
        }

        private fun deleteMediaStoreUri(uri: Uri) {
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Timber.d(e, "Failed to delete MediaStore share URI %s", uri)
            }
        }

        /**
         * Bulk-delete every Riposte share entry from MediaStore. Safe to call from
         * app start, before each new share, or as a periodic backstop — it only
         * touches files under [SHARE_RELATIVE_PATH] which we exclusively own.
         */
        private fun cleanupMediaStoreShares(): Int {
            return try {
                context.contentResolver.delete(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                    arrayOf("$SHARE_RELATIVE_PATH%"),
                )
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Timber.w(e, "Failed to bulk-clean MediaStore share entries")
                0
            }
        }

        private fun cleanupCacheDir() {
            val now = System.currentTimeMillis()
            shareCacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > CACHE_MAX_AGE_MS) {
                    file.delete()
                }
            }
        }

        override fun createShareIntent(
            uri: Uri,
            mimeType: String,
        ): Intent {
            val chooserTitle = context.getString(R.string.share_chooser_title)
            val baseIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, chooserTitle)
                    putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                    clipData = ClipData.newUri(context.contentResolver, chooserTitle, uri)
                    addFlags(SHARE_URI_PERMISSION_FLAGS)
                }

            val chooserIntent = Intent.createChooser(baseIntent, chooserTitle).apply {
                // Some launchers / chooser implementations don't propagate flags from the
                // wrapped intent reliably — set them on the chooser as well.
                addFlags(SHARE_URI_PERMISSION_FLAGS)
            }

            // Prioritize messaging apps at the top of the chooser
            val messagingIntents = resolveMessagingAppIntents(uri, mimeType)
            if (messagingIntents.isNotEmpty()) {
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, messagingIntents.toTypedArray())

                // On API 33+, exclude pinned components from the main list to avoid duplicates
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val excludedComponents = messagingIntents.mapNotNull { it.component }.toTypedArray()
                    chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents)
                }
            }

            return chooserIntent
        }

        override fun createMultipleShareIntent(
            uris: List<Uri>,
            mimeType: String,
        ): Intent {
            val baseIntent =
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = mimeType
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(SHARE_URI_PERMISSION_FLAGS)
                }
            return Intent.createChooser(baseIntent, null).apply {
                addFlags(SHARE_URI_PERMISSION_FLAGS)
            }
        }

        /**
         * Resolves explicit share intents for installed messaging apps.
         * Each intent targets a specific activity component so the system chooser
         * displays them correctly at the top via EXTRA_INITIAL_INTENTS.
         */
        private fun resolveMessagingAppIntents(
            uri: Uri,
            mimeType: String,
        ): List<Intent> {
            val pm = context.packageManager
            return MESSAGING_PACKAGES.mapNotNull { pkg ->
                val queryIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        setPackage(pkg)
                    }

                @Suppress("DEPRECATION")
                val resolveInfo =
                    pm.queryIntentActivities(queryIntent, PackageManager.MATCH_DEFAULT_ONLY)
                        .firstOrNull() ?: return@mapNotNull null

                Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(SHARE_URI_PERMISSION_FLAGS)
                    component =
                        ComponentName(
                            resolveInfo.activityInfo.packageName,
                            resolveInfo.activityInfo.name,
                        )
                }
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
                    val outputStream = resolver.openOutputStream(imageUri)
                        ?: run {
                            resolver.delete(imageUri, null, null)
                            return@withContext Result.failure(Exception("Failed to open output stream"))
                        }
                    outputStream.use { output ->
                        // Process and write
                        val tempFile = File(shareCacheDir, "temp_gallery_${System.currentTimeMillis()}")
                        val result = imageProcessor.processImage(meme.filePath, config, tempFile)

                        when (result) {
                            is ImageProcessor.ProcessResult.Error -> {
                                tempFile.delete()
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
                } catch (
                    @Suppress("TooGenericExceptionCaught") // I/O and parsing may throw various exceptions
                    e: Exception,
                ) {
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

        companion object {
            /**
             * MediaStore relative path under which transient share files live. The
             * leading "." hides the directory in well-behaved gallery apps and file
             * managers. We own everything under this path exclusively, which means
             * [cleanupStaleShares] can safely bulk-delete it.
             */
            internal const val SHARE_RELATIVE_PATH = "Pictures/.riposte-share/"

            /** Maximum age for cached temp processing files before cleanup (1 hour). */
            private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L

            /**
             * Flags applied to every outgoing share intent.
             *
             * - READ: required so the receiver can open the stream via the transient
             *   grant when their broad media permission isn't granted.
             * - WRITE: some receivers (e.g. Discord's React Native ShareActivity)
             *   internally call [android.content.Context.grantUriPermission] to forward
             *   the URI to their workers — WRITE lets that call succeed for FileProvider
             *   URIs. Harmless for MediaStore URIs; receiver can only mutate the
             *   specific shared file (no PREFIX flag), and we clean it up afterwards.
             * - PERSISTABLE: lets the receiver call takePersistableUriPermission() so the
             *   grant survives their process restart during upload retries.
             */
            private const val SHARE_URI_PERMISSION_FLAGS =
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

            /** Well-known messaging app packages, in priority order. */
            private val MESSAGING_PACKAGES =
                listOf(
                    "com.whatsapp",
                    "org.telegram.messenger",
                    "com.discord",
                    "org.thoughtcrime.securesms",
                    "com.facebook.orca",
                    "com.google.android.apps.messaging",
                    "com.slack",
                    "com.microsoft.teams",
                    "com.viber.voip",
                    "com.skype.raider",
                    "com.kakao.talk",
                    "jp.naver.line.android",
                    "com.tencent.mm",
                )
        }

        /**
         * Parse emoji tags from the stored JSON string.
         * Delegates to [MemeMapper.parseEmojiTagsJson] for consistent parsing across the app.
         */
        private fun parseEmojiTags(emojiTagsJson: String): List<EmojiTag> {
            return MemeMapper.parseEmojiTagsJson(emojiTagsJson)
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
