package com.mememymood.feature.gallery.domain.repository

import com.mememymood.core.model.ShareTarget
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing share target preferences and tracking.
 */
interface ShareTargetRepository {

    /**
     * Returns all known share targets ordered by usage frequency.
     */
    fun getShareTargets(): Flow<List<ShareTarget>>

    /**
     * Returns the top N most-used share targets.
     */
    suspend fun getTopShareTargets(limit: Int = 6): List<ShareTarget>

    /**
     * Records a share event for the given target.
     * Creates the target if it doesn't exist, otherwise increments share count.
     */
    suspend fun recordShare(target: ShareTarget)
}
