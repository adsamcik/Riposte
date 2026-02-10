package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    suspend fun updateItemStatus(itemId: String, status: String, errorMessage: String? = null)

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

    @Query("DELETE FROM import_request_items WHERE requestId IN (SELECT id FROM import_requests WHERE status IN ('completed', 'failed') AND updatedAt < :before)")
    suspend fun cleanupOldRequestItems(before: Long)
}
