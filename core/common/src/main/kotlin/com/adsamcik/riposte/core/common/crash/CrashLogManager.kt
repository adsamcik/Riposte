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
     * Returns all crash reports concatenated into a single string
     * suitable for sharing (e.g., via an [android.content.Intent.ACTION_SEND]).
     *
     * Reports are ordered newest-first and separated by a blank line.
     * Returns an empty string when there are no crash logs.
     */
    fun getShareableReport(): String

    /** Deletes all stored crash reports. */
    fun clearAll()
}
