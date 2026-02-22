package com.adsamcik.riposte.feature.import_feature.data.worker

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
import com.adsamcik.riposte.core.database.entity.ImportRequestItemEntity
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.feature.import_feature.domain.repository.ImportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

/**
 * WorkManager worker that processes meme imports in the background.
 *
 * Uses adaptive batch sizing: imports the first item to measure device speed,
 * then fills the remaining time budget (7 min total, 3 min headroom = 4 min work).
 * Each batch re-enqueues itself if items remain, staying within WorkManager's
 * 10-minute execution window on any device.
 *
 * - Reads staged images and metadata from [ImportRequestDao]
 * - Imports each image via [ImportRepository.importImage]
 * - Reports progress via [setProgress] and updates the notification
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

            val pendingItems = importRequestDao.getPendingItems(requestId)
            if (pendingItems.isEmpty()) {
                return finalizeImport(request, requestId, request.completedCount, request.failedCount)
            }

            val (completed, failed, processedCount) = processAdaptiveBatch(
                requestId = requestId,
                pendingItems = pendingItems,
                startCompleted = request.completedCount,
                startFailed = request.failedCount,
                totalImageCount = request.imageCount,
            )

            val remaining = pendingItems.size - processedCount
            Timber.i(
                "Import batch complete: %d succeeded, %d failed out of %d total (%d remaining)",
                completed,
                failed,
                request.imageCount,
                remaining,
            )

            return if (remaining > 0) {
                importRequestDao.updateRequestProgress(
                    id = requestId,
                    status = ImportRequestEntity.STATUS_IN_PROGRESS,
                    completed = completed,
                    failed = failed,
                    updatedAt = System.currentTimeMillis(),
                )
                enqueue(applicationContext, requestId)
                Result.success(
                    workDataOf(
                        KEY_COMPLETED to completed,
                        KEY_FAILED to failed,
                        KEY_TOTAL to request.imageCount,
                    ),
                )
            } else {
                finalizeImport(request, requestId, completed, failed)
            }
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            notificationManager.createChannel()
            val notification = notificationManager.buildProgressNotification(0, 0)
            return ForegroundInfo(ImportNotificationManager.NOTIFICATION_ID, notification)
        }

        /**
         * Adaptive batch: import the first item to measure speed, then fill the
         * remaining time budget. Returns (completed, failed, itemsProcessed).
         */
        private suspend fun processAdaptiveBatch(
            requestId: String,
            pendingItems: List<ImportRequestItemEntity>,
            startCompleted: Int,
            startFailed: Int,
            totalImageCount: Int,
        ): Triple<Int, Int, Int> {
            var completed = startCompleted
            var failed = startFailed
            var processed = 0
            val batchStartTime = System.currentTimeMillis()

            // Import first item and measure duration
            val firstItem = pendingItems.first()
            val firstItemStart = System.currentTimeMillis()
            importSingleItem(firstItem, requestId, totalImageCount).let { success ->
                if (success) completed++ else failed++
            }
            processed++
            val firstItemDuration = System.currentTimeMillis() - firstItemStart
            val perItemMs = firstItemDuration.coerceAtLeast(MIN_ITEM_DURATION_MS)

            // Calculate how many more items fit in the work budget
            val elapsedMs = System.currentTimeMillis() - batchStartTime
            val remainingBudgetMs = WORK_BUDGET_MS - elapsedMs
            val additionalItems = (remainingBudgetMs / perItemMs).toInt()
                .coerceIn(0, pendingItems.size - 1)

            Timber.d(
                "Adaptive batch: first item took %dms, budget allows %d more (of %d pending)",
                firstItemDuration,
                additionalItems,
                pendingItems.size - 1,
            )

            // Process the rest of the adaptive batch
            for (i in 1..additionalItems) {
                if (isStopped) break
                val item = pendingItems[i]
                importSingleItem(item, requestId, totalImageCount).let { success ->
                    if (success) completed++ else failed++
                }
                processed++
            }

            return Triple(completed, failed, processed)
        }

        /** Imports a single item, updates DB status and progress. Returns true on success. */
        private suspend fun importSingleItem(
            item: ImportRequestItemEntity,
            requestId: String,
            totalImageCount: Int,
        ): Boolean {
            val stagedFile = File(item.stagedFilePath)

            // Validate staged file still exists before attempting import
            if (!stagedFile.exists()) {
                Timber.w("Staged file missing for item %s: %s", item.id, item.stagedFilePath)
                val currentRequest = importRequestDao.getRequest(requestId)
                val newFailed = (currentRequest?.failedCount ?: 0) + 1
                importRequestDao.completeItem(
                    itemId = item.id,
                    itemStatus = ImportRequestEntity.STATUS_FAILED,
                    errorMessage = "Staged file not found: ${stagedFile.name}",
                    requestId = requestId,
                    completed = currentRequest?.completedCount ?: 0,
                    failed = newFailed,
                )
                setProgress(
                    workDataOf(
                        KEY_COMPLETED to (currentRequest?.completedCount ?: 0),
                        KEY_FAILED to newFailed,
                        KEY_TOTAL to totalImageCount,
                    ),
                )
                return false
            }

            val uri = Uri.fromFile(stagedFile)
            val metadata = parseItemMetadata(item)

            val result = importRepository.importImage(uri, metadata)
            val success = result.isSuccess

            // Read current totals and atomically update item + request in one transaction
            val currentRequest = importRequestDao.getRequest(requestId)
            val newCompleted = (currentRequest?.completedCount ?: 0) + if (success) 1 else 0
            val newFailed = (currentRequest?.failedCount ?: 0) + if (!success) 1 else 0

            if (success) {
                importRequestDao.completeItem(
                    itemId = item.id,
                    itemStatus = ImportRequestEntity.STATUS_COMPLETED,
                    requestId = requestId,
                    completed = newCompleted,
                    failed = newFailed,
                )
            } else {
                Timber.w("Failed to import item %s: %s", item.id, result.exceptionOrNull()?.message)
                importRequestDao.completeItem(
                    itemId = item.id,
                    itemStatus = ImportRequestEntity.STATUS_FAILED,
                    errorMessage = result.exceptionOrNull()?.message,
                    requestId = requestId,
                    completed = newCompleted,
                    failed = newFailed,
                )
            }

            setProgress(
                workDataOf(
                    KEY_COMPLETED to newCompleted,
                    KEY_FAILED to newFailed,
                    KEY_TOTAL to totalImageCount,
                ),
            )
            return success
        }

        private suspend fun finalizeImport(
            request: ImportRequestEntity,
            requestId: String,
            completed: Int,
            failed: Int,
        ): Result {
            val stagingDir = File(request.stagingDir)
            if (stagingDir.exists()) {
                stagingDir.deleteRecursively()
                Timber.d("Cleaned up staging directory: %s", stagingDir.absolutePath)
            }

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

            val dayAgo = System.currentTimeMillis() - DAY_IN_MILLIS
            importRequestDao.cleanupOldRequestItems(dayAgo)
            importRequestDao.cleanupOldRequests(dayAgo)

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

        private fun parseItemMetadata(item: ImportRequestItemEntity): MemeMetadata? {
            val metadataJsonValue = item.metadataJson
            return if (metadataJsonValue != null) {
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
        }

        companion object {
            const val KEY_REQUEST_ID = "request_id"
            const val KEY_COMPLETED = "completed"
            const val KEY_FAILED = "failed"
            const val KEY_TOTAL = "total"
            private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

            /** 7 min total minus 3 min headroom = 4 min work budget per batch. */
            private const val WORK_BUDGET_MS = 4L * 60 * 1000

            /** Floor for per-item duration to avoid division issues on very fast imports. */
            private const val MIN_ITEM_DURATION_MS = 50L

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
