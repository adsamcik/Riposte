package com.adsamcik.riposte.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity for storing meme information.
 */
@Entity(
    tableName = "memes",
    indices = [
        Index(value = ["importedAt"]),
        Index(value = ["isFavorite"]),
        Index(value = ["filePath"], unique = true),
        Index(value = ["viewCount"]),
        Index(value = ["lastViewedAt"]),
        Index(value = ["fileHash"]),
        Index(value = ["perceptualHash"]),
    ],
)
data class MemeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /**
     * Absolute path to the image file in app storage.
     */
    val filePath: String,
    /**
     * Original file name.
     */
    val fileName: String,
    /**
     * MIME type (e.g., "image/png", "image/jpeg").
     */
    val mimeType: String,
    /**
     * Image width in pixels.
     */
    val width: Int,
    /**
     * Image height in pixels.
     */
    val height: Int,
    /**
     * File size in bytes.
     */
    val fileSizeBytes: Long,
    /**
     * Timestamp when the meme was imported (epoch millis).
     */
    val importedAt: Long,
    /**
     * JSON array of emoji characters associated with this meme.
     * Example: ["😂", "🔥", "💯"]
     */
    val emojiTagsJson: String,
    /**
     * Optional title for the meme.
     */
    val title: String? = null,
    /**
     * Optional description.
     */
    val description: String? = null,
    /**
     * OCR-extracted text content from the image.
     */
    val textContent: String? = null,
    /**
     * JSON array of natural language search phrases.
     * Example: ["that feeling when code works", "confused programmer"]
     */
    val searchPhrasesJson: String? = null,
    /**
     * Serialized embedding vector for semantic search (FloatArray as ByteArray).
     */
    val embedding: ByteArray? = null,
    /**
     * Whether this meme is marked as favorite.
     */
    val isFavorite: Boolean = false,
    /**
     * Timestamp when the original image was created (epoch millis).
     * Falls back to importedAt if not available.
     */
    val createdAt: Long = importedAt,
    /**
     * Number of times this meme has been shared or used.
     */
    val useCount: Int = 0,
    /**
     * BCP 47 language code of the primary content (title, description, tags).
     * Examples: "en", "cs", "de", "zh-TW"
     */
    val primaryLanguage: String? = null,
    /**
     * JSON object containing localized content for additional languages.
     * Keys are BCP 47 language codes, values are objects with title, description,
     * textContent, and tags fields.
     * Example: {"cs": {"title": "...", "description": "...", "tags": [...]}}
     */
    val localizationsJson: String? = null,
    /**
     * Number of times this meme has been viewed.
     */
    val viewCount: Int = 0,
    /**
     * Timestamp when this meme was last viewed (epoch millis).
     */
    val lastViewedAt: Long? = null,
    /**
     * SHA-256 hash of the imported image file for duplicate detection.
     */
    val fileHash: String? = null,
    /**
     * Cultural source the meme is based on (e.g., meme template, franchise, game).
     * Examples: "Drake Hotline Bling", "The Witcher 3", "Star Wars"
     */
    val basedOn: String? = null,
    /**
     * Perceptual hash (dHash) for near-duplicate detection.
     * 64-bit hash based on visual structure — similar images produce similar hashes.
     */
    val perceptualHash: Long? = null,
) {
    @Suppress("CyclomaticComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MemeEntity

        if (id != other.id) return false
        if (filePath != other.filePath) return false
        if (fileName != other.fileName) return false
        if (mimeType != other.mimeType) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (fileSizeBytes != other.fileSizeBytes) return false
        if (importedAt != other.importedAt) return false
        if (emojiTagsJson != other.emojiTagsJson) return false
        if (title != other.title) return false
        if (description != other.description) return false
        if (textContent != other.textContent) return false
        if (searchPhrasesJson != other.searchPhrasesJson) return false
        if (embedding != null) {
            if (other.embedding == null) return false
            if (!embedding.contentEquals(other.embedding)) return false
        } else if (other.embedding != null) {
            return false
        }
        if (isFavorite != other.isFavorite) return false
        if (createdAt != other.createdAt) return false
        if (useCount != other.useCount) return false
        if (primaryLanguage != other.primaryLanguage) return false
        if (localizationsJson != other.localizationsJson) return false
        if (viewCount != other.viewCount) return false
        if (lastViewedAt != other.lastViewedAt) return false
        if (fileHash != other.fileHash) return false
        if (basedOn != other.basedOn) return false
        if (perceptualHash != other.perceptualHash) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + filePath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + fileSizeBytes.hashCode()
        result = 31 * result + importedAt.hashCode()
        result = 31 * result + emojiTagsJson.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (textContent?.hashCode() ?: 0)
        result = 31 * result + (searchPhrasesJson?.hashCode() ?: 0)
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + useCount
        result = 31 * result + (primaryLanguage?.hashCode() ?: 0)
        result = 31 * result + (localizationsJson?.hashCode() ?: 0)
        result = 31 * result + viewCount
        result = 31 * result + (lastViewedAt?.hashCode() ?: 0)
        result = 31 * result + (fileHash?.hashCode() ?: 0)
        result = 31 * result + (basedOn?.hashCode() ?: 0)
        result = 31 * result + (perceptualHash?.hashCode() ?: 0)
        return result
    }
}
