package com.mememymood.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * XMP Metadata format for embedding meme information in images.
 * This follows the XMP standard and uses a custom namespace for meme-specific data.
 * 
 * Namespace: http://meme-my-mood.app/1.0/
 * Prefix: mmm
 * 
 * Standard Dublin Core fields are reused for interoperability:
 * - dc:title -> title
 * - dc:description -> description
 * - dc:subject -> emoji names as keywords
 */
@Serializable
data class MemeMetadata(
    /**
     * Schema version for forward compatibility.
     */
    @SerialName("schemaVersion")
    val schemaVersion: String = CURRENT_SCHEMA_VERSION,
    
    /**
     * List of emoji characters associated with this meme.
     * Stored as actual Unicode emoji characters for maximum compatibility.
     */
    @SerialName("emojis")
    val emojis: List<String>,
    
    /**
     * Optional title for the meme.
     * Maps to Dublin Core dc:title.
     */
    @SerialName("title")
    val title: String? = null,
    
    /**
     * Optional description of the meme.
     * Maps to Dublin Core dc:description.
     */
    @SerialName("description")
    val description: String? = null,
    
    /**
     * Timestamp when the metadata was created/modified (ISO 8601).
     */
    @SerialName("createdAt")
    val createdAt: String? = null,
    
    /**
     * App version that created this metadata.
     */
    @SerialName("appVersion")
    val appVersion: String? = null,
    
    /**
     * Optional source URL if the meme was imported from the web.
     */
    @SerialName("source")
    val source: String? = null,
    
    /**
     * Optional tags/keywords for enhanced search.
     * These are in addition to auto-generated emoji name tags.
     */
    @SerialName("tags")
    val tags: List<String> = emptyList(),

    /**
     * Optional pre-extracted text content from the image.
     * If provided, the app may skip OCR processing.
     */
    @SerialName("textContent")
    val textContent: String? = null,
) {
    init {
        require(emojis.isNotEmpty()) { "At least one emoji is required" }
    }
    
    /**
     * Converts to a list of EmojiTag domain objects.
     */
    fun toEmojiTags(): List<EmojiTag> = emojis.map { EmojiTag.fromEmoji(it) }
    
    companion object {
        const val CURRENT_SCHEMA_VERSION = "1.0"
        
        // XMP Namespace constants
        const val XMP_NAMESPACE = "http://meme-my-mood.app/1.0/"
        const val XMP_PREFIX = "mmm"
        
        // Dublin Core namespace for standard fields
        const val DC_NAMESPACE = "http://purl.org/dc/elements/1.1/"
        const val DC_PREFIX = "dc"
        
        /**
         * Creates metadata from a Meme domain object.
         */
        fun fromMeme(meme: Meme): MemeMetadata = MemeMetadata(
            emojis = meme.emojiTags.map { it.emoji },
            title = meme.title,
            description = meme.description,
            createdAt = java.time.Instant.ofEpochMilli(meme.importedAt).toString()
        )
        
        /**
         * Creates minimal metadata with just emojis.
         */
        fun withEmojis(vararg emojis: String): MemeMetadata = MemeMetadata(
            emojis = emojis.toList()
        )
    }
}
