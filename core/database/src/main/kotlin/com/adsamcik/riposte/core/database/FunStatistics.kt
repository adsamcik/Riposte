package com.adsamcik.riposte.core.database

import com.adsamcik.riposte.core.database.dao.EmojiUsageStats
import com.adsamcik.riposte.core.database.dao.WeeklyCount

/**
 * Aggregated fun statistics about the meme library.
 * Computed from existing database data — no schema changes needed.
 */
data class FunStatistics(
    // Storage
    val totalStorageBytes: Long = 0,
    val averageFileSize: Long = 0,
    val largestFileSize: Long = 0,
    // Usage
    val totalUseCount: Int = 0,
    val totalViewCount: Int = 0,
    val maxViewCount: Int = 0,
    // Collection
    val totalMemes: Int = 0,
    val favoriteMemes: Int = 0,
    val uniqueEmojiCount: Int = 0,
    val topEmojis: List<EmojiUsageStats> = emptyList(),
    // Import
    val completedImports: Int = 0,
    val lastImportTimestamp: Long? = null,
    val totalImportedMemes: Int = 0,
    // Momentum
    val weeklyImportCounts: List<WeeklyCount> = emptyList(),
    // Milestone support
    val oldestImportTimestamp: Long? = null,
    val newestImportTimestamp: Long? = null,
    val distinctMimeTypes: Int = 0,
    val neverInteractedCount: Int = 0,
    val favoritesWithViews: Int = 0,
)
