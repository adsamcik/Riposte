package com.adsamcik.riposte.feature.share.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.ShareConfig
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for processing images before sharing.
 * Handles resizing, compression, and format conversion.
 */
@Singleton
class ImageProcessor
    @Inject
    constructor() {
        companion object {
            private const val PNG_BYTES_PER_PIXEL = 1.0
            private const val JPEG_BYTES_PER_PIXEL_FACTOR = 0.3
            private const val WEBP_BYTES_PER_PIXEL_FACTOR = 0.2
            private const val GIF_BYTES_PER_PIXEL = 0.5
            private const val QUALITY_PERCENT_DIVISOR = 100.0
            private const val MIN_ESTIMATED_FILE_SIZE = 1024L
        }

        /**
         * Process an image according to the share configuration.
         */
        fun processImage(
            sourcePath: String,
            config: ShareConfig,
            outputFile: File,
        ): ProcessResult {
            val originalBitmap =
                BitmapFactory.decodeFile(sourcePath)
                    ?: return ProcessResult.Error("Failed to load image")

            // Resize if needed
            val maxWidth = config.maxWidth ?: originalBitmap.width
            val maxHeight = config.maxHeight ?: originalBitmap.height
            val resizedBitmap = resizeBitmap(originalBitmap, maxWidth, maxHeight)

            // Save in target format with compression
            val success =
                saveBitmap(
                    bitmap = resizedBitmap,
                    file = outputFile,
                    format = config.format,
                    quality = config.quality,
                )

            // Save dimensions before recycling
            val resultWidth = resizedBitmap.width
            val resultHeight = resizedBitmap.height

            // Clean up intermediate bitmaps
            if (resizedBitmap != originalBitmap) {
                originalBitmap.recycle()
            }
            resizedBitmap.recycle()

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
                width = resultWidth,
                height = resultHeight,
                fileSize = outputFile.length(),
            )
        }

        /**
         * Resize bitmap to fit within max dimensions while maintaining aspect ratio.
         */
        fun resizeBitmap(
            bitmap: Bitmap,
            maxWidth: Int,
            maxHeight: Int,
        ): Bitmap {
            val width = bitmap.width
            val height = bitmap.height

            if (width <= maxWidth && height <= maxHeight) {
                return bitmap
            }

            val ratio =
                minOf(
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
         * Strip all EXIF metadata from an image.
         */
        fun stripExifData(filePath: String) {
            try {
                val exif = ExifInterface(filePath)

                // Clear common EXIF tags
                val tagsToRemove =
                    listOf(
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
            } catch (e: IOException) {
                // Ignore errors when stripping metadata
                Timber.d(e, "Failed to strip EXIF metadata")
            }
        }

        /**
         * Copy EXIF data from source to destination file.
         */
        private fun copyExifData(
            sourcePath: String,
            destPath: String,
        ) {
            try {
                val sourceExif = ExifInterface(sourcePath)
                val destExif = ExifInterface(destPath)

                val tagsToCopy =
                    listOf(
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
            } catch (e: IOException) {
                // Ignore errors when copying metadata
                Timber.d(e, "Failed to copy EXIF metadata")
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
                    val compressFormat =
                        when (format) {
                            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
                            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
                            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
                            ImageFormat.GIF -> Bitmap.CompressFormat.PNG // GIF not directly supported
                        }
                    bitmap.compress(compressFormat, quality, out)
                }
                true
            } catch (e: IOException) {
                Timber.e(e, "Failed to save processed bitmap")
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
            val bytesPerPixel =
                when (format) {
                    ImageFormat.PNG -> PNG_BYTES_PER_PIXEL
                    ImageFormat.JPEG -> (quality / QUALITY_PERCENT_DIVISOR) * JPEG_BYTES_PER_PIXEL_FACTOR
                    ImageFormat.WEBP -> (quality / QUALITY_PERCENT_DIVISOR) * WEBP_BYTES_PER_PIXEL_FACTOR
                    ImageFormat.GIF -> GIF_BYTES_PER_PIXEL
                }
            return (pixels * bytesPerPixel).toLong().coerceAtLeast(MIN_ESTIMATED_FILE_SIZE)
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
