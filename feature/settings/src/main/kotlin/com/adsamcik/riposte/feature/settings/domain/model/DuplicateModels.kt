package com.adsamcik.riposte.feature.settings.domain.model

import com.adsamcik.riposte.core.database.entity.MemeEntity

/**
 * Represents a group of potential duplicate memes with comparison metadata.
 */
data class DuplicateGroup(
    /** Database ID of the potential_duplicates row. */
    val duplicateId: Long,
    /** First meme in the pair. */
    val meme1: MemeEntity,
    /** Second meme in the pair. */
    val meme2: MemeEntity,
    /** Hamming distance (0 = identical, lower = more similar). */
    val hammingDistance: Int,
    /** Detection method: "exact" or "perceptual". */
    val detectionMethod: String,
)

/**
 * Result of a smart merge between two duplicate memes.
 */
data class MergeResult(
    /** The meme that was kept (with merged metadata). */
    val winnerId: Long,
    /** The meme that was deleted. */
    val loserId: Long,
    /** File path of the deleted meme (for disk cleanup). */
    val loserFilePath: String,
)

/**
 * Progress of a duplicate scan operation.
 */
data class ScanProgress(
    /** Number of memes hashed so far. */
    val hashedCount: Int,
    /** Total memes to hash. */
    val totalToHash: Int,
    /** Number of duplicate groups found so far. */
    val duplicatesFound: Int,
    /** Whether the scan is complete. */
    val isComplete: Boolean = false,
)
