package com.adsamcik.riposte.feature.settings.data

import android.graphics.BitmapFactory
import com.adsamcik.riposte.core.database.dao.DuplicateDetectionDao
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.entity.PotentialDuplicateEntity
import com.adsamcik.riposte.core.ml.DHashCalculator
import com.adsamcik.riposte.feature.settings.domain.model.DuplicateGroup
import com.adsamcik.riposte.feature.settings.domain.model.MemeEntityMerger
import com.adsamcik.riposte.feature.settings.domain.model.MergeResult
import com.adsamcik.riposte.feature.settings.domain.model.ScanProgress
import com.adsamcik.riposte.feature.settings.domain.repository.DuplicateDetectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject

/**
 * Default implementation of [DuplicateDetectionRepository].
 * Computes perceptual hashes, finds near-duplicates, and performs smart merges.
 */
class DefaultDuplicateDetectionRepository @Inject constructor(
    private val dao: DuplicateDetectionDao,
    private val dHashCalculator: DHashCalculator,
    private val merger: MemeEntityMerger,
) : DuplicateDetectionRepository {

    override fun observePendingCount(): Flow<Int> = dao.getPendingDuplicateCount()

    override fun observeDuplicateGroups(): Flow<List<DuplicateGroup>> =
        dao.getPendingDuplicates().map { duplicates ->
            duplicates.mapNotNull { dup ->
                val meme1 = dao.getMemeById(dup.memeId1) ?: return@mapNotNull null
                val meme2 = dao.getMemeById(dup.memeId2) ?: return@mapNotNull null
                DuplicateGroup(
                    duplicateId = dup.id,
                    meme1 = meme1,
                    meme2 = meme2,
                    hammingDistance = dup.hammingDistance,
                    detectionMethod = dup.detectionMethod,
                )
            }
        }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override fun runDuplicateScan(maxHammingDistance: Int): Flow<ScanProgress> = flow {
        // Phase 1: Compute perceptual hashes for un-hashed memes
        val unhashed = dao.getMemesWithoutPerceptualHash()
        val totalToHash = unhashed.size
        var hashedCount = 0
        var duplicatesFound = 0

        emit(ScanProgress(hashedCount = 0, totalToHash = totalToHash, duplicatesFound = 0))

        for (meme in unhashed) {
            val bitmap = BitmapFactory.decodeFile(meme.filePath)
            if (bitmap != null) {
                val hash = dHashCalculator.calculate(bitmap)
                bitmap.recycle()
                if (hash != null) {
                    dao.updatePerceptualHash(meme.id, hash)
                }
            }
            hashedCount++
            if (hashedCount % HASH_PROGRESS_INTERVAL == 0 || hashedCount == totalToHash) {
                emit(ScanProgress(hashedCount = hashedCount, totalToHash = totalToHash, duplicatesFound = 0))
            }
        }

        // Phase 2: Clear old pending duplicates and find new ones
        dao.clearPendingDuplicates()

        val allHashed = dao.getMemesWithPerceptualHash()
        val now = System.currentTimeMillis()
        val newDuplicates = mutableListOf<PotentialDuplicateEntity>()

        // Check exact hash duplicates first
        val hashGroups = allHashed.filter { it.fileHash != null }.groupBy { it.fileHash }
        for ((_, memes) in hashGroups) {
            if (memes.size > 1) {
                for (i in memes.indices) {
                    for (j in i + 1 until memes.size) {
                        val id1 = minOf(memes[i].id, memes[j].id)
                        val id2 = maxOf(memes[i].id, memes[j].id)
                        if (!dao.pairExists(id1, id2)) {
                            newDuplicates.add(
                                PotentialDuplicateEntity(
                                    memeId1 = id1,
                                    memeId2 = id2,
                                    hammingDistance = 0,
                                    detectionMethod = "exact",
                                    detectedAt = now,
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Then check perceptual hash similarity
        for (i in allHashed.indices) {
            for (j in i + 1 until allHashed.size) {
                processPerceptualDuplicatePair(
                    allHashed[i], allHashed[j], maxHammingDistance, newDuplicates, now,
                )?.let { newDuplicates.add(it) }
            }
            // Emit progress during comparison phase
            if (i % COMPARE_PROGRESS_INTERVAL == 0) {
                duplicatesFound = newDuplicates.size
                emit(
                    ScanProgress(
                        hashedCount = totalToHash,
                        totalToHash = totalToHash,
                        duplicatesFound = duplicatesFound,
                    ),
                )
            }
        }

        // Batch insert all found duplicates
        if (newDuplicates.isNotEmpty()) {
            dao.insertPotentialDuplicates(newDuplicates)
        }

        emit(
            ScanProgress(
                hashedCount = totalToHash,
                totalToHash = totalToHash,
                duplicatesFound = newDuplicates.size,
                isComplete = true,
            ),
        )
    }

    override suspend fun dismissDuplicate(duplicateId: Long) {
        dao.dismissDuplicate(duplicateId)
    }

    override suspend fun mergeDuplicates(duplicateId: Long): MergeResult {
        val duplicates = dao.getPendingDuplicateById(duplicateId)
            ?: throw IllegalArgumentException("Duplicate not found: $duplicateId")

        val meme1 = dao.getMemeById(duplicates.memeId1)
            ?: error("Meme not found: ${duplicates.memeId1}")
        val meme2 = dao.getMemeById(duplicates.memeId2)
            ?: error("Meme not found: ${duplicates.memeId2}")

        val merged = merger.merge(meme1, meme2)

        dao.performMerge(
            winnerId = merged.winnerId,
            loserId = merged.loserId,
            duplicateId = duplicateId,
            emojiTagsJson = merged.emojiTagsJson,
            title = merged.title,
            description = merged.description,
            textContent = merged.textContent,
            searchPhrasesJson = merged.searchPhrasesJson,
            useCount = merged.useCount,
            viewCount = merged.viewCount,
            isFavorite = merged.isFavorite,
        )

        // Clean up the loser's file from disk
        try {
            File(merged.loserFilePath).delete()
        } catch (_: Exception) {
            // Best-effort cleanup
        }

        return MergeResult(
            winnerId = merged.winnerId,
            loserId = merged.loserId,
            loserFilePath = merged.loserFilePath,
        )
    }

    override suspend fun dismissAll() {
        // Get all pending and dismiss them
        // Since we can't easily get a non-Flow list, use clearPendingDuplicates
        // but we want "dismissed" not deleted, so we need a custom query
        dao.dismissAllPending()
    }

    override suspend fun mergeAll(): List<MergeResult> {
        val results = mutableListOf<MergeResult>()
        val pending = dao.getPendingDuplicatesList()
        for (dup in pending) {
            try {
                results.add(mergeDuplicates(dup.id))
            } catch (_: Exception) {
                // Skip pairs that can't be merged (already deleted, etc.)
            }
        }
        return results
    }

    /**
     * Check if two memes are perceptual duplicates and return a [PotentialDuplicateEntity] if so.
     * Returns null when the pair should be skipped (missing hashes, distance too large, or already known).
     */
    @Suppress("ReturnCount")
    private suspend fun processPerceptualDuplicatePair(
        meme1: MemeEntity,
        meme2: MemeEntity,
        maxHammingDistance: Int,
        existingDuplicates: List<PotentialDuplicateEntity>,
        now: Long,
    ): PotentialDuplicateEntity? {
        val hash1 = meme1.perceptualHash ?: return null
        val hash2 = meme2.perceptualHash ?: return null

        val distance = DHashCalculator.hammingDistance(hash1, hash2)
        if (distance > maxHammingDistance) return null

        val id1 = minOf(meme1.id, meme2.id)
        val id2 = maxOf(meme1.id, meme2.id)

        val alreadyExact = existingDuplicates.any { it.memeId1 == id1 && it.memeId2 == id2 }
        if (alreadyExact || dao.pairExists(id1, id2)) return null

        return PotentialDuplicateEntity(
            memeId1 = id1,
            memeId2 = id2,
            hammingDistance = distance,
            detectionMethod = "perceptual",
            detectedAt = now,
        )
    }

    companion object {
        private const val HASH_PROGRESS_INTERVAL = 5
        private const val COMPARE_PROGRESS_INTERVAL = 10
    }
}
