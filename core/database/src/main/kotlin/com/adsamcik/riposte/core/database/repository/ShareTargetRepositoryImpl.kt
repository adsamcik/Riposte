package com.adsamcik.riposte.core.database.repository

import com.adsamcik.riposte.core.common.di.IoDispatcher
import com.adsamcik.riposte.core.common.repository.ShareTargetRepository
import com.adsamcik.riposte.core.database.dao.ShareTargetDao
import com.adsamcik.riposte.core.database.entity.ShareTargetEntity
import com.adsamcik.riposte.core.model.ShareTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ShareTargetRepositoryImpl
    @Inject
    constructor(
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
                val updated = shareTargetDao.recordShare(target.packageName)
                if (updated == 0) {
                    shareTargetDao.upsertShareTarget(
                        ShareTargetEntity(
                            packageName = target.packageName,
                            activityName = target.activityName,
                            displayLabel = target.displayLabel,
                            shareCount = 1,
                            lastSharedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }
        }

        private fun ShareTargetEntity.toDomain() =
            ShareTarget(
                packageName = packageName,
                activityName = activityName,
                displayLabel = displayLabel,
                shareCount = shareCount,
                lastSharedAt = lastSharedAt,
            )
    }
