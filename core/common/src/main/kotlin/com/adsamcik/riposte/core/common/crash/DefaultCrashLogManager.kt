package com.adsamcik.riposte.core.common.crash

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File-based implementation of [CrashLogManager].
 *
 * Reads crash report text files written by [CrashReportWriter].
 * Automatically prunes logs exceeding [MAX_CRASH_LOGS] at read-time.
 */
@Singleton
class DefaultCrashLogManager
    @Inject
    constructor(
        @param:CrashLogDir private val crashDir: File,
    ) : CrashLogManager {
        override fun getCrashLogCount(): Int = crashDir.listFiles()?.size ?: 0

        override fun getShareableReport(): CrashLogReport {
            val files = crashDir.listFiles() ?: return CrashLogReport(count = 0, text = "")
            val sorted = files.sortedByDescending { it.name }

            // Prune oldest logs beyond the cap
            if (sorted.size > MAX_CRASH_LOGS) {
                sorted.drop(MAX_CRASH_LOGS).forEach { it.delete() }
            }

            val kept = sorted.take(MAX_CRASH_LOGS)
            return CrashLogReport(
                count = kept.size,
                text = kept.joinToString(separator = "\n\n") { it.readText() },
            )
        }

        override fun clearAll() {
            crashDir.listFiles()?.forEach { it.delete() }
        }

        companion object {
            /** Maximum number of crash logs to retain. Oldest are pruned at read-time. */
            const val MAX_CRASH_LOGS = 20
        }
    }
