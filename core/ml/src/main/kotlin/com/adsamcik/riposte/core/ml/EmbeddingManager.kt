package com.adsamcik.riposte.core.ml

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.adsamcik.riposte.core.common.lifecycle.AppLifecycleTracker
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.ml.worker.EmbeddingGenerationWorker
import com.adsamcik.riposte.core.model.EmbeddingType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates all embedding-related operations.
 *
 * This class serves as the main entry point for:
 * - Generating embeddings for memes
 * - Storing and retrieving embeddings
 * - Managing embedding model versions
 * - Scheduling background embedding generation
 *
 * Usage:
 * ```kotlin
 * // Generate and store embedding for a meme
 * embeddingManager.generateAndStoreEmbedding(memeId, searchText)
 *
 * // Get embedding for a meme
 * val embedding = embeddingManager.getEmbedding(memeId)
 *
 * // Schedule background processing for pending memes
 * embeddingManager.scheduleBackgroundGeneration()
 * ```
 */
@Singleton
class EmbeddingManager
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val embeddingGenerator: EmbeddingGenerator,
        private val memeEmbeddingDao: MemeEmbeddingDao,
        private val versionManager: EmbeddingModelVersionManager,
        private val appLifecycleTracker: AppLifecycleTracker,
    ) {
        /**
         * Initializes the embedding model and resumes any incomplete indexing.
         *
         * Performs warm-up (model initialization), then checks for model upgrades
         * and schedules background generation if there is pending work.
         * All steps run sequentially in a single coroutine to avoid race conditions.
         *
         * Safe to call on any flavor — lite/simple generators treat warm-up as a no-op.
         */
        fun warmUpAndResumeIndexing(scope: CoroutineScope) {
            scope.launch {
                // 1. Initialize the embedding model
                try {
                    embeddingGenerator.initialize()
                    versionManager.clearInitializationFailure()
                    Timber.d("Embedding model warm-up completed")
                } catch (e: Exception) {
                    Timber.w(e, "Embedding model warm-up failed (non-fatal)")
                    try {
                        versionManager.recordInitializationFailure(getAppVersionCode())
                    } catch (ve: Exception) {
                        Timber.w(ve, "Failed to record initialization failure")
                    }
                }

                // 2. Resume incomplete indexing (runs after warm-up completes)
                try {
                    checkAndHandleModelUpgrade()

                    if (embeddingGenerator.initializationError == null) {
                        val stats = getStatistics()
                        if (!stats.isFullyIndexed) {
                            Timber.d(
                                "Resuming indexing: ${stats.pendingEmbeddingCount} pending, " +
                                    "${stats.regenerationNeededCount} regenerating",
                            )
                            scheduleBackgroundGeneration()
                        }
                    } else {
                        Timber.d("Skipping auto-reindex: model error present")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to resume incomplete indexing")
                }
            }

            // 3. Re-check for pending work whenever the app returns to foreground
            observeForegroundResume(scope)
        }

        /**
         * Generate and store an embedding for a meme.
         *
         * @param memeId The ID of the meme.
         * @param searchText The text to generate embedding from (title, description, etc.).
         * @param embeddingType The type of embedding slot (defaults to CONTENT).
         * @return True if successful, false otherwise.
         */
        suspend fun generateAndStoreEmbedding(
            memeId: Long,
            searchText: String,
            embeddingType: EmbeddingType = EmbeddingType.CONTENT,
        ): Boolean {
            return try {
                val embedding = embeddingGenerator.generateFromText(searchText)
                if (isZeroEmbedding(embedding)) {
                    Timber.w("Generated embedding is all zeros for meme $memeId, skipping storage")
                    return false
                }

                val entity =
                    MemeEmbeddingEntity(
                        memeId = memeId,
                        embeddingType = embeddingType.key,
                        embedding = encodeEmbedding(embedding),
                        dimension = embedding.size,
                        modelVersion = versionManager.currentModelVersion,
                        generatedAt = System.currentTimeMillis(),
                        sourceTextHash = generateHash(searchText),
                        needsRegeneration = false,
                    )

                memeEmbeddingDao.insertEmbedding(entity)
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to generate embedding for meme $memeId")
                false
            }
        }

        /**
         * Get the embedding for a meme.
         *
         * @param memeId The ID of the meme.
         * @return The embedding as FloatArray, or null if not found.
         */
        suspend fun getEmbedding(memeId: Long): FloatArray? {
            val entity = memeEmbeddingDao.getEmbeddingByMemeId(memeId) ?: return null
            return decodeEmbedding(entity.embedding)
        }

        /**
         * Check if a meme has a valid (non-outdated) embedding.
         */
        suspend fun hasValidEmbedding(memeId: Long): Boolean {
            return memeEmbeddingDao.hasValidEmbedding(memeId)
        }

        /**
         * Mark a meme's embedding for regeneration.
         * Call this when the meme's content changes.
         */
        suspend fun markForRegeneration(memeId: Long) {
            memeEmbeddingDao.markForRegeneration(memeId)
        }

        /**
         * Delete embedding for a meme.
         */
        suspend fun deleteEmbedding(memeId: Long) {
            memeEmbeddingDao.deleteEmbeddingByMemeId(memeId)
        }

        /**
         * Get embedding statistics.
         * The [EmbeddingStatistics.modelError] is only populated when the error
         * has been confirmed (multiple failures on the same app version).
         */
        suspend fun getStatistics(): EmbeddingStatistics {
            val validCount = memeEmbeddingDao.countValidEmbeddings()
            val pendingCount = memeEmbeddingDao.countMemesWithoutEmbeddings()
            val regenerationCount = memeEmbeddingDao.countEmbeddingsNeedingRegeneration()
            val versionCounts = memeEmbeddingDao.getEmbeddingCountByModelVersion()

            val rawError = embeddingGenerator.initializationError

            return EmbeddingStatistics(
                validEmbeddingCount = validCount,
                pendingEmbeddingCount = pendingCount,
                regenerationNeededCount = regenerationCount,
                currentModelVersion = versionManager.currentModelVersion,
                embeddingsByVersion = versionCounts.associate { it.modelVersion to it.count },
                modelError = rawError,
            )
        }

        /**
         * Observe embedding statistics as a Flow.
         */
        fun observeValidEmbeddingCount(): Flow<Int> {
            return memeEmbeddingDao.observeValidEmbeddingsCount()
        }

        /**
         * Schedule background embedding generation for pending memes.
         */
        fun scheduleBackgroundGeneration() {
            EmbeddingGenerationWorker.enqueue(context)
        }

        /**
         * Observes app lifecycle and re-checks for pending indexing work
         * whenever the app returns to the foreground. This catches cases where:
         * - The worker stopped due to temporary model failures
         * - New memes were added while the app was backgrounded
         * - The previous worker batch completed but more work remains
         */
        private fun observeForegroundResume(scope: CoroutineScope) {
            scope.launch {
                appLifecycleTracker.isInBackground
                    // Drop the initial value to only react to transitions
                    .drop(1)
                    // Only act when returning to foreground (false = foreground)
                    .filter { isBackground -> !isBackground }
                    .collectLatest {
                        try {
                            if (embeddingGenerator.initializationError != null) return@collectLatest
                            val stats = getStatistics()
                            if (!stats.isFullyIndexed) {
                                Timber.d(
                                    "App returned to foreground with pending indexing: " +
                                        "${stats.pendingEmbeddingCount} pending, " +
                                        "${stats.regenerationNeededCount} regenerating",
                                )
                                scheduleBackgroundGeneration()
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to check indexing status on foreground return")
                        }
                    }
            }
        }

        /**
         * Check for model upgrades and schedule regeneration if needed.
         */
        suspend fun checkAndHandleModelUpgrade() {
            if (versionManager.hasModelBeenUpgraded()) {
                // Mark all embeddings with old version for regeneration
                memeEmbeddingDao.markOutdatedForRegeneration(versionManager.currentModelVersion)

                // Update stored version
                versionManager.updateToCurrentVersion()

                // Schedule regeneration
                EmbeddingGenerationWorker.enqueueRegeneration(
                    context,
                    versionManager.currentModelVersion,
                )
            }
        }

        /**
         * Get information about the current embedding model.
         */
        fun getModelInfo(): EmbeddingModelInfo {
            return versionManager.getModelInfo()
        }

        private fun isZeroEmbedding(embedding: FloatArray): Boolean {
            return embedding.all { it == 0f }
        }

        private fun encodeEmbedding(embedding: FloatArray): ByteArray {
            val buffer =
                ByteBuffer.allocate(embedding.size * 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
            embedding.forEach { buffer.putFloat(it) }
            return buffer.array()
        }

        private fun decodeEmbedding(bytes: ByteArray): FloatArray {
            val floatArray = FloatArray(bytes.size / 4)
            ByteBuffer.wrap(bytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .asFloatBuffer()
                .get(floatArray)
            return floatArray
        }

        private fun generateHash(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
            // Truncate to 32 chars (128 bits) for storage efficiency while maintaining uniqueness
            return hash.take(16).joinToString("") { "%02x".format(it) }
        }

        private fun getAppVersionCode(): Long {
            return try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                PackageInfoCompat.getLongVersionCode(packageInfo)
            } catch (e: Exception) {
                Timber.w(e, "Failed to get app version code")
                0L
            }
        }
    }

/**
 * Statistics about embeddings in the database.
 */
data class EmbeddingStatistics(
    val validEmbeddingCount: Int,
    val pendingEmbeddingCount: Int,
    val regenerationNeededCount: Int,
    val currentModelVersion: String,
    val embeddingsByVersion: Map<String, Int>,
    val modelError: String? = null,
) {
    val totalPendingWork: Int
        get() = pendingEmbeddingCount + regenerationNeededCount

    val isFullyIndexed: Boolean
        get() = pendingEmbeddingCount == 0 && regenerationNeededCount == 0
}
