package com.mememymood.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks share target apps and their usage frequency.
 * Used to populate the quick share bottom sheet with most-used apps.
 */
@Entity(
    tableName = "share_targets",
    indices = [
        Index(value = ["shareCount"]),
        Index(value = ["lastSharedAt"]),
    ],
)
data class ShareTargetEntity(
    @PrimaryKey
    val packageName: String,
    val activityName: String,
    val displayLabel: String,
    val shareCount: Int = 0,
    val lastSharedAt: Long? = null,
)
