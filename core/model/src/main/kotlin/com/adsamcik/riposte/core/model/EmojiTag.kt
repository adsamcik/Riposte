package com.adsamcik.riposte.core.model

import kotlinx.serialization.Serializable

/**
 * Represents an emoji tag associated with a meme.
 */
@Serializable
data class EmojiTag(
    /**
     * The actual emoji character (e.g., "😂").
     */
    val emoji: String,
    /**
     * The standardized name of the emoji (e.g., "face_with_tears_of_joy").
     * Used for search indexing and display.
     */
    val name: String,
    /**
     * Optional category for grouping (e.g., "Smileys & Emotion").
     */
    val category: String? = null,
    /**
     * Optional keywords associated with this emoji for enhanced search.
     */
    val keywords: List<String> = emptyList(),
) {
    companion object {
        /**
         * Creates an EmojiTag from just an emoji character.
         */
        fun fromEmoji(emoji: String): EmojiTag {
            return EmojiTag(
                emoji = emoji,
                name = emoji,
            )
        }
    }
}
