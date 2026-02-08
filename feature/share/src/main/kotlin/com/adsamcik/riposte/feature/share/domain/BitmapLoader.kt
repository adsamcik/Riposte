package com.adsamcik.riposte.feature.share.domain

import android.graphics.Bitmap

/**
 * Interface for loading bitmaps and file information.
 * Abstraction over BitmapFactory and File operations for testability.
 */
interface BitmapLoader {
    /**
     * Loads a bitmap from the given file path.
     *
     * @param filePath The absolute path to the image file.
     * @return The decoded Bitmap, or null if decoding fails.
     */
    suspend fun loadBitmap(filePath: String): Bitmap?

    /**
     * Gets the size of a file in bytes.
     *
     * @param filePath The absolute path to the file.
     * @return The file size in bytes, or 0 if the file doesn't exist.
     */
    suspend fun getFileSize(filePath: String): Long
}
