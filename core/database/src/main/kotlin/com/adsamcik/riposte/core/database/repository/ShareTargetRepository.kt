package com.adsamcik.riposte.core.database.repository

import com.adsamcik.riposte.core.model.ShareTarget
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing share target preferences and tracking.
 */
interface ShareTargetRepository {
    fun getShareTargets(): Flow<List<ShareTarget>>

    suspend fun getTopShareTargets(limit: Int = 6): List<ShareTarget>

    suspend fun recordShare(target: ShareTarget)
}
