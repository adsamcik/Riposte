package com.adsamcik.riposte.feature.settings.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.adsamcik.riposte.feature.settings.domain.repository.DuplicateDetectionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Background worker that scans the meme library for near-duplicates
 * using perceptual hashing (dHash).
 *
 * Enqueued from the duplicate detection settings screen or automatically after imports.
 */
@HiltWorker
class DuplicateScanWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted params: WorkerParameters,
        private val repository: DuplicateDetectionRepository,
    ) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val maxDistance = inputData.getInt(KEY_MAX_HAMMING_DISTANCE, DEFAULT_MAX_DISTANCE)

        return try {
            repository.runDuplicateScan(maxDistance)
                .onEach { progress ->
                    val total = progress.totalToHash.coerceAtLeast(1)
                    val percentage = (progress.hashedCount * PERCENTAGE_MAX) / total
                    setProgressAsync(
                        workDataOf(
                            KEY_PROGRESS to percentage,
                            KEY_HASHED_COUNT to progress.hashedCount,
                            KEY_TOTAL_TO_HASH to progress.totalToHash,
                            KEY_DUPLICATES_FOUND to progress.duplicatesFound,
                            KEY_IS_COMPLETE to progress.isComplete,
                        ),
                    )
                }
                .collect()
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Duplicate scan failed")
            if (runAttemptCount < MAX_RETRIES) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "duplicate_scan"
        const val KEY_MAX_HAMMING_DISTANCE = "max_hamming_distance"
        const val KEY_PROGRESS = "progress"
        const val KEY_HASHED_COUNT = "hashed_count"
        const val KEY_TOTAL_TO_HASH = "total_to_hash"
        const val KEY_DUPLICATES_FOUND = "duplicates_found"
        const val KEY_IS_COMPLETE = "is_complete"

        private const val DEFAULT_MAX_DISTANCE = 10
        private const val MAX_RETRIES = 3
        private const val PERCENTAGE_MAX = 100
        private const val BACKOFF_DELAY_SECONDS = 30L

        /**
         * Enqueue a duplicate scan with the specified sensitivity.
         */
        fun enqueue(context: Context, maxHammingDistance: Int = DEFAULT_MAX_DISTANCE) {
            val request = OneTimeWorkRequestBuilder<DuplicateScanWorker>()
                .setInputData(workDataOf(KEY_MAX_HAMMING_DISTANCE to maxHammingDistance))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_SECONDS, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
