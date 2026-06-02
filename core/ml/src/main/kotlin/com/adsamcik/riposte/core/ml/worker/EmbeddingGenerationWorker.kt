package com.adsamcik.riposte.core.ml.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.events.EmbeddingsReady
import com.adsamcik.riposte.core.events.EventBus
import com.adsamcik.riposte.core.ml.EmbeddingGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * WorkManager worker for generating embeddings for memes in the background.
 *
 * This worker processes memes that don't have embeddings or need regeneration.
 * It runs with low priority to avoid impacting app performance.
 *
 * Features:
 * - Batch processing with configurable batch size
 * - Exponential backoff on failure
 * - Progress reporting
 * - Model version tracking
 */
class EmbeddingWorkerDependencies @Inject constructor(
    val embeddingGenerator: EmbeddingGenerator,
    val embeddingRepository: EmbeddingWorkRepository,
    val appLifecycleTracker: AppLifecycleTracker,
    val notificationManager: EmbeddingNotificationManager,
    val eventBus: EventBus,
)

@HiltWorker
class EmbeddingGenerationWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        dependencies: EmbeddingWorkerDependencies,
    ) : CoroutineWorker(context, params) {
        private val embeddingGenerator = dependencies.embeddingGenerator
        private val embeddingRepository = dependencies.embeddingRepository
        private val appLifecycleTracker = dependencies.appLifecycleTracker
        private val notificationManager = dependencies.notificationManager
        private val eventBus = dependencies.eventBus
        private val slotGenerator = EmbeddingSlotGenerator(embeddingGenerator, embeddingRepository)

        override suspend fun getForegroundInfo(): ForegroundInfo {
            notificationManager.createChannel()
            val notification = notificationManager.buildProgressNotification(current = 0, total = 0)
            return ForegroundInfo(
                EmbeddingNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        }

        override suspend fun doWork(): Result =
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                Timber.i(
                    "Embedding generation starting (maxFetchSize=%d, attempt=%d)",
                    BATCH_FETCH_SIZE,
                    runAttemptCount + 1,
                )
                try {
                    notificationManager.createChannel()
                    val modelError = embeddingGenerator.initializationError
                    if (modelError != null) {
                        modelUnavailableFailure(modelError)
                    } else {
                        finishEmbeddingWork(processPendingEmbeddings(), startTime)
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // Worker must not crash - reports failure instead
                    e: Exception,
                ) {
                    handleWorkerFailure(e, startTime)
                }
            }

        private fun modelUnavailableFailure(modelError: String): Result {
            Timber.w("Embedding model unavailable (%s), skipping work", modelError)
            return Result.failure(workDataOf(KEY_ERROR_MESSAGE to modelError))
        }

        private data class EmbeddingWorkSummary(
            val totalSuccess: Int,
            val totalFailure: Int,
            val remainingCount: Int,
            val modelError: String?,
        )

        private suspend fun processPendingEmbeddings(): EmbeddingWorkSummary {
            var totalSuccess = 0
            var totalFailure = 0
            val initialTotal = embeddingRepository.countMemesNeedingEmbeddings().coerceAtLeast(1)
            val processedMemeIds = mutableSetOf<Long>()
            var keepProcessing = true
            var modelError: String? = null

            while (keepProcessing) {
                kotlinx.coroutines.yield()
                val pendingMemes = embeddingRepository.getMemesNeedingEmbeddings(BATCH_FETCH_SIZE)
                    .filter { it.id !in processedMemeIds }
                keepProcessing = pendingMemes.isNotEmpty()

                if (keepProcessing) {
                    processedMemeIds.addAll(pendingMemes.map { it.id })
                    val (successCount, failureCount) = processAdaptiveBatch(
                        pendingMemes,
                        totalSuccess + totalFailure,
                        initialTotal,
                    )
                    totalSuccess += successCount
                    totalFailure += failureCount
                    markBatchAttempted(pendingMemes)
                    val fullBatchFailed = successCount == 0 && failureCount > 0
                    modelError = detectBatchStopReason(successCount, failureCount)
                    keepProcessing = modelError == null && !fullBatchFailed && successCount + failureCount > 0
                    if (keepProcessing) {
                        kotlinx.coroutines.delay(INTER_BATCH_DELAY_MS)
                    }
                }
            }

            return EmbeddingWorkSummary(
                totalSuccess = totalSuccess,
                totalFailure = totalFailure,
                remainingCount = embeddingRepository.countMemesNeedingEmbeddings(),
                modelError = modelError,
            )
        }

        private suspend fun markBatchAttempted(pendingMemes: List<MemeDataForEmbedding>) {
            for (meme in pendingMemes) {
                try {
                    embeddingRepository.markMemeFullyAttempted(meme.id)
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Timber.d(e, "Failed to mark meme %d as attempted", meme.id)
                }
            }
        }

        private fun detectBatchStopReason(successCount: Int, failureCount: Int): String? =
            if (successCount == 0 && failureCount > 0) {
                embeddingGenerator.initializationError.also { postRunModelError ->
                    if (postRunModelError != null) {
                        Timber.w("Model error after batch: %s — giving up", postRunModelError)
                    } else {
                        Timber.w("Entire batch failed (%d items) — stopping", failureCount)
                    }
                }
            } else {
                null
            }

        private suspend fun finishEmbeddingWork(
            summary: EmbeddingWorkSummary,
            startTime: Long,
        ): Result {
            summary.modelError?.let { return Result.failure(workDataOf(KEY_ERROR_MESSAGE to it)) }
            val elapsed = System.currentTimeMillis() - startTime
            Timber.i(
                "Embedding generation done in %dms: %d ok, %d failed, %d remaining",
                elapsed,
                summary.totalSuccess,
                summary.totalFailure,
                summary.remainingCount,
            )
            if (summary.remainingCount == 0 && summary.totalSuccess > 0 && appLifecycleTracker.isInBackground.value) {
                notificationManager.showCompleteNotification(summary.totalSuccess, summary.totalFailure)
            }
            eventBus.emit(
                EmbeddingsReady(
                    processedCount = summary.totalSuccess,
                    failedCount = summary.totalFailure,
                    remainingCount = summary.remainingCount,
                ),
            )
            return Result.success(
                workDataOf(
                    KEY_PROCESSED_COUNT to summary.totalSuccess,
                    KEY_FAILED_COUNT to summary.totalFailure,
                    KEY_REMAINING_COUNT to summary.remainingCount,
                ),
            )
        }

        private fun handleWorkerFailure(e: Exception, startTime: Long): Result {
            val elapsed = System.currentTimeMillis() - startTime
            Timber.e(
                e,
                "Embedding generation failed after %dms (attempt %d/%d)",
                elapsed,
                runAttemptCount + 1,
                MAX_RETRY_COUNT,
            )
            return if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_ERROR_MESSAGE to e.message))
            }
        }

        /**
         * Processes a batch of memes with adaptive sizing based on device speed.
         * Measures the first item to estimate how many fit in the time budget,
         * yielding between items for responsiveness.
         */
        private suspend fun processAdaptiveBatch(
            pendingMemes: List<MemeDataForEmbedding>,
            previouslyProcessed: Int,
            overallTotal: Int,
        ): Pair<Int, Int> {
            var successCount = 0
            var failureCount = 0
            val batchStartTime = System.currentTimeMillis()

            // Process first item and measure duration
            val firstStart = System.currentTimeMillis()
            processOneEmbedding(pendingMemes.first()).let { ok -> if (ok) successCount++ else failureCount++ }
            val firstDuration = (System.currentTimeMillis() - firstStart).coerceAtLeast(MIN_ITEM_DURATION_MS)

            reportProgress(previouslyProcessed + successCount + failureCount, overallTotal)

            // Calculate how many more fit in budget
            val elapsedMs = System.currentTimeMillis() - batchStartTime
            val remainingBudgetMs = BATCH_BUDGET_MS - elapsedMs
            val additionalItems = (remainingBudgetMs / firstDuration).toInt()
                .coerceIn(0, pendingMemes.size - 1)

            Timber.d(
                "Adaptive embedding batch: first item took %dms, budget allows %d more (of %d pending)",
                firstDuration,
                additionalItems,
                pendingMemes.size - 1,
            )

            for (i in 1..additionalItems) {
                kotlinx.coroutines.yield()
                processOneEmbedding(pendingMemes[i]).let { ok -> if (ok) successCount++ else failureCount++ }
                reportProgress(previouslyProcessed + successCount + failureCount, overallTotal)
            }

            return Pair(successCount, failureCount)
        }

        /** Generates embeddings for a single meme. Returns true on success. */
        private suspend fun processOneEmbedding(memeData: MemeDataForEmbedding): Boolean =
            slotGenerator.process(memeData)

        /** High-water mark ensures reported progress never regresses. */
        private var lastReportedProgress = 0

        private fun reportProgress(processed: Int, total: Int) {
            val safeTotal = total.coerceAtLeast(1)
            val progress = (processed * PERCENTAGE_MULTIPLIER / safeTotal)
                .coerceIn(0, PERCENTAGE_MULTIPLIER)
            lastReportedProgress = maxOf(lastReportedProgress, progress)
            setProgressAsync(
                workDataOf(
                    KEY_PROGRESS to lastReportedProgress,
                    KEY_PROCESSED_COUNT to processed,
                    KEY_REMAINING_COUNT to (safeTotal - processed).coerceAtLeast(0),
                ),
            )
        }

        companion object {
            const val WORK_NAME = "embedding_generation_work"
            const val MAX_RETRY_COUNT = 3

            /**
             * Current model version — delegates to the single source of truth in
             * [com.adsamcik.riposte.core.ml.EmbeddingModelVersionManager].
             */
            val CURRENT_MODEL_VERSION = com.adsamcik.riposte.core.ml.EmbeddingModelVersionManager.CURRENT_VERSION
            private const val PERCENTAGE_MULTIPLIER = 100
            private const val BACKOFF_SECONDS = 30L

            /** Fetch this many memes per inner batch; adaptive logic trims based on device speed. */
            private const val BATCH_FETCH_SIZE = 50

            /**
             * Time budget per adaptive batch. The outer loop in doWork() runs multiple
             * batches continuously with a brief pause between them. This controls how
             * long each inner batch runs before yielding to the inter-batch delay.
             */
            private const val BATCH_BUDGET_MS = 2L * 60 * 1000

            /** Brief pause between batches to reduce thermal pressure. */
            private const val INTER_BATCH_DELAY_MS = 500L

            /** Floor for per-item duration to avoid division issues on very fast inference. */
            private const val MIN_ITEM_DURATION_MS = 50L

            // Output data keys
            const val KEY_PROCESSED_COUNT = "processed_count"
            const val KEY_FAILED_COUNT = "failed_count"
            const val KEY_REMAINING_COUNT = "remaining_count"
            const val KEY_PROGRESS = "progress"
            const val KEY_ERROR_MESSAGE = "error_message"

            /**
             * Enqueues the embedding generation work.
             * Uses KEEP to prevent duplicate runs when triggered externally.
             */
            fun enqueue(context: Context) {
                val constraints =
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()

                val request =
                    OneTimeWorkRequestBuilder<EmbeddingGenerationWorker>()
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                            BackoffPolicy.EXPONENTIAL,
                            BACKOFF_SECONDS,
                            TimeUnit.SECONDS,
                        )
                        .addTag(WORK_NAME)
                        .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        WORK_NAME,
                        ExistingWorkPolicy.KEEP,
                        request,
                    )
            }

        }
    }

/**
 * Data class containing meme information needed for embedding generation.
 */
data class MemeDataForEmbedding(
    val id: Long,
    val filePath: String,
    val title: String?,
    val description: String?,
    val textContent: String?,
    val searchPhrases: String?,
    val emojiTagsJson: String? = null,
    val basedOn: String? = null,
    val emotionsJson: String? = null,
)

/**
 * Repository interface for embedding work operations.
 * This abstracts the database operations needed by the worker.
 */
interface EmbeddingWorkRepository {
    /**
     * Get memes that need embedding generation.
     */
    suspend fun getMemesNeedingEmbeddings(limit: Int): List<MemeDataForEmbedding>

    /**
     * Save a generated embedding.
     */
    suspend fun saveEmbedding(
        memeId: Long,
        embedding: ByteArray,
        dimension: Int,
        modelVersion: String,
        sourceTextHash: String?,
        embeddingType: String = "content",
    )

    /**
     * Count memes that need embedding generation.
     */
    suspend fun countMemesNeedingEmbeddings(): Int

    /**
     * Delete embeddings with outdated model version.
     */
    suspend fun deleteOutdatedEmbeddings(currentVersion: String)

    /**
     * Mark a meme as having been fully attempted for embedding generation.
     * Persists the attempt count so future worker runs skip memes
     * that legitimately produce fewer than all embedding types.
     */
    suspend fun markMemeFullyAttempted(memeId: Long)
}
