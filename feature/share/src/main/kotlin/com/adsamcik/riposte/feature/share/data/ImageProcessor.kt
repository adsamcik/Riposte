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
            // GIF: copy original to preserve animation when no resize/strip is needed
            if (config.format == ImageFormat.GIF) {
                val needsResize = config.maxWidth != null || config.maxHeight != null
                if (!needsResize && !config.stripMetadata) {
                    return try {
                        File(sourcePath).copyTo(outputFile, overwrite = true)
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(sourcePath, boundsOpts)
                        ProcessResult.Success(
                            file = outputFile,
                            width = boundsOpts.outWidth,
                            height = boundsOpts.outHeight,
                            fileSize = outputFile.length(),
                        )
                    } catch (e: IOException) {
                        ProcessResult.Error("Failed to copy GIF: ${e.message}")
                    }
                } else {
                    Timber.w("GIF requested but modification needed — will re-encode as PNG")
                }
            }

            // First pass: get dimensions only
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourcePath, boundsOptions)

            val targetMaxWidth = config.maxWidth ?: boundsOptions.outWidth
            val targetMaxHeight = config.maxHeight ?: boundsOptions.outHeight

            // Calculate inSampleSize to avoid loading full-resolution image into memory
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(
                    boundsOptions.outWidth, boundsOptions.outHeight,
                    targetMaxWidth, targetMaxHeight,
                )
            }

            val originalBitmap =
                BitmapFactory.decodeFile(sourcePath, decodeOptions)
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

            val newWidth = (width * ratio).toInt().coerceAtLeast(1)
            val newHeight = (height * ratio).toInt().coerceAtLeast(1)

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
                        ExifInterface.TAG_IMAGE_UNIQUE_ID,
                        ExifInterface.TAG_IMAGE_DESCRIPTION,
                        ExifInterface.TAG_CAMERA_OWNER_NAME,
                        ExifInterface.TAG_BODY_SERIAL_NUMBER,
                        ExifInterface.TAG_LENS_SERIAL_NUMBER,
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
                            ImageFormat.GIF -> {
                                Timber.w("GIF re-encoding not supported — falling back to PNG")
                                Bitmap.CompressFormat.PNG
                            }
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

        private fun calculateInSampleSize(
            width: Int,
            height: Int,
            reqWidth: Int,
            reqHeight: Int,
        ): Int {
            var inSampleSize = 1
            if (width > reqWidth || height > reqHeight) {
                val halfWidth = width / 2
                val halfHeight = height / 2
                while ((halfWidth / inSampleSize) >= reqWidth && (halfHeight / inSampleSize) >= reqHeight) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }
