package com.adsamcik.riposte.core.common.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EmojiUtilsTest {
    @Test
    fun `normalizeEmoji strips emoji presentation selector`() {
        // ❤️ = U+2764 U+FE0F → ❤ = U+2764
        val withSelector = "\u2764\uFE0F"
        val without = "\u2764"

        assertThat(normalizeEmoji(withSelector)).isEqualTo(without)
    }

    @Test
    fun `normalizeEmoji strips text presentation selector`() {
        val withSelector = "\u2764\uFE0E"
        val without = "\u2764"

        assertThat(normalizeEmoji(withSelector)).isEqualTo(without)
    }

    @Test
    fun `normalizeEmoji matches heart with and without variation selector`() {
        val heartWithSelector = "\u2764\uFE0F"
        val heartWithout = "\u2764"

        assertThat(normalizeEmoji(heartWithSelector)).isEqualTo(normalizeEmoji(heartWithout))
    }

    @Test
    fun `normalizeEmoji strips trailing zero-width joiner`() {
        val withZwj = "\uD83D\uDE00\u200D"
        val without = "\uD83D\uDE00"

        assertThat(normalizeEmoji(withZwj)).isEqualTo(without)
    }

    @Test
    fun `normalizeEmoji preserves emoji without selectors`() {
        val emoji = "\uD83D\uDE02" // 😂
        assertThat(normalizeEmoji(emoji)).isEqualTo(emoji)
    }

    @Test
    fun `normalizeEmoji preserves empty string`() {
        assertThat(normalizeEmoji("")).isEmpty()
    }

    @Test
    fun `normalizeEmoji preserves regular text`() {
        assertThat(normalizeEmoji("hello")).isEqualTo("hello")
    }
}
