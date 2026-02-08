package com.adsamcik.riposte.core.model

/**
 * Domain model representing a share target app.
 *
 * @property packageName Android package name of the target app.
 * @property activityName Fully qualified activity/component name.
 * @property displayLabel User-visible app name.
 * @property shareCount Number of times the user has shared to this app.
 * @property lastSharedAt Epoch millis of the last share, or null if never shared.
 */
data class ShareTarget(
    val packageName: String,
    val activityName: String,
    val displayLabel: String,
    val shareCount: Int = 0,
    val lastSharedAt: Long? = null,
)
