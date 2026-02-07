package com.mememymood.core.ml.worker

import android.content.Context
import android.graphics.BitmapFactory
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mememymood.core.ml.EmbeddingGenerator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

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
@HiltWorker
class EmbeddingGenerationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val embeddingGenerator: EmbeddingGenerator,
    private val embeddingRepository: EmbeddingWorkRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        try {
            // Get memes that need embedding generation
            val pendingMemes = embeddingRepository.getMemesNeedingEmbeddings(BATCH_SIZE)
            
            if (pendingMemes.isEmpty()) {
                return@withContext Result.success(
                    workDataOf(
                        KEY_PROCESSED_COUNT to 0,
                        KEY_REMAINING_COUNT to 0
                    )
                )
            }

            var successCount = 0
            var failureCount = 0

            pendingMemes.forEach { memeData ->
                try {
                    // Build search text from meme data
                    val searchText = buildSearchText(memeData)
                    
                    // Generate embedding
                    val embedding = embeddingGenerator.generateFromText(searchText)
                    
                    // Generate source text hash for tracking
                    val sourceHash = generateHash(searchText)
                    
                    // Store embedding
                    embeddingRepository.saveEmbedding(
                        memeId = memeData.id,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = CURRENT_MODEL_VERSION,
                        sourceTextHash = sourceHash
                    )
                    
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    android.util.Log.w(TAG, "Failed to generate embedding for meme ${memeData.id}", e)
                }

                // Update progress
                setProgressAsync(
                    workDataOf(
                        KEY_PROGRESS to ((successCount + failureCount) * 100 / pendingMemes.size)
                    )
                )
            }

            // Check if there are more memes to process
            val remainingCount = embeddingRepository.countMemesNeedingEmbeddings()
            
            val outputData = workDataOf(
                KEY_PROCESSED_COUNT to successCount,
                KEY_FAILED_COUNT to failureCount,
                KEY_REMAINING_COUNT to remainingCount
            )

            // If there are more memes, schedule another run
            if (remainingCount > 0) {
                return@withContext Result.success(outputData)
            }

            Result.success(outputData)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Embedding generation work failed", e)
            if (runAttemptCount < MAX_RETRY_COUNT) {
                Result.retry()
            } else {
                Result.failure(
                    workDataOf(KEY_ERROR_MESSAGE to e.message)
                )
            }
        }
    }

    private fun buildSearchText(memeData: MemeDataForEmbedding): String {
        return buildString {
            memeData.title?.let { append(it).append(" ") }
            memeData.description?.let { append(it).append(" ") }
            memeData.textContent?.let { append(it).append(" ") }
            memeData.emojiNames?.let { append(it).append(" ") }
        }.trim()
    }

    private fun encodeEmbedding(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(embedding.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    private fun generateHash(text: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(text.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "EmbeddingGenWorker"
        
        const val WORK_NAME = "embedding_generation_work"
        const val BATCH_SIZE = 20
        const val MAX_RETRY_COUNT = 3
        const val CURRENT_MODEL_VERSION = "embeddinggemma:1.0.0"
        
        // Output data keys
        const val KEY_PROCESSED_COUNT = "processed_count"
        const val KEY_FAILED_COUNT = "failed_count"
        const val KEY_REMAINING_COUNT = "remaining_count"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR_MESSAGE = "error_message"

        /**
         * Enqueues the embedding generation work.
         * Uses unique work to prevent duplicate runs.
         */
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<EmbeddingGenerationWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request
                )
        }

        /**
         * Enqueues embedding regeneration work for outdated model versions.
         */
        fun enqueueRegeneration(context: Context, currentVersion: String) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true) // Regeneration can be expensive
                .build()

            val request = OneTimeWorkRequestBuilder<EmbeddingGenerationWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1,
                    TimeUnit.MINUTES
                )
                .addTag("${WORK_NAME}_regeneration")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${WORK_NAME}_regeneration",
                    ExistingWorkPolicy.REPLACE,
                    request
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
    val emojiNames: String?,
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
        sourceTextHash: String?
    )
    
    /**
     * Count memes that need embedding generation.
     */
    suspend fun countMemesNeedingEmbeddings(): Int
    
    /**
     * Mark embeddings with outdated model version for regeneration.
     */
    suspend fun markOutdatedEmbeddings(currentVersion: String)
}
