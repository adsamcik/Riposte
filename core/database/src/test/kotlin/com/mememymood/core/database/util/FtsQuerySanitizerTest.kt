package com.mememymood.core.database.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FtsQuerySanitizerTest {

    // region sanitize() tests

    @Test
    fun `sanitize returns empty string for blank input`() {
        assertThat(FtsQuerySanitizer.sanitize("")).isEmpty()
        assertThat(FtsQuerySanitizer.sanitize("   ")).isEmpty()
        assertThat(FtsQuerySanitizer.sanitize("\t\n")).isEmpty()
    }

    @Test
    fun `sanitize removes FTS special characters`() {
        assertThat(FtsQuerySanitizer.sanitize("hello\"world")).isEqualTo("hello world")
        assertThat(FtsQuerySanitizer.sanitize("test*")).isEqualTo("test")
        assertThat(FtsQuerySanitizer.sanitize("(foo)")).isEqualTo("foo")
        assertThat(FtsQuerySanitizer.sanitize("bar:baz")).isEqualTo("bar baz")
        assertThat(FtsQuerySanitizer.sanitize("`code`")).isEqualTo("code")
    }

    @Test
    fun `sanitize removes all special characters in combination`() {
        assertThat(FtsQuerySanitizer.sanitize("\"test*(foo):bar`")).isEqualTo("test foo bar")
    }

    @Test
    fun `sanitize removes FTS operators case insensitively`() {
        assertThat(FtsQuerySanitizer.sanitize("cat AND dog")).isEqualTo("cat dog")
        assertThat(FtsQuerySanitizer.sanitize("cat OR dog")).isEqualTo("cat dog")
        assertThat(FtsQuerySanitizer.sanitize("NOT cat")).isEqualTo("cat")
        assertThat(FtsQuerySanitizer.sanitize("cat NEAR dog")).isEqualTo("cat dog")

        // Case insensitive
        assertThat(FtsQuerySanitizer.sanitize("cat and dog")).isEqualTo("cat dog")
        assertThat(FtsQuerySanitizer.sanitize("cat or dog")).isEqualTo("cat dog")
        assertThat(FtsQuerySanitizer.sanitize("not cat")).isEqualTo("cat")
    }

    @Test
    fun `sanitize preserves words containing operator substrings`() {
        // "android" contains "and", "fortune" contains "or", "knot" contains "not"
        assertThat(FtsQuerySanitizer.sanitize("android")).isEqualTo("android")
        assertThat(FtsQuerySanitizer.sanitize("fortune")).isEqualTo("fortune")
        assertThat(FtsQuerySanitizer.sanitize("knot")).isEqualTo("knot")
        assertThat(FtsQuerySanitizer.sanitize("nearby")).isEqualTo("nearby")
    }

    @Test
    fun `sanitize normalizes whitespace`() {
        assertThat(FtsQuerySanitizer.sanitize("hello   world")).isEqualTo("hello world")
        assertThat(FtsQuerySanitizer.sanitize("foo\tbar\nbaz")).isEqualTo("foo bar baz")
        assertThat(FtsQuerySanitizer.sanitize("  trim  me  ")).isEqualTo("trim me")
    }

    @Test
    fun `sanitize filters short ASCII terms by default`() {
        // Single character ASCII terms should be filtered
        assertThat(FtsQuerySanitizer.sanitize("a")).isEmpty()
        assertThat(FtsQuerySanitizer.sanitize("I am here")).isEqualTo("am here")

        // Two character ASCII terms pass
        assertThat(FtsQuerySanitizer.sanitize("go")).isEqualTo("go")
    }

    @Test
    fun `sanitize respects custom minTermLength`() {
        assertThat(FtsQuerySanitizer.sanitize("ab cd", minTermLength = 3)).isEmpty()
        assertThat(FtsQuerySanitizer.sanitize("abc", minTermLength = 3)).isEqualTo("abc")
        assertThat(FtsQuerySanitizer.sanitize("a ab abc", minTermLength = 1)).isEqualTo("a ab abc")
    }

    @Test
    fun `sanitize limits number of terms`() {
        val manyTerms = (1..20).joinToString(" ") { "term$it" }
        val result = FtsQuerySanitizer.sanitize(manyTerms)

        // Default max is 10
        assertThat(result.split(" ")).hasSize(10)
        assertThat(result).contains("term1")
        assertThat(result).contains("term10")
        assertThat(result).doesNotContain("term11")
    }

    @Test
    fun `sanitize respects custom maxTerms`() {
        val manyTerms = (1..10).joinToString(" ") { "term$it" }
        val result = FtsQuerySanitizer.sanitize(manyTerms, maxTerms = 3)

        assertThat(result.split(" ")).hasSize(3)
    }

    @Test
    fun `sanitize preserves emoji characters`() {
        assertThat(FtsQuerySanitizer.sanitize("😂")).isEqualTo("😂")
        assertThat(FtsQuerySanitizer.sanitize("🔥 fire")).isEqualTo("🔥 fire")
        assertThat(FtsQuerySanitizer.sanitize("cat 🐱 dog 🐕")).isEqualTo("cat 🐱 dog 🐕")
    }

    @Test
    fun `sanitize preserves non-ASCII characters like CJK`() {
        assertThat(FtsQuerySanitizer.sanitize("日本語")).isEqualTo("日本語")
        assertThat(FtsQuerySanitizer.sanitize("한국어")).isEqualTo("한국어")
    }

    @Test
    fun `sanitize removes RTL marks`() {
        // U+200E (LRM), U+200F (RLM)
        val withRtl = "hello\u200Eworld\u200F"
        assertThat(FtsQuerySanitizer.sanitize(withRtl)).isEqualTo("helloworld")
    }

    @Test
    fun `sanitize removes emoji variation selectors`() {
        // U+FE0F is variation selector
        val withVariation = "star\uFE0F"
        assertThat(FtsQuerySanitizer.sanitize(withVariation)).isEqualTo("star")
    }

    @Test
    fun `sanitize returns empty for only special characters`() {
        assertThat(FtsQuerySanitizer.sanitize("\"*():`")).isEmpty()
        assertThat(FtsQuerySanitizer.sanitize("OR AND NOT")).isEmpty()
    }

    // endregion

    // region prepareForMatch() tests

    @Test
    fun `prepareForMatch returns empty string for blank input`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("")).isEmpty()
        assertThat(FtsQuerySanitizer.prepareForMatch("   ")).isEmpty()
    }

    @Test
    fun `prepareForMatch formats single term correctly`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("hello")).isEqualTo("\"hello\"*")
    }

    @Test
    fun `prepareForMatch formats multiple terms with OR`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("hello world"))
            .isEqualTo("\"hello\"* OR \"world\"*")
    }

    @Test
    fun `prepareForMatch sanitizes before formatting`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("hello\"world"))
            .isEqualTo("\"hello\"* OR \"world\"*")
        assertThat(FtsQuerySanitizer.prepareForMatch("cat AND dog"))
            .isEqualTo("\"cat\"* OR \"dog\"*")
    }

    @Test
    fun `prepareForMatch returns empty for only special characters`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("\"*():`")).isEmpty()
    }

    @Test
    fun `prepareForMatch handles emoji`() {
        assertThat(FtsQuerySanitizer.prepareForMatch("😂")).isEqualTo("\"😂\"*")
    }

    // endregion

    // region prepareForColumns() tests

    @Test
    fun `prepareForColumns returns empty for blank input`() {
        assertThat(FtsQuerySanitizer.prepareForColumns("", listOf("title"))).isEmpty()
    }

    @Test
    fun `prepareForColumns returns empty for empty columns`() {
        assertThat(FtsQuerySanitizer.prepareForColumns("test", emptyList())).isEmpty()
    }

    @Test
    fun `prepareForColumns formats single column correctly`() {
        assertThat(FtsQuerySanitizer.prepareForColumns("hello", listOf("title")))
            .isEqualTo("title:\"hello\"*")
    }

    @Test
    fun `prepareForColumns formats multiple columns with OR`() {
        assertThat(FtsQuerySanitizer.prepareForColumns("hello", listOf("title", "description")))
            .isEqualTo("title:\"hello\"* OR description:\"hello\"*")
    }

    @Test
    fun `prepareForColumns formats multiple terms and columns`() {
        val result = FtsQuerySanitizer.prepareForColumns(
            "hello world",
            listOf("title", "description"),
        )
        assertThat(result).contains("title:\"hello\"*")
        assertThat(result).contains("description:\"hello\"*")
        assertThat(result).contains("title:\"world\"*")
        assertThat(result).contains("description:\"world\"*")
    }

    // endregion

    // region prepareEmojiQuery() tests

    @Test
    fun `prepareEmojiQuery returns empty for blank input`() {
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("")).isEmpty()
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("   ")).isEmpty()
    }

    @Test
    fun `prepareEmojiQuery formats emoji with default column`() {
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("😂"))
            .isEqualTo("emojiTagsJson:\"😂\"")
    }

    @Test
    fun `prepareEmojiQuery uses custom column`() {
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("🔥", column = "emojis"))
            .isEqualTo("emojis:\"🔥\"")
    }

    @Test
    fun `prepareEmojiQuery removes special characters`() {
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("😂*"))
            .isEqualTo("emojiTagsJson:\"😂\"")
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("\"😂\""))
            .isEqualTo("emojiTagsJson:\"😂\"")
    }

    @Test
    fun `prepareEmojiQuery returns empty for only special characters`() {
        assertThat(FtsQuerySanitizer.prepareEmojiQuery("\"*():`")).isEmpty()
    }

    // endregion

    // region isValidSearchTerm() tests

    @Test
    fun `isValidSearchTerm rejects blank strings`() {
        assertThat(FtsQuerySanitizer.isValidSearchTerm("")).isFalse()
        assertThat(FtsQuerySanitizer.isValidSearchTerm("   ")).isFalse()
    }

    @Test
    fun `isValidSearchTerm rejects short ASCII terms`() {
        assertThat(FtsQuerySanitizer.isValidSearchTerm("a")).isFalse()
        assertThat(FtsQuerySanitizer.isValidSearchTerm("I")).isFalse()
    }

    @Test
    fun `isValidSearchTerm accepts ASCII terms meeting minimum length`() {
        assertThat(FtsQuerySanitizer.isValidSearchTerm("go")).isTrue()
        assertThat(FtsQuerySanitizer.isValidSearchTerm("cat")).isTrue()
    }

    @Test
    fun `isValidSearchTerm always accepts non-ASCII terms`() {
        // Emoji (always valid regardless of length)
        assertThat(FtsQuerySanitizer.isValidSearchTerm("😂")).isTrue()

        // CJK characters
        assertThat(FtsQuerySanitizer.isValidSearchTerm("日")).isTrue()

        // Cyrillic
        assertThat(FtsQuerySanitizer.isValidSearchTerm("я")).isTrue()
    }

    @Test
    fun `isValidSearchTerm respects custom minLength`() {
        assertThat(FtsQuerySanitizer.isValidSearchTerm("ab", minLength = 3)).isFalse()
        assertThat(FtsQuerySanitizer.isValidSearchTerm("abc", minLength = 3)).isTrue()
    }

    // endregion

    // region containsDangerousPatterns() tests

    @Test
    fun `containsDangerousPatterns detects special characters`() {
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("hello\"world")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("test*")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("(foo)")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("bar:baz")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("`code`")).isTrue()
    }

    @Test
    fun `containsDangerousPatterns detects FTS operators`() {
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("cat AND dog")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("cat OR dog")).isTrue()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("NOT cat")).isTrue()
    }

    @Test
    fun `containsDangerousPatterns detects RTL marks`() {
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("hello\u200Eworld")).isTrue()
    }

    @Test
    fun `containsDangerousPatterns detects variation selectors`() {
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("star\uFE0F")).isTrue()
    }

    @Test
    fun `containsDangerousPatterns returns false for safe queries`() {
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("hello world")).isFalse()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("funny cat")).isFalse()
        assertThat(FtsQuerySanitizer.containsDangerousPatterns("😂")).isFalse()
    }

    // endregion

    // region Edge cases and attack patterns

    @Test
    fun `sanitize handles SQL injection attempts`() {
        // These shouldn't affect FTS - semicolons and dashes are not FTS special chars
        // The sanitizer only removes FTS-specific dangerous characters
        assertThat(FtsQuerySanitizer.sanitize("'; DROP TABLE memes;--"))
            .isEqualTo("'; DROP TABLE memes;--")
        assertThat(FtsQuerySanitizer.sanitize("1=1; --")).isEqualTo("1=1; --")
    }

    @Test
    fun `sanitize handles FTS injection attempts`() {
        // Attempt to use FTS column filters (colons are removed)
        assertThat(FtsQuerySanitizer.sanitize("title:\"hack\"")).isEqualTo("title hack")

        // Attempt to use wildcards (asterisks are removed)
        assertThat(FtsQuerySanitizer.sanitize("*hack*")).isEqualTo("hack")

        // Attempt to use proximity operators - NEAR is removed, / is not an FTS special char
        assertThat(FtsQuerySanitizer.sanitize("cat NEAR/5 dog")).isEqualTo("cat /5 dog")
    }

    @Test
    fun `sanitize handles extremely long input`() {
        val longInput = "word ".repeat(1000)
        val result = FtsQuerySanitizer.sanitize(longInput)

        // Should be limited to maxTerms
        assertThat(result.split(" ")).hasSize(FtsQuerySanitizer.DEFAULT_MAX_TERMS)
    }

    @Test
    fun `sanitize handles mixed emoji and text`() {
        assertThat(FtsQuerySanitizer.sanitize("I love 😂 memes"))
            .isEqualTo("love 😂 memes")
    }

    @Test
    fun `sanitize handles compound emoji`() {
        // Family emoji with ZWJ
        val familyEmoji = "👨‍👩‍👧‍👦"
        assertThat(FtsQuerySanitizer.sanitize(familyEmoji)).isEqualTo(familyEmoji)
    }

    @Test
    fun `prepareForMatch handles real-world search queries`() {
        // Common user queries
        assertThat(FtsQuerySanitizer.prepareForMatch("funny cat"))
            .isEqualTo("\"funny\"* OR \"cat\"*")

        assertThat(FtsQuerySanitizer.prepareForMatch("😂 meme"))
            .isEqualTo("\"😂\"* OR \"meme\"*")

        // User might accidentally include quotes
        assertThat(FtsQuerySanitizer.prepareForMatch("\"funny meme\""))
            .isEqualTo("\"funny\"* OR \"meme\"*")
    }

    // endregion
}
