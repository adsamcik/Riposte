package com.adsamcik.riposte.feature.gallery.domain.usecase

import android.content.Context
import androidx.work.WorkManager
import com.adsamcik.riposte.core.common.AppConstants
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Result of a single stale-import recovery.
 */
data class RecoveredImport(
    val completedCount: Int,
    val imageCount: Int,
)

/**
 * Detects import requests stuck in IN_PROGRESS with no active WorkManager work,
 * and marks them as completed or failed. Returns a list of recovered requests
 * so the caller can display notifications.
 */
class RecoverStaleImportsUseCase
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val importRequestDao: ImportRequestDao,
    ) {
        suspend operator fun invoke(staleThresholdMs: Long): List<RecoveredImport> {
            val staleThreshold = System.currentTimeMillis() - staleThresholdMs
            val staleRequests = importRequestDao.getStaleRequests(staleThreshold)
            val hasActiveWork = staleRequests.isNotEmpty() && hasActiveImportWork()

            return when {
                staleRequests.isEmpty() -> emptyList()
                hasActiveWork -> {
                    Timber.d("Import work still active, skipping stale recovery")
                    emptyList()
                }
                else -> recoverRequests(staleRequests)
            }
        }

        private suspend fun hasActiveImportWork(): Boolean {
            val workInfos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(AppConstants.IMPORT_WORK_NAME)
                .get()
            return workInfos.any { !it.state.isFinished }
        }

        private suspend fun recoverRequests(
            staleRequests: List<ImportRequestEntity>,
        ): List<RecoveredImport> =
            staleRequests.map { request ->
                Timber.w(
                    "Marking stale import %s as failed (%d completed, %d failed of %d)",
                    request.id,
                    request.completedCount,
                    request.failedCount,
                    request.imageCount,
                )
                importRequestDao.updateRequestProgress(
                    id = request.id,
                    status = if (request.completedCount > 0) {
                        ImportRequestEntity.STATUS_COMPLETED
                    } else {
                        ImportRequestEntity.STATUS_FAILED
                    },
                    completed = request.completedCount,
                    failed = request.failedCount,
                    updatedAt = System.currentTimeMillis(),
                )
                RecoveredImport(
                    completedCount = request.completedCount,
                    imageCount = request.imageCount,
                )
            }
    }
