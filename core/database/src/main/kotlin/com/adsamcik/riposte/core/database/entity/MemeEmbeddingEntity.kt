package com.adsamcik.riposte.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing meme embeddings for semantic search.
 * 
 * Embeddings are stored separately from the main meme table for several reasons:
 * 1. Performance: Large BLOB data can slow down queries on the main table
 * 2. Flexibility: Allows easy re-generation when model changes
 * 3. Optional: Not all memes need embeddings immediately
 * 
 * Each embedding is associated with a specific model version, allowing
 * graceful migration when the embedding model is upgraded.
 */
@Entity(
    tableName = "meme_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = MemeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["memeId"], unique = true),
        Index(value = ["modelVersion"]),
        Index(value = ["generatedAt"]),
        Index(value = ["needsRegeneration"])
    ]
)
data class MemeEmbeddingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * Foreign key to the meme this embedding belongs to.
     */
    val memeId: Long,
    
    /**
     * The embedding vector serialized as ByteArray.
     * 
     * Format: Little-endian float32 array
     * Size: embeddingDimension * 4 bytes
     */
    val embedding: ByteArray,
    
    /**
     * Dimension of the embedding vector.
     * Stored for validation and compatibility checks.
     */
    val dimension: Int,
    
    /**
     * Version identifier for the embedding model used.
     * 
     * Format: "model_name:version" (e.g., "use_v1:1.0.0", "litert_use:2.0.0")
     * Used to detect when embeddings need re-generation due to model updates.
     */
    val modelVersion: String,
    
    /**
     * Timestamp when this embedding was generated (epoch millis).
     */
    val generatedAt: Long,
    
    /**
     * Source text used to generate the embedding.
     * 
     * This is useful for debugging and can be used to verify embedding quality.
     * Stores a hash or truncated version if the full text is too long.
     */
    val sourceTextHash: String? = null,
    
    /**
     * Indicates whether this embedding needs regeneration.
     * 
     * Set to true when:
     * - The source meme's text content changes
     * - The model version is outdated
     * - Manual regeneration is requested
     */
    val needsRegeneration: Boolean = false,

    /**
     * Number of times embedding generation has been attempted.
     * Used to track and limit retries for problematic memes.
     */
    val indexingAttempts: Int = 0,

    /**
     * Timestamp when embedding generation was last attempted (epoch millis).
     */
    val lastAttemptAt: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemeEmbeddingEntity

        if (id != other.id) return false
        if (memeId != other.memeId) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (dimension != other.dimension) return false
        if (modelVersion != other.modelVersion) return false
        if (generatedAt != other.generatedAt) return false
        if (sourceTextHash != other.sourceTextHash) return false
        if (needsRegeneration != other.needsRegeneration) return false
        if (indexingAttempts != other.indexingAttempts) return false
        if (lastAttemptAt != other.lastAttemptAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + memeId.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + dimension
        result = 31 * result + modelVersion.hashCode()
        result = 31 * result + generatedAt.hashCode()
        result = 31 * result + (sourceTextHash?.hashCode() ?: 0)
        result = 31 * result + needsRegeneration.hashCode()
        result = 31 * result + indexingAttempts
        result = 31 * result + (lastAttemptAt?.hashCode() ?: 0)
        return result
    }

    companion object {
        /**
         * Current model version string.
         * Update this when the embedding model changes.
         */
        const val CURRENT_MODEL_VERSION = "embeddinggemma:1.0.0"
        
        /**
         * Default embedding dimension for the current model.
         * EmbeddingGemma produces 768-dimensional embeddings.
         */
        const val DEFAULT_DIMENSION = 768
    }
}

/**
 * Data class for meme with its embedding for search operations.
 * Used when joining memes with their embeddings.
 */
data class MemeWithEmbeddingData(
    val memeId: Long,
    val filePath: String,
    val fileName: String,
    val title: String?,
    val description: String?,
    val textContent: String?,
    val emojiTagsJson: String,
    val embedding: ByteArray?,
    val dimension: Int?,
    val modelVersion: String?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemeWithEmbeddingData

        if (memeId != other.memeId) return false
        if (filePath != other.filePath) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = memeId.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}
