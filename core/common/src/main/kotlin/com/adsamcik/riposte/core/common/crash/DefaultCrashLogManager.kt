package com.adsamcik.riposte.core.common.crash

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based implementation of [CrashLogManager].
 *
 * Reads crash report text files written by [CrashReportWriter].
 */
@Singleton
class DefaultCrashLogManager @Inject constructor(
    @CrashLogDir private val crashDir: File,
) : CrashLogManager {

    override fun getCrashLogCount(): Int =
        crashDir.listFiles()?.size ?: 0

    override fun getShareableReport(): String {
        val files = crashDir.listFiles() ?: return ""
        return files
            .sortedByDescending { it.name }
            .joinToString(separator = "\n\n") { it.readText() }
    }

    override fun clearAll() {
        crashDir.listFiles()?.forEach { it.delete() }
    }
}
