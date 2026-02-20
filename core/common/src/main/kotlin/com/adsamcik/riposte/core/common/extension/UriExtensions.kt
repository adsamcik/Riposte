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
    if (scheme == "content") {
        queryColumn(context, OpenableColumns.DISPLAY_NAME)?.let { return it }
    }
    return path?.let { path ->
        val cut = path.lastIndexOf('/')
        if (cut != -1) path.substring(cut + 1) else path
    }
}

/**
 * Gets the file size from a content URI.
 */
fun Uri.getFileSize(context: Context): Long {
    if (scheme == "content") {
        return queryColumn(context, OpenableColumns.SIZE)?.toLongOrNull() ?: 0L
    }
    return 0L
}

private fun Uri.queryColumn(context: Context, column: String): String? {
    return context.contentResolver.query(this, arrayOf(column), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
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
