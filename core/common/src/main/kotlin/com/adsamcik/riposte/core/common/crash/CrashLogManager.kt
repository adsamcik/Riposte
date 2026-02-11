package com.adsamcik.riposte.core.common.crash

/**
 * Read-only access to stored crash reports.
 *
 * Implementations are expected to be injected via Hilt; the actual
 * writing of crash files is handled by [CrashReportWriter].
 */
interface CrashLogManager {
    /** Number of crash report files currently stored. */
    fun getCrashLogCount(): Int

    /**
     * Returns a [CrashLogReport] containing all crash reports concatenated
     * into a single string suitable for sharing, along with the count of
     * included reports.
     *
     * The count and text are derived from a single directory snapshot,
     * so they are guaranteed to be consistent with each other.
     *
     * Reports are ordered newest-first and separated by a blank line.
     * Reports beyond [DefaultCrashLogManager.MAX_CRASH_LOGS] are pruned
     * before building the result.
     */
    fun getShareableReport(): CrashLogReport

    /** Deletes all stored crash reports. */
    fun clearAll()
}
