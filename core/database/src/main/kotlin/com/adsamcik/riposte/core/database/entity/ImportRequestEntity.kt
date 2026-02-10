package com.adsamcik.riposte.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persists an import request so that the [ImportWorker] can survive process death
 * and resume where it left off.
 */
@Entity(tableName = "import_requests")
data class ImportRequestEntity(
    @PrimaryKey val id: String,
    val status: String,
    val imageCount: Int,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val stagingDir: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_FAILED = "failed"
    }
}

/**
 * Individual image item within an [ImportRequestEntity].
 * Tracks per-item status so partially completed imports can be resumed.
 */
@Entity(
    tableName = "import_request_items",
    indices = [Index(value = ["requestId"])],
)
data class ImportRequestItemEntity(
    @PrimaryKey val id: String,
    val requestId: String,
    val stagedFilePath: String,
    val originalFileName: String,
    val emojis: String,
    val title: String?,
    val description: String?,
    val extractedText: String?,
    val status: String = ImportRequestEntity.STATUS_PENDING,
    val errorMessage: String? = null,
)
