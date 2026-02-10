package com.adsamcik.riposte.core.ml

import android.content.Context
import android.util.Log
import com.adsamcik.riposte.core.database.dao.MemeEmbeddingDao
import com.adsamcik.riposte.core.database.entity.MemeEmbeddingEntity
import com.adsamcik.riposte.core.model.EmbeddingType
import com.adsamcik.riposte.core.ml.worker.EmbeddingGenerationWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
class EmbeddingManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val embeddingGenerator: EmbeddingGenerator,
    private val memeEmbeddingDao: MemeEmbeddingDao,
    private val versionManager: EmbeddingModelVersionManager,
) {
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
                Log.w(TAG, "Generated embedding is all zeros for meme $memeId, skipping storage")
                return false
            }
            
            val entity = MemeEmbeddingEntity(
                memeId = memeId,
                embeddingType = embeddingType.key,
                embedding = encodeEmbedding(embedding),
                dimension = embedding.size,
                modelVersion = versionManager.currentModelVersion,
                generatedAt = System.currentTimeMillis(),
                sourceTextHash = generateHash(searchText),
                needsRegeneration = false
            )
            
            memeEmbeddingDao.insertEmbedding(entity)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to generate embedding for meme $memeId", e)
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
     */
    suspend fun getStatistics(): EmbeddingStatistics {
        val validCount = memeEmbeddingDao.countValidEmbeddings()
        val pendingCount = memeEmbeddingDao.countMemesWithoutEmbeddings()
        val regenerationCount = memeEmbeddingDao.countEmbeddingsNeedingRegeneration()
        val versionCounts = memeEmbeddingDao.getEmbeddingCountByModelVersion()
        
        return EmbeddingStatistics(
            validEmbeddingCount = validCount,
            pendingEmbeddingCount = pendingCount,
            regenerationNeededCount = regenerationCount,
            currentModelVersion = versionManager.currentModelVersion,
            embeddingsByVersion = versionCounts.associate { it.modelVersion to it.count }
        )
    }

    /**
     * Observe embedding statistics as a Flow.
     */
    fun observeValidEmbeddingCount(): Flow<Int> {
        return memeEmbeddingDao.observeAllValidEmbeddings().map { it.size }
    }

    /**
     * Schedule background embedding generation for pending memes.
     */
    fun scheduleBackgroundGeneration() {
        EmbeddingGenerationWorker.enqueue(context)
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
                versionManager.currentModelVersion
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
        val buffer = ByteBuffer.allocate(embedding.size * 4)
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

    companion object {
        private const val TAG = "EmbeddingManager"
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
    val embeddingsByVersion: Map<String, Int>
) {
    val totalPendingWork: Int
        get() = pendingEmbeddingCount + regenerationNeededCount
    
    val isFullyIndexed: Boolean
        get() = pendingEmbeddingCount == 0 && regenerationNeededCount == 0
}
