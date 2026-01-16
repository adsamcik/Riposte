package com.mememymood.core.model

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
    val keywords: List<String> = emptyList()
) {
    companion object {
        /**
         * Creates an EmojiTag from just an emoji character with auto-generated name.
         */
        fun fromEmoji(emoji: String): EmojiTag {
            return EmojiTag(
                emoji = emoji,
                name = emojiToName(emoji)
            )
        }

        /**
         * Simple emoji to name converter.
         * In production, this would use a comprehensive emoji database.
         */
        private fun emojiToName(emoji: String): String {
            return commonEmojis[emoji] ?: "unknown_emoji"
        }

        private val commonEmojis = mapOf(
            "😀" to "grinning_face",
            "😂" to "face_with_tears_of_joy",
            "🤣" to "rolling_on_the_floor_laughing",
            "😊" to "smiling_face_with_smiling_eyes",
            "😍" to "smiling_face_with_heart_eyes",
            "🥺" to "pleading_face",
            "😭" to "loudly_crying_face",
            "😤" to "face_with_steam_from_nose",
            "😡" to "pouting_face",
            "🤔" to "thinking_face",
            "😏" to "smirking_face",
            "😴" to "sleeping_face",
            "🤯" to "exploding_head",
            "🥳" to "partying_face",
            "😎" to "smiling_face_with_sunglasses",
            "🤡" to "clown_face",
            "👀" to "eyes",
            "💀" to "skull",
            "🔥" to "fire",
            "💯" to "hundred_points",
            "❤️" to "red_heart",
            "💔" to "broken_heart",
            "👍" to "thumbs_up",
            "👎" to "thumbs_down",
            "👏" to "clapping_hands",
            "🙏" to "folded_hands",
            "💪" to "flexed_biceps",
            "🎉" to "party_popper",
            "✨" to "sparkles",
            "🌟" to "glowing_star"
        )
    }
}
