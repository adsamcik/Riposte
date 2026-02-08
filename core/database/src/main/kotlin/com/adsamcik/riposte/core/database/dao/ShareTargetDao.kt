package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.riposte.core.database.entity.ShareTargetEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for share target tracking.
 */
@Dao
interface ShareTargetDao {

    /**
     * Returns all share targets ordered by usage frequency (most-used first).
     */
    @Query("SELECT * FROM share_targets ORDER BY shareCount DESC, lastSharedAt DESC")
    fun getShareTargets(): Flow<List<ShareTargetEntity>>

    /**
     * Returns the top N most-used share targets.
     */
    @Query("SELECT * FROM share_targets ORDER BY shareCount DESC, lastSharedAt DESC LIMIT :limit")
    suspend fun getTopShareTargets(limit: Int): List<ShareTargetEntity>

    /**
     * Increment share count and update timestamp for a target.
     * Inserts the row if it doesn't exist yet.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShareTarget(target: ShareTargetEntity)

    /**
     * Record a share to a specific target.
     */
    @Query("""
        UPDATE share_targets 
        SET shareCount = shareCount + 1, lastSharedAt = :timestamp 
        WHERE packageName = :packageName
    """)
    suspend fun recordShare(packageName: String, timestamp: Long = System.currentTimeMillis()): Int

    /**
     * Delete all share target history.
     */
    @Query("DELETE FROM share_targets")
    suspend fun clearAll()
}
