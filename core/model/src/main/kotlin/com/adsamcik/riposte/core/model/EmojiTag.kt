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
         * Unicode variation selectors (U+FE00–U+FE0F) that must be stripped so that
         * the stored emoji bytes match the search-query bytes produced by
         * [com.adsamcik.riposte.core.database.util.FtsQuerySanitizer].
         */
        private val VARIATION_SELECTORS_REGEX = Regex("[\uFE00-\uFE0F]")

        /**
         * Strips variation selectors from an emoji string so that FTS4 tokens
         * are identical to the sanitized search terms.
         *
         * Example: `🏋️‍♂️` (U+1F3CB U+FE0F U+200D U+2642 U+FE0F)
         *        → `🏋‍♂`  (U+1F3CB U+200D U+2642)
         */
        fun normalizeEmoji(emoji: String): String =
            emoji.replace(VARIATION_SELECTORS_REGEX, "")

        /**
         * Creates an EmojiTag from just an emoji character.
         * Variation selectors are stripped for FTS consistency.
         */
        fun fromEmoji(emoji: String): EmojiTag {
            val normalized = normalizeEmoji(emoji)
            return EmojiTag(
                emoji = normalized,
                name = normalized,
            )
        }
    }
}
