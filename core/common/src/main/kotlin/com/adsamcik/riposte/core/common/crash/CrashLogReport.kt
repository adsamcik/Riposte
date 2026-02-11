package com.adsamcik.riposte.core.common.crash

/**
 * Atomic snapshot of crash log state returned by [CrashLogManager.getShareableReport].
 *
 * Bundles the report text with the count of included reports so that callers
 * get a consistent view without needing a separate [CrashLogManager.getCrashLogCount] call.
 *
 * @property count Number of crash reports included in [text] (post-prune).
 * @property text All crash reports concatenated newest-first, separated by blank lines.
 *               Empty string when there are no reports.
 */
data class CrashLogReport(
    val count: Int,
    val text: String,
)
