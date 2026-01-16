package com.mememymood.core.common.extension

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Scales a bitmap to fit within the specified max dimensions while maintaining aspect ratio.
 */
fun Bitmap.scaleToFit(maxWidth: Int, maxHeight: Int): Bitmap {
    val ratioX = maxWidth.toFloat() / width
    val ratioY = maxHeight.toFloat() / height
    val ratio = minOf(ratioX, ratioY)

    if (ratio >= 1f) return this

    val newWidth = (width * ratio).toInt()
    val newHeight = (height * ratio).toInt()

    return Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
}

/**
 * Compresses a bitmap to JPEG/PNG/WEBP with the specified quality.
 */
fun Bitmap.compress(
    format: Bitmap.CompressFormat,
    quality: Int
): ByteArray {
    return ByteArrayOutputStream().use { stream ->
        compress(format, quality.coerceIn(0, 100), stream)
        stream.toByteArray()
    }
}

/**
 * Saves a bitmap to a file with the specified format and quality.
 */
fun Bitmap.saveToFile(
    file: File,
    format: Bitmap.CompressFormat,
    quality: Int
): Boolean {
    return try {
        FileOutputStream(file).use { stream ->
            compress(format, quality.coerceIn(0, 100), stream)
        }
        true
    } catch (e: Exception) {
        false
    }
}

/**
 * Loads a bitmap from a file with optional sample size for memory efficiency.
 */
fun File.loadBitmap(maxWidth: Int? = null, maxHeight: Int? = null): Bitmap? {
    if (!exists()) return null

    // First, decode bounds only
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeFile(absolutePath, options)

    // Calculate sample size
    if (maxWidth != null && maxHeight != null) {
        options.inSampleSize = calculateInSampleSize(
            options.outWidth,
            options.outHeight,
            maxWidth,
            maxHeight
        )
    }

    // Decode with sample size
    options.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(absolutePath, options)
}

private fun calculateInSampleSize(
    width: Int,
    height: Int,
    reqWidth: Int,
    reqHeight: Int
): Int {
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}
