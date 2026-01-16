package com.mememymood.core.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing a meme/image in the app.
 */
@Serializable
data class Meme(
    val id: Long = 0,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val importedAt: Long,
    val emojiTags: List<EmojiTag>,
    val title: String? = null,
    val description: String? = null,
    val textContent: String? = null,
    val isFavorite: Boolean = false
) {
    /**
     * Returns a comma-separated string of emojis for display.
     */
    val emojiDisplay: String
        get() = emojiTags.joinToString(" ") { it.emoji }

    /**
     * Returns true if the meme has any searchable text content.
     */
    val hasSearchableContent: Boolean
        get() = !title.isNullOrBlank() || 
                !description.isNullOrBlank() || 
                !textContent.isNullOrBlank() ||
                emojiTags.isNotEmpty()
}
