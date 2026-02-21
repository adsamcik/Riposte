package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for import request persistence, enabling WorkManager-based imports
 * to survive process death and resume from the last completed item.
 */
@Dao
interface ImportRequestDao {
    @Query("SELECT * FROM import_requests WHERE id = :id")
    suspend fun getRequest(id: String): ImportRequestEntity?

    @Query("SELECT * FROM import_requests WHERE status IN ('pending', 'in_progress') ORDER BY createdAt DESC")
    fun getActiveRequests(): Flow<List<ImportRequestEntity>>

    @Query("SELECT * FROM import_request_items WHERE requestId = :requestId AND status = 'pending'")
    suspend fun getPendingItems(requestId: String): List<ImportRequestItemEntity>

    @Query("SELECT * FROM import_request_items WHERE requestId = :requestId")
    suspend fun getAllItems(requestId: String): List<ImportRequestItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: ImportRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ImportRequestItemEntity>)

    @Query("UPDATE import_request_items SET status = :status, errorMessage = :errorMessage WHERE id = :itemId")
    suspend fun updateItemStatus(
        itemId: String,
        status: String,
        errorMessage: String? = null,
    )

    @Query(
        "UPDATE import_requests SET status = :status, completedCount = :completed, " +
            "failedCount = :failed, updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateRequestProgress(
        id: String,
        status: String,
        completed: Int,
        failed: Int,
        updatedAt: Long,
    )

    @Query("DELETE FROM import_requests WHERE status IN ('completed', 'failed') AND updatedAt < :before")
    suspend fun cleanupOldRequests(before: Long)

    @Query(
        """DELETE FROM import_request_items
            WHERE requestId IN (
                SELECT id FROM import_requests
                WHERE status IN ('completed', 'failed')
                AND updatedAt < :before
            )""",
    )
    suspend fun cleanupOldRequestItems(before: Long)

    /**
     * Get the number of completed import requests.
     */
    @Query("SELECT COUNT(*) FROM import_requests WHERE status = 'completed'")
    suspend fun getCompletedImportCount(): Int

    /**
     * Get the timestamp of the most recent completed import.
     */
    @Query("SELECT MAX(updatedAt) FROM import_requests WHERE status = 'completed'")
    suspend fun getLastCompletedImportTimestamp(): Long?

    /**
     * Get the total number of memes imported across all requests.
     */
    @Query("SELECT COALESCE(SUM(completedCount), 0) FROM import_requests")
    suspend fun getTotalImportedMemeCount(): Int

    /**
     * Atomically update an item's status and the parent request's progress
     * counters in a single transaction, preventing count drift if the worker
     * is killed between the two writes.
     */
    @Transaction
    suspend fun completeItem(
        itemId: String,
        itemStatus: String,
        errorMessage: String? = null,
        requestId: String,
        completed: Int,
        failed: Int,
    ) {
        updateItemStatus(itemId, itemStatus, errorMessage)
        updateRequestProgress(
            id = requestId,
            status = ImportRequestEntity.STATUS_IN_PROGRESS,
            completed = completed,
            failed = failed,
            updatedAt = System.currentTimeMillis(),
        )
    }

    /**
     * Find import requests stuck in IN_PROGRESS for longer than [staleThreshold]
     * (epoch millis). Used by the gallery watchdog to recover stale imports.
     */
    @Query(
        """SELECT * FROM import_requests
           WHERE status = 'in_progress' AND updatedAt < :staleThreshold""",
    )
    suspend fun getStaleRequests(staleThreshold: Long): List<ImportRequestEntity>
}
