package com.adsamcik.riposte.core.common.util

private const val BYTES_PER_KB = 1024L
private const val BYTES_PER_MB = 1_048_576L
private const val BYTES_PER_GB = 1_073_741_824L

fun formatFileSize(bytes: Long): String =
    when {
        bytes >= BYTES_PER_GB -> "%.1f GB".format(bytes / BYTES_PER_GB.toDouble())
        bytes >= BYTES_PER_MB -> "%.1f MB".format(bytes / BYTES_PER_MB.toDouble())
        bytes >= BYTES_PER_KB -> "%.1f KB".format(bytes / BYTES_PER_KB.toDouble())
        else -> "$bytes B"
    }
