package com.adsamcik.riposte.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks potential duplicate meme pairs detected by perceptual or exact hashing.
 * Each row represents one pair relationship with a similarity score.
 */
@Entity(
    tableName = "potential_duplicates",
    indices = [
        Index(value = ["memeId1"]),
        Index(value = ["memeId2"]),
        Index(value = ["status"]),
        Index(value = ["memeId1", "memeId2"], unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId1"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId2"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PotentialDuplicateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** First meme in the duplicate pair (always the lower ID). */
    val memeId1: Long,
    /** Second meme in the duplicate pair (always the higher ID). */
    val memeId2: Long,
    /** Hamming distance between perceptual hashes (0 = identical, lower = more similar). */
    val hammingDistance: Int,
    /** Detection method: "exact" for SHA-256 match, "perceptual" for dHash. */
    val detectionMethod: String,
    /** Status: "pending", "dismissed", "merged". */
    val status: String = "pending",
    /** Timestamp when this duplicate pair was detected (epoch millis). */
    val detectedAt: Long,
)
