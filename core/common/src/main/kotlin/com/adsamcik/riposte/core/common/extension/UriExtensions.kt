package com.adsamcik.riposte.core.common.extension

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Gets the file name from a content URI.
 */
fun Uri.getFileName(context: Context): String? {
    var result: String? = null
    if (scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex)
                }
            }
        }
    }
    if (result == null) {
        result =
            path?.let { path ->
                val cut = path.lastIndexOf('/')
                if (cut != -1) path.substring(cut + 1) else path
            }
    }
    return result
}

/**
 * Gets the file size from a content URI.
 */
fun Uri.getFileSize(context: Context): Long {
    var size: Long = 0
    if (scheme == "content") {
        context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        }
    }
    return size
}

/**
 * Copies a content URI to a file in the app's cache directory.
 */
fun Uri.copyToFile(
    context: Context,
    destinationFile: File,
): Boolean {
    return try {
        context.contentResolver.openInputStream(this)?.use { input ->
            FileOutputStream(destinationFile).use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: IOException) {
        Timber.e(e, "Failed to copy URI to file")
        false
    }
}

/**
 * Gets the MIME type from a content URI.
 */
fun Uri.getMimeType(context: Context): String? {
    return context.contentResolver.getType(this)
}
