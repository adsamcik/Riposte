package com.mememymood.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Localized content for a specific language.
 * Used to store translations of meme metadata in different languages.
 */
@Serializable
data class LocalizedContent(
    /**
     * Localized title for the meme.
     */
    @SerialName("title")
    val title: String? = null,
    
    /**
     * Localized description of the meme.
     */
    @SerialName("description")
    val description: String? = null,
    
    /**
     * Localized text content (if applicable).
     */
    @SerialName("textContent")
    val textContent: String? = null,
    
    /**
     * Localized tags/keywords for searching.
     */
    @SerialName("tags")
    val tags: List<String> = emptyList(),
)

/**
 * XMP Metadata format for embedding meme information in images.
 * This follows the XMP standard and uses a custom namespace for meme-specific data.
 * 
 * Namespace: http://meme-my-mood.app/1.1/
 * Prefix: mmm
 * 
 * Standard Dublin Core fields are reused for interoperability:
 * - dc:title -> title
 * - dc:description -> description
 * - dc:subject -> emoji names as keywords
 * 
 * Supports multilingual content via [localizations] field (added in schema v1.1).
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
     * Optional title for the meme (in primary language).
     * Maps to Dublin Core dc:title.
     */
    @SerialName("title")
    val title: String? = null,
    
    /**
     * Optional description of the meme (in primary language).
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
     * Optional tags/keywords for enhanced search (in primary language).
     * These are in addition to auto-generated emoji name tags.
     */
    @SerialName("tags")
    val tags: List<String> = emptyList(),

    /**
     * Optional pre-extracted text content from the image (in primary language).
     * If provided, the app may skip OCR processing.
     */
    @SerialName("textContent")
    val textContent: String? = null,
    
    /**
     * BCP 47 language code of the primary content (title, description, tags).
     * Examples: "en", "cs", "de", "zh-TW"
     * Added in schema v1.1.
     */
    @SerialName("primaryLanguage")
    val primaryLanguage: String? = null,
    
    /**
     * Localized content keyed by BCP 47 language code.
     * Each entry contains translations of title, description, textContent, and tags.
     * Added in schema v1.1.
     */
    @SerialName("localizations")
    val localizations: Map<String, LocalizedContent> = emptyMap(),
) {
    init {
        require(emojis.isNotEmpty()) { "At least one emoji is required" }
    }
    
    /**
     * Converts to a list of EmojiTag domain objects.
     */
    fun toEmojiTags(): List<EmojiTag> = emojis.map { EmojiTag.fromEmoji(it) }
    
    /**
     * Gets the localized content for a specific language.
     * 
     * @param languageCode BCP 47 language code (e.g., "en", "cs", "de")
     * @return LocalizedContent for the language, or null if not available
     */
    fun getLocalization(languageCode: String): LocalizedContent? =
        localizations[languageCode]
    
    /**
     * Gets the title in the specified language, falling back to primary title.
     * 
     * @param languageCode BCP 47 language code
     * @return Localized title, or primary title if not available
     */
    fun getTitleForLanguage(languageCode: String): String? =
        localizations[languageCode]?.title ?: title
    
    /**
     * Gets the description in the specified language, falling back to primary.
     * 
     * @param languageCode BCP 47 language code
     * @return Localized description, or primary description if not available
     */
    fun getDescriptionForLanguage(languageCode: String): String? =
        localizations[languageCode]?.description ?: description
    
    /**
     * Gets the tags in the specified language, falling back to primary tags.
     * 
     * @param languageCode BCP 47 language code
     * @return Localized tags, or primary tags if not available
     */
    fun getTagsForLanguage(languageCode: String): List<String> =
        localizations[languageCode]?.tags?.takeIf { it.isNotEmpty() } ?: tags
    
    /**
     * Returns all available language codes (primary + localizations).
     */
    fun availableLanguages(): Set<String> = buildSet {
        primaryLanguage?.let { add(it) }
        addAll(localizations.keys)
    }
    
    companion object {
        const val CURRENT_SCHEMA_VERSION = "1.1"
        
        // Legacy schema version for backward compatibility
        const val LEGACY_SCHEMA_VERSION = "1.0"
        
        // XMP Namespace constants
        const val XMP_NAMESPACE = "http://meme-my-mood.app/1.1/"
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
