package com.adsamcik.riposte.feature.import_feature.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

private const val ZIP_BYTES_PER_KB = 1024
private const val ZIP_IO_BUFFER_SIZE = 8192
private val SUPPORTED_IMAGE_EXTENSIONS =
    setOf(
        ".jpg",
        ".jpeg",
        ".png",
        ".webp",
        ".gif",
        ".bmp",
        ".tiff",
        ".tif",
        ".heic",
        ".heif",
        ".avif",
        ".jxl",
    )

internal fun fileSizeLimitMessage(): String {
    val maxMb = DefaultZipImporter.MAX_SINGLE_FILE_SIZE / ZIP_BYTES_PER_KB / ZIP_BYTES_PER_KB
    return "File size limit exceeded (max: ${maxMb}MB)"
}

internal fun jsonSizeLimitMessage(maxSize: Long): String =
    "JSON size limit exceeded (max: ${maxSize / ZIP_BYTES_PER_KB}KB)"

internal fun totalExtractionSizeLimitMessage(): String {
    val maxMb = DefaultZipImporter.MAX_TOTAL_EXTRACTION_SIZE / ZIP_BYTES_PER_KB / ZIP_BYTES_PER_KB
    return "Total extraction size limit exceeded (max: ${maxMb}MB)"
}

internal fun readBytesWithLimit(
    input: ZipInputStream,
    maxSize: Long,
    entryName: String,
    errors: MutableMap<String, String>,
): ByteArray? {
    val buffer = ByteArray(ZIP_IO_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var totalRead = 0L
    var bytesRead: Int

    while (input.read(buffer).also { bytesRead = it } != -1) {
        totalRead += bytesRead
        if (totalRead > maxSize) {
            errors[entryName] = jsonSizeLimitMessage(maxSize)
            return null
        }
        output.write(buffer, 0, bytesRead)
    }

    return output.toByteArray()
}

internal fun readBytesWithLimitStream(input: ZipInputStream, maxSize: Long): ByteArray? {
    val buffer = ByteArray(ZIP_IO_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var totalRead = 0L
    var bytesRead: Int

    while (input.read(buffer).also { bytesRead = it } != -1) {
        totalRead += bytesRead
        if (totalRead > maxSize) {
            return null
        }
        output.write(buffer, 0, bytesRead)
    }

    return output.toByteArray()
}

internal fun copyWithLimit(
    input: ZipInputStream,
    outputFile: File,
    maxSize: Long,
): Long {
    val buffer = ByteArray(ZIP_IO_BUFFER_SIZE)
    var totalWritten = 0L

    FileOutputStream(outputFile).use { output ->
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            totalWritten += bytesRead
            if (totalWritten > maxSize) {
                return -1
            }
            output.write(buffer, 0, bytesRead)
        }
    }

    return totalWritten
}

internal fun getSafeFileName(entryName: String): String? {
    val fileName = File(entryName).name
    if (fileName.isEmpty() || fileName.startsWith(".") || fileName.contains("..")) {
        return null
    }
    return fileName
}

internal fun getSafeOutputFile(fileName: String, baseDir: File): File? {
    val outputFile = File(baseDir, fileName)
    val canonicalExtractDir = baseDir.canonicalPath
    val canonicalOutputPath = outputFile.canonicalPath
    return if (canonicalOutputPath.startsWith(canonicalExtractDir + File.separator)) {
        outputFile
    } else {
        null
    }
}

internal fun isImageFile(fileName: String): Boolean {
    val lowerName = fileName.lowercase()
    return SUPPORTED_IMAGE_EXTENSIONS.any { lowerName.endsWith(it) }
}
