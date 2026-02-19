package com.adsamcik.riposte.feature.import_feature.data.worker

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.riposte.core.common.AppConstants
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.database.dao.ImportRequestDao
import com.adsamcik.riposte.core.database.entity.ImportRequestEntity
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

/**
 * WorkManager worker that processes meme imports in the background.
 *
 * - Reads staged images and metadata from [ImportRequestDao]
 * - Imports each image via [ImportRepository.importImage]
 * - Reports progress via [setProgress] and updates the notification
 * - Conditionally promotes to a foreground service when the app is backgrounded
 * - Survives process death by tracking per-item status in Room
 */
@HiltWorker
class ImportWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val importRepository: ImportRepository,
        private val importRequestDao: ImportRequestDao,
        private val appLifecycleTracker: AppLifecycleTracker,
        private val notificationManager: ImportNotificationManager,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val requestId = inputData.getString(KEY_REQUEST_ID) ?: return Result.failure()
            val request = importRequestDao.getRequest(requestId) ?: return Result.failure()
            Timber.d("Starting import worker for request %s with %d images", requestId, request.imageCount)

            notificationManager.createChannel()

            // Mark request as in-progress
            importRequestDao.updateRequestProgress(
                id = requestId,
                status = ImportRequestEntity.STATUS_IN_PROGRESS,
                completed = request.completedCount,
                failed = request.failedCount,
                updatedAt = System.currentTimeMillis(),
            )

            // Promote to foreground if app is already backgrounded
            maybePromoteToForeground(request.completedCount, request.imageCount)

            val pendingItems = importRequestDao.getPendingItems(requestId)
            var completed = request.completedCount
            var failed = request.failedCount

            for (item in pendingItems) {
                if (isStopped) break

                val stagedFile = File(item.stagedFilePath)
                val uri = Uri.fromFile(stagedFile)

                val metadataJsonValue = item.metadataJson
                val metadata =
                    if (metadataJsonValue != null) {
                        try {
                            kotlinx.serialization.json.Json.decodeFromString<MemeMetadata>(
                                metadataJsonValue,
                            )
                        } catch (e: kotlinx.serialization.SerializationException) {
                            Timber.w(e, "Failed to parse metadata JSON in import worker")
                            null
                        }
                    } else {
                        val emojis = item.emojis.split(",").filter { it.isNotBlank() }
                        if (emojis.isNotEmpty()) {
                            MemeMetadata(
                                emojis = emojis,
                                title = item.title,
                                description = item.description,
                                textContent = item.extractedText,
                            )
                        } else {
                            null
                        }
                    }

                val result = importRepository.importImage(uri, metadata)
                if (result.isSuccess) {
                    completed++
                    importRequestDao.updateItemStatus(
                        itemId = item.id,
                        status = ImportRequestEntity.STATUS_COMPLETED,
                    )
                } else {
                    Timber.w("Failed to import item %s: %s", item.id, result.exceptionOrNull()?.message)
                    failed++
                    importRequestDao.updateItemStatus(
                        itemId = item.id,
                        status = ImportRequestEntity.STATUS_FAILED,
                        errorMessage = result.exceptionOrNull()?.message,
                    )
                }

                // Update request progress in DB
                importRequestDao.updateRequestProgress(
                    id = requestId,
                    status = ImportRequestEntity.STATUS_IN_PROGRESS,
                    completed = completed,
                    failed = failed,
                    updatedAt = System.currentTimeMillis(),
                )

                // Report progress to WorkManager observers
                setProgress(
                    workDataOf(
                        KEY_COMPLETED to completed,
                        KEY_FAILED to failed,
                        KEY_TOTAL to request.imageCount,
                    ),
                )

                // Update foreground notification if active
                maybePromoteToForeground(completed, request.imageCount)
            }
            Timber.i("Import complete: %d succeeded, %d failed out of %d total", completed, failed, request.imageCount)

            // Cleanup staging directory
            val stagingDir = File(request.stagingDir)
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
                Timber.d("Cleaned up staging directory: %s", stagingDir.absolutePath)
            }

            // Final status
            val finalStatus =
                if (failed == request.imageCount) {
                    ImportRequestEntity.STATUS_FAILED
                } else {
                    ImportRequestEntity.STATUS_COMPLETED
                }

            importRequestDao.updateRequestProgress(
                id = requestId,
                status = finalStatus,
                completed = completed,
                failed = failed,
                updatedAt = System.currentTimeMillis(),
            )

            // Cleanup old completed requests (>24h)
            val dayAgo = System.currentTimeMillis() - DAY_IN_MILLIS
            importRequestDao.cleanupOldRequestItems(dayAgo)
            importRequestDao.cleanupOldRequests(dayAgo)

            // Show completion notification if app is in background
            if (appLifecycleTracker.isInBackground.value) {
                notificationManager.showCompleteNotification(completed, failed)
            }

            return Result.success(
                workDataOf(
                    KEY_COMPLETED to completed,
                    KEY_FAILED to failed,
                    KEY_TOTAL to request.imageCount,
                ),
            )
        }

        private suspend fun maybePromoteToForeground(
            current: Int,
            total: Int,
        ) {
            if (appLifecycleTracker.isInBackground.value) {
                setForeground(createForegroundInfo(current, total))
            }
        }

        @SuppressLint("SpecifyForegroundServiceType") // Declared in app manifest
        private fun createForegroundInfo(
            current: Int,
            total: Int,
        ): ForegroundInfo {
            val notification = notificationManager.buildProgressNotification(current, total)
            return ForegroundInfo(
                ImportNotificationManager.NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }

        companion object {
            const val KEY_REQUEST_ID = "request_id"
            const val KEY_COMPLETED = "completed"
            const val KEY_FAILED = "failed"
            const val KEY_TOTAL = "total"
            private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

            /**
             * Enqueues an import worker for the given request.
             * Uses [ExistingWorkPolicy.APPEND_OR_REPLACE] so a new import
             * waits for any existing one to finish.
             */
            fun enqueue(
                context: Context,
                requestId: String,
            ): Data {
                val inputData = workDataOf(KEY_REQUEST_ID to requestId)
                val request =
                    OneTimeWorkRequestBuilder<ImportWorker>()
                        .setInputData(inputData)
                        .addTag(AppConstants.IMPORT_WORK_NAME)
                        .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    AppConstants.IMPORT_WORK_NAME,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    request,
                )

                return inputData
            }
        }
    }
