package com.adsamcik.riposte.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiTagTest {

    // Constructor and property tests
    @Test
    fun `emojiTag stores emoji and name correctly`() {
        val tag = EmojiTag(emoji = "😂", name = "face_with_tears_of_joy")

        assertThat(tag.emoji).isEqualTo("😂")
        assertThat(tag.name).isEqualTo("face_with_tears_of_joy")
    }

    @Test
    fun `default category is null`() {
        val tag = EmojiTag(emoji = "😂", name = "face_with_tears_of_joy")

        assertThat(tag.category).isNull()
    }

    @Test
    fun `default keywords is empty list`() {
        val tag = EmojiTag(emoji = "😂", name = "face_with_tears_of_joy")

        assertThat(tag.keywords).isEmpty()
    }

    @Test
    fun `emojiTag stores all properties correctly`() {
        val keywords = listOf("happy", "laugh", "funny", "lol")
        val tag = EmojiTag(
            emoji = "😂",
            name = "face_with_tears_of_joy",
            category = "Smileys & Emotion",
            keywords = keywords
        )

        assertThat(tag.emoji).isEqualTo("😂")
        assertThat(tag.name).isEqualTo("face_with_tears_of_joy")
        assertThat(tag.category).isEqualTo("Smileys & Emotion")
        assertThat(tag.keywords).isEqualTo(keywords)
    }

    // Copy tests
    @Test
    fun `copy creates identical tag when no changes`() {
        val original = EmojiTag(
            emoji = "🔥",
            name = "fire",
            category = "Symbols",
            keywords = listOf("hot", "trending")
        )
        val copied = original.copy()

        assertThat(copied).isEqualTo(original)
    }

    @Test
    fun `copy can change single property`() {
        val original = EmojiTag(emoji = "🔥", name = "fire")
        val copied = original.copy(category = "Symbols")

        assertThat(copied.category).isEqualTo("Symbols")
        assertThat(copied.emoji).isEqualTo(original.emoji)
        assertThat(copied.name).isEqualTo(original.name)
    }

    @Test
    fun `copy can add keywords`() {
        val original = EmojiTag(emoji = "💯", name = "hundred_points")
        val copied = original.copy(keywords = listOf("perfect", "score", "100"))

        assertThat(copied.keywords).containsExactly("perfect", "score", "100")
        assertThat(original.keywords).isEmpty()
    }

    // Equality tests
    @Test
    fun `emojiTags with same properties are equal`() {
        val tag1 = EmojiTag(
            emoji = "😂",
            name = "face_with_tears_of_joy",
            category = "Smileys",
            keywords = listOf("laugh")
        )
        val tag2 = EmojiTag(
            emoji = "😂",
            name = "face_with_tears_of_joy",
            category = "Smileys",
            keywords = listOf("laugh")
        )

        assertThat(tag1).isEqualTo(tag2)
        assertThat(tag1.hashCode()).isEqualTo(tag2.hashCode())
    }

    @Test
    fun `emojiTags with different emojis are not equal`() {
        val tag1 = EmojiTag(emoji = "😂", name = "face_with_tears_of_joy")
        val tag2 = EmojiTag(emoji = "🤣", name = "face_with_tears_of_joy")

        assertThat(tag1).isNotEqualTo(tag2)
    }

    @Test
    fun `emojiTags with different names are not equal`() {
        val tag1 = EmojiTag(emoji = "😂", name = "face_with_tears_of_joy")
        val tag2 = EmojiTag(emoji = "😂", name = "laughing_face")

        assertThat(tag1).isNotEqualTo(tag2)
    }

    @Test
    fun `emojiTags with different categories are not equal`() {
        val tag1 = EmojiTag(emoji = "😂", name = "test", category = "Category A")
        val tag2 = EmojiTag(emoji = "😂", name = "test", category = "Category B")

        assertThat(tag1).isNotEqualTo(tag2)
    }

    @Test
    fun `emojiTags with different keywords are not equal`() {
        val tag1 = EmojiTag(emoji = "😂", name = "test", keywords = listOf("a"))
        val tag2 = EmojiTag(emoji = "😂", name = "test", keywords = listOf("b"))

        assertThat(tag1).isNotEqualTo(tag2)
    }

    // fromEmoji companion function tests
    @Test
    fun `fromEmoji creates tag with known emoji`() {
        val tag = EmojiTag.fromEmoji("😂")

        assertThat(tag.emoji).isEqualTo("😂")
        assertThat(tag.name).isEqualTo("face_with_tears_of_joy")
    }

    @Test
    fun `fromEmoji maps grinning face correctly`() {
        val tag = EmojiTag.fromEmoji("😀")

        assertThat(tag.emoji).isEqualTo("😀")
        assertThat(tag.name).isEqualTo("grinning_face")
    }

    @Test
    fun `fromEmoji maps fire emoji correctly`() {
        val tag = EmojiTag.fromEmoji("🔥")

        assertThat(tag.emoji).isEqualTo("🔥")
        assertThat(tag.name).isEqualTo("fire")
    }

    @Test
    fun `fromEmoji maps skull emoji correctly`() {
        val tag = EmojiTag.fromEmoji("💀")

        assertThat(tag.emoji).isEqualTo("💀")
        assertThat(tag.name).isEqualTo("skull")
    }

    @Test
    fun `fromEmoji maps thumbs up correctly`() {
        val tag = EmojiTag.fromEmoji("👍")

        assertThat(tag.emoji).isEqualTo("👍")
        assertThat(tag.name).isEqualTo("thumbs_up")
    }

    @Test
    fun `fromEmoji maps party popper correctly`() {
        val tag = EmojiTag.fromEmoji("🎉")

        assertThat(tag.emoji).isEqualTo("🎉")
        assertThat(tag.name).isEqualTo("party_popper")
    }

    @Test
    fun `fromEmoji returns unknown for unmapped emoji`() {
        val tag = EmojiTag.fromEmoji("🦄")

        assertThat(tag.emoji).isEqualTo("🦄")
        assertThat(tag.name).isEqualTo("unknown_emoji")
    }

    @Test
    fun `fromEmoji returns unknown for random string`() {
        val tag = EmojiTag.fromEmoji("not_an_emoji")

        assertThat(tag.emoji).isEqualTo("not_an_emoji")
        assertThat(tag.name).isEqualTo("unknown_emoji")
    }

    @Test
    fun `fromEmoji sets null category`() {
        val tag = EmojiTag.fromEmoji("😂")

        assertThat(tag.category).isNull()
    }

    @Test
    fun `fromEmoji sets empty keywords`() {
        val tag = EmojiTag.fromEmoji("😂")

        assertThat(tag.keywords).isEmpty()
    }

    // Test all common emoji mappings
    @Test
    fun `fromEmoji maps all common emojis`() {
        val expectedMappings = mapOf(
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

        expectedMappings.forEach { (emoji, expectedName) ->
            val tag = EmojiTag.fromEmoji(emoji)
            assertThat(tag.name).isEqualTo(expectedName)
        }
    }
}
