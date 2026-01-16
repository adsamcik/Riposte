package com.mememymood.feature.share.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import com.mememymood.core.model.ImageFormat
import com.mememymood.core.model.ShareConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for processing images before sharing.
 * Handles resizing, compression, format conversion, and watermarking.
 */
@Singleton
class ImageProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        private const val WATERMARK_TEXT = "Made with Meme My Mood"
        private const val WATERMARK_PADDING = 16f
        private const val WATERMARK_TEXT_SIZE = 24f
        private const val WATERMARK_ALPHA = 128
    }

    /**
     * Process an image according to the share configuration.
     */
    fun processImage(
        sourcePath: String,
        config: ShareConfig,
        outputFile: File,
    ): ProcessResult {
        val originalBitmap = BitmapFactory.decodeFile(sourcePath)
            ?: return ProcessResult.Error("Failed to load image")

        // Resize if needed
        val maxWidth = config.maxWidth ?: originalBitmap.width
        val maxHeight = config.maxHeight ?: originalBitmap.height
        val resizedBitmap = resizeBitmap(originalBitmap, maxWidth, maxHeight)

        // Add watermark if enabled
        val watermarkedBitmap = if (config.addWatermark) {
            addWatermark(resizedBitmap)
        } else {
            resizedBitmap
        }

        // Save in target format with compression
        val success = saveBitmap(
            bitmap = watermarkedBitmap,
            file = outputFile,
            format = config.format,
            quality = config.quality,
        )

        // Clean up intermediate bitmaps
        if (resizedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        if (watermarkedBitmap != resizedBitmap) {
            resizedBitmap.recycle()
        }
        watermarkedBitmap.recycle()

        if (!success) {
            return ProcessResult.Error("Failed to save processed image")
        }

        // Handle EXIF data
        if (config.stripMetadata) {
            stripExifData(outputFile.absolutePath)
        } else if (sourcePath != outputFile.absolutePath) {
            copyExifData(sourcePath, outputFile.absolutePath)
        }

        return ProcessResult.Success(
            file = outputFile,
            width = watermarkedBitmap.width,
            height = watermarkedBitmap.height,
            fileSize = outputFile.length(),
        )
    }

    /**
     * Resize bitmap to fit within max dimensions while maintaining aspect ratio.
     */
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val ratio = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height,
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Compress bitmap to target format and quality.
     */
    fun compressToFormat(
        bitmap: Bitmap,
        outputFile: File,
        format: ImageFormat,
        quality: Int,
    ): Boolean {
        return saveBitmap(bitmap, outputFile, format, quality)
    }

    /**
     * Add a semi-transparent watermark to the bottom-right corner.
     */
    fun addWatermark(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val paint = Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = WATERMARK_ALPHA
            textSize = WATERMARK_TEXT_SIZE * (bitmap.width / 1080f).coerceIn(0.5f, 2f)
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        }

        val textWidth = paint.measureText(WATERMARK_TEXT)
        val x = bitmap.width - textWidth - WATERMARK_PADDING
        val y = bitmap.height - WATERMARK_PADDING

        canvas.drawText(WATERMARK_TEXT, x, y, paint)

        return result
    }

    /**
     * Strip all EXIF metadata from an image.
     */
    fun stripExifData(filePath: String) {
        try {
            val exif = ExifInterface(filePath)
            
            // Clear common EXIF tags
            val tagsToRemove = listOf(
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_GPS_LATITUDE,
                ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE,
                ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP,
                ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_ARTIST,
                ExifInterface.TAG_COPYRIGHT,
                ExifInterface.TAG_USER_COMMENT,
            )

            tagsToRemove.forEach { tag ->
                exif.setAttribute(tag, null)
            }

            exif.saveAttributes()
        } catch (e: Exception) {
            // Ignore errors when stripping metadata
        }
    }

    /**
     * Copy EXIF data from source to destination file.
     */
    private fun copyExifData(sourcePath: String, destPath: String) {
        try {
            val sourceExif = ExifInterface(sourcePath)
            val destExif = ExifInterface(destPath)

            val tagsToCopy = listOf(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_IMAGE_WIDTH,
                ExifInterface.TAG_IMAGE_LENGTH,
            )

            tagsToCopy.forEach { tag ->
                sourceExif.getAttribute(tag)?.let { value ->
                    destExif.setAttribute(tag, value)
                }
            }

            destExif.saveAttributes()
        } catch (e: Exception) {
            // Ignore errors when copying metadata
        }
    }

    private fun saveBitmap(
        bitmap: Bitmap,
        file: File,
        format: ImageFormat,
        quality: Int,
    ): Boolean {
        return try {
            FileOutputStream(file).use { out ->
                val compressFormat = when (format) {
                    ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                    ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                    ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                    ImageFormat.GIF -> Bitmap.CompressFormat.PNG // GIF not directly supported
                }
                bitmap.compress(compressFormat, quality, out)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Estimate the output file size for given configuration.
     */
    fun estimateFileSize(
        width: Int,
        height: Int,
        format: ImageFormat,
        quality: Int,
    ): Long {
        // Rough estimation based on typical compression ratios
        val pixels = width.toLong() * height
        val bytesPerPixel = when (format) {
            ImageFormat.PNG -> 1.0 // PNG is lossless but compresses well
            ImageFormat.JPEG -> (quality / 100.0) * 0.3 // JPEG compression
            ImageFormat.WEBP -> (quality / 100.0) * 0.2 // WebP is more efficient
            ImageFormat.GIF -> 0.5
        }
        return (pixels * bytesPerPixel).toLong().coerceAtLeast(1024)
    }

    sealed class ProcessResult {
        data class Success(
            val file: File,
            val width: Int,
            val height: Int,
            val fileSize: Long,
        ) : ProcessResult()

        data class Error(val message: String) : ProcessResult()
    }
}
