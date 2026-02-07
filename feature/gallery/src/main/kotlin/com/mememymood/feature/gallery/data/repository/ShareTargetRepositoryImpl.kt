package com.mememymood.feature.gallery.data.repository

import com.mememymood.core.common.di.IoDispatcher
import com.mememymood.core.database.dao.ShareTargetDao
import com.mememymood.core.database.entity.ShareTargetEntity
import com.mememymood.core.model.ShareTarget
import com.mememymood.feature.gallery.domain.repository.ShareTargetRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of [ShareTargetRepository] backed by Room.
 */
class ShareTargetRepositoryImpl @Inject constructor(
    private val shareTargetDao: ShareTargetDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ShareTargetRepository {

    override fun getShareTargets(): Flow<List<ShareTarget>> =
        shareTargetDao.getShareTargets()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)

    override suspend fun getTopShareTargets(limit: Int): List<ShareTarget> =
        withContext(ioDispatcher) {
            shareTargetDao.getTopShareTargets(limit).map { it.toDomain() }
        }

    override suspend fun recordShare(target: ShareTarget) {
        withContext(ioDispatcher) {
            // Try to increment existing row first
            val updated = shareTargetDao.recordShare(target.packageName)
            if (updated == 0) {
                // First time sharing to this target — insert it
                shareTargetDao.upsertShareTarget(
                    ShareTargetEntity(
                        packageName = target.packageName,
                        activityName = target.activityName,
                        displayLabel = target.displayLabel,
                        shareCount = 1,
                        lastSharedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    private fun ShareTargetEntity.toDomain() = ShareTarget(
        packageName = packageName,
        activityName = activityName,
        displayLabel = displayLabel,
        shareCount = shareCount,
        lastSharedAt = lastSharedAt,
    )
}
