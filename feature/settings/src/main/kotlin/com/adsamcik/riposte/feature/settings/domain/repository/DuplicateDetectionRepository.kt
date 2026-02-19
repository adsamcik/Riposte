package com.adsamcik.riposte.feature.settings.domain.repository

import com.adsamcik.riposte.feature.settings.domain.model.DuplicateGroup
import com.adsamcik.riposte.feature.settings.domain.model.MergeResult
import com.adsamcik.riposte.feature.settings.domain.model.ScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for advanced duplicate detection operations.
 */
interface DuplicateDetectionRepository {

    /**
     * Observe the count of pending duplicate groups.
     */
    fun observePendingCount(): Flow<Int>

    /**
     * Observe all pending duplicate groups with their meme data.
     */
    fun observeDuplicateGroups(): Flow<List<DuplicateGroup>>

    /**
     * Run a full duplicate scan: compute perceptual hashes for un-hashed memes,
     * then find near-duplicates within the given Hamming distance threshold.
     * Emits progress updates.
     */
    fun runDuplicateScan(maxHammingDistance: Int): Flow<ScanProgress>

    /**
     * Dismiss a duplicate pair (won't be re-flagged).
     */
    suspend fun dismissDuplicate(duplicateId: Long)

    /**
     * Smart-merge two duplicate memes: keep the best, combine metadata, delete the rest.
     * Returns the file path of the deleted meme for disk cleanup.
     */
    suspend fun mergeDuplicates(duplicateId: Long): MergeResult

    /**
     * Dismiss all pending duplicates.
     */
    suspend fun dismissAll()

    /**
     * Merge all pending duplicates automatically.
     */
    suspend fun mergeAll(): List<MergeResult>
}
