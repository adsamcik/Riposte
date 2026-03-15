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
        val tag =
            EmojiTag(
                emoji = "😂",
                name = "face_with_tears_of_joy",
                category = "Smileys & Emotion",
                keywords = keywords,
            )

        assertThat(tag.emoji).isEqualTo("😂")
        assertThat(tag.name).isEqualTo("face_with_tears_of_joy")
        assertThat(tag.category).isEqualTo("Smileys & Emotion")
        assertThat(tag.keywords).isEqualTo(keywords)
    }

    // Copy tests
    @Test
    fun `copy creates identical tag when no changes`() {
        val original =
            EmojiTag(
                emoji = "🔥",
                name = "fire",
                category = "Symbols",
                keywords = listOf("hot", "trending"),
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
        val tag1 =
            EmojiTag(
                emoji = "😂",
                name = "face_with_tears_of_joy",
                category = "Smileys",
                keywords = listOf("laugh"),
            )
        val tag2 =
            EmojiTag(
                emoji = "😂",
                name = "face_with_tears_of_joy",
                category = "Smileys",
                keywords = listOf("laugh"),
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
    fun `fromEmoji creates tag with emoji as name`() {
        val tag = EmojiTag.fromEmoji("😂")

        assertThat(tag.emoji).isEqualTo("😂")
        assertThat(tag.name).isEqualTo("😂")
    }

    @Test
    fun `fromEmoji works with any emoji`() {
        val tag = EmojiTag.fromEmoji("😀")

        assertThat(tag.emoji).isEqualTo("😀")
        assertThat(tag.name).isEqualTo("😀")
    }

    @Test
    fun `fromEmoji works with fire emoji`() {
        val tag = EmojiTag.fromEmoji("🔥")

        assertThat(tag.emoji).isEqualTo("🔥")
        assertThat(tag.name).isEqualTo("🔥")
    }

    @Test
    fun `fromEmoji works with skull emoji`() {
        val tag = EmojiTag.fromEmoji("💀")

        assertThat(tag.emoji).isEqualTo("💀")
        assertThat(tag.name).isEqualTo("💀")
    }

    @Test
    fun `fromEmoji works with thumbs up`() {
        val tag = EmojiTag.fromEmoji("👍")

        assertThat(tag.emoji).isEqualTo("👍")
        assertThat(tag.name).isEqualTo("👍")
    }

    @Test
    fun `fromEmoji works with party popper`() {
        val tag = EmojiTag.fromEmoji("🎉")

        assertThat(tag.emoji).isEqualTo("🎉")
        assertThat(tag.name).isEqualTo("🎉")
    }

    @Test
    fun `fromEmoji works with unmapped emoji`() {
        val tag = EmojiTag.fromEmoji("🦄")

        assertThat(tag.emoji).isEqualTo("🦄")
        assertThat(tag.name).isEqualTo("🦄")
    }

    @Test
    fun `fromEmoji works with arbitrary string`() {
        val tag = EmojiTag.fromEmoji("not_an_emoji")

        assertThat(tag.emoji).isEqualTo("not_an_emoji")
        assertThat(tag.name).isEqualTo("not_an_emoji")
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

    @Test
    fun `fromEmoji sets name to emoji for any input`() {
        val testEmojis =
            listOf(
                "😀", "😂", "🤣", "😊", "😍", "🥺", "😭", "😤", "😡", "🤔",
                "😏", "😴", "🤯", "🥳", "😎", "🤡", "👀", "💀", "🔥", "💯",
                "💔", "👍", "👎", "👏", "🙏", "💪", "🎉", "✨", "🌟",
            )

        testEmojis.forEach { emoji ->
            val tag = EmojiTag.fromEmoji(emoji)
            assertThat(tag.name).isEqualTo(emoji)
        }
    }

    // normalizeEmoji tests

    @Test
    fun `normalizeEmoji strips variation selector from heart emoji`() {
        // ❤️ = U+2764 + U+FE0F
        assertThat(EmojiTag.normalizeEmoji("❤️")).isEqualTo("❤")
    }

    @Test
    fun `normalizeEmoji strips variation selectors from ZWJ sequence`() {
        // 🏋️‍♂️ = U+1F3CB U+FE0F U+200D U+2642 U+FE0F
        val raw = "\uD83C\uDFCB\uFE0F\u200D\u2642\uFE0F"
        val expected = "\uD83C\uDFCB\u200D\u2642"
        assertThat(EmojiTag.normalizeEmoji(raw)).isEqualTo(expected)
    }

    @Test
    fun `normalizeEmoji preserves simple emoji without variation selector`() {
        assertThat(EmojiTag.normalizeEmoji("😂")).isEqualTo("😂")
        assertThat(EmojiTag.normalizeEmoji("🔥")).isEqualTo("🔥")
        assertThat(EmojiTag.normalizeEmoji("💪")).isEqualTo("💪")
    }

    @Test
    fun `normalizeEmoji is idempotent`() {
        val raw = "❤️"
        val once = EmojiTag.normalizeEmoji(raw)
        val twice = EmojiTag.normalizeEmoji(once)
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `normalizeEmoji preserves ZWJ but strips VS in complex sequences`() {
        // 🧑‍💻 = U+1F9D1 U+200D U+1F4BB (no VS — should be unchanged)
        val devEmoji = "\uD83E\uDDD1\u200D\uD83D\uDCBB"
        assertThat(EmojiTag.normalizeEmoji(devEmoji)).isEqualTo(devEmoji)
    }

    @Test
    fun `fromEmoji normalizes variation selectors`() {
        val tag = EmojiTag.fromEmoji("❤️")
        assertThat(tag.emoji).isEqualTo("❤")
        assertThat(tag.name).isEqualTo("❤")
    }

    @Test
    fun `fromEmoji normalizes ZWJ sequence with variation selectors`() {
        // 🏃‍♂️ = U+1F3C3 U+200D U+2642 U+FE0F
        val running = "\uD83C\uDFC3\u200D\u2642\uFE0F"
        val tag = EmojiTag.fromEmoji(running)
        val expected = "\uD83C\uDFC3\u200D\u2642"
        assertThat(tag.emoji).isEqualTo(expected)
    }
}
