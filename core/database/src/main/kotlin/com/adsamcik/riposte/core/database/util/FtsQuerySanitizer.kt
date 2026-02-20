package com.adsamcik.riposte.core.database.util

/**
 * Sanitizes user input for safe use in FTS4 MATCH clauses.
 *
 * FTS4 queries are vulnerable to crashes and unexpected behavior when user input contains:
 * - Special characters: `"*():\``
 * - Boolean operators: OR, AND, NOT, NEAR
 * - Unicode control characters (RTL marks, variation selectors)
 *
 * This utility removes these dangerous patterns and prepares safe query strings.
 */
object FtsQuerySanitizer {
    /**
     * Default minimum grapheme length for search terms.
     * ASCII-only terms shorter than this are filtered out.
     */
    const val DEFAULT_MIN_TERM_LENGTH = 2

    /**
     * Default maximum number of terms to prevent DoS attacks.
     */
    const val DEFAULT_MAX_TERMS = 10

    private const val MAX_ASCII_CODE = 127

    /** FTS4 special characters that need to be removed. */
    private val FTS_SPECIAL_CHARS_REGEX = Regex("[\"*():`]")

    /** FTS4 operators that need to be removed. */
    private val FTS_OPERATORS_REGEX = Regex("\\b(OR|AND|NOT|NEAR)\\b", RegexOption.IGNORE_CASE)

    /** Whitespace for splitting terms. */
    private val WHITESPACE_REGEX = Regex("\\s+")

    /** RTL/LTR marks and other directional formatting characters. */
    private val RTL_MARKS_REGEX = Regex("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")

    /** Emoji variation selectors that can cause FTS issues. */
    private val VARIATION_SELECTORS_REGEX = Regex("[\\uFE00-\\uFE0F]")

    /**
     * Sanitizes a query string for FTS4 MATCH clause.
     *
     * This method:
     * 1. Removes Unicode control characters (RTL marks, variation selectors)
     * 2. Removes FTS special characters: `"*():\``
     * 3. Removes FTS operators: OR, AND, NOT, NEAR
     * 4. Trims and normalizes whitespace
     * 5. Filters terms by minimum length (ASCII-only terms)
     * 6. Limits total number of terms
     *
     * @param query The raw user input query
     * @param minTermLength Minimum length for ASCII-only terms (default: 2)
     * @param maxTerms Maximum number of terms to include (default: 10)
     * @return Sanitized query with individual terms, or empty string if no valid terms
     */
    fun sanitize(
        query: String,
        minTermLength: Int = DEFAULT_MIN_TERM_LENGTH,
        maxTerms: Int = DEFAULT_MAX_TERMS,
    ): String {
        if (query.isBlank()) return ""

        // Step 1: Remove Unicode control characters
        val withoutControlChars =
            query
                .replace(RTL_MARKS_REGEX, "")
                .replace(VARIATION_SELECTORS_REGEX, "")

        // Step 2: Remove FTS special characters and operators
        val sanitized =
            withoutControlChars
                .replace(FTS_SPECIAL_CHARS_REGEX, " ")
                .replace(FTS_OPERATORS_REGEX, " ")

        // Step 3: Split, filter, and limit terms
        val terms =
            sanitized
                .split(WHITESPACE_REGEX)
                .filter { term ->
                    term.isNotBlank() && isValidSearchTerm(term, minTermLength)
                }
                .take(maxTerms)

        return terms.joinToString(" ").trim()
    }

    /**
     * Sanitizes and formats a query for general FTS4 MATCH with OR semantics.
     *
     * Returns a query string like: "term1"* OR "term2"*
     * Each term is quoted for safety and has a prefix wildcard.
     *
     * @param query The raw user input query
     * @param minTermLength Minimum length for ASCII-only terms (default: 2)
     * @param maxTerms Maximum number of terms to include (default: 10)
     * @return Formatted FTS query, or empty string if no valid terms
     */
    fun prepareForMatch(
        query: String,
        minTermLength: Int = DEFAULT_MIN_TERM_LENGTH,
        maxTerms: Int = DEFAULT_MAX_TERMS,
    ): String {
        if (query.isBlank()) return ""

        // Step 1: Remove Unicode control characters
        val withoutControlChars =
            query
                .replace(RTL_MARKS_REGEX, "")
                .replace(VARIATION_SELECTORS_REGEX, "")

        // Step 2: Remove FTS special characters and operators
        val sanitized =
            withoutControlChars
                .replace(FTS_SPECIAL_CHARS_REGEX, " ")
                .replace(FTS_OPERATORS_REGEX, " ")

        // Step 3: Split, filter, and limit terms
        val terms =
            sanitized
                .split(WHITESPACE_REGEX)
                .filter { term ->
                    term.isNotBlank() && isValidSearchTerm(term, minTermLength)
                }
                .take(maxTerms)

        if (terms.isEmpty()) return ""

        // Quote each term for safety and add prefix wildcard
        return terms.joinToString(" OR ") { "\"$it\"*" }
    }

    /**
     * Prepares a query for column-specific FTS4 MATCH (e.g., title and description).
     *
     * Returns a query string like:
     * title:"term1"* OR description:"term1"* OR title:"term2"* OR description:"term2"*
     *
     * @param query The raw user input query
     * @param columns The columns to search in
     * @param minTermLength Minimum length for ASCII-only terms (default: 2)
     * @param maxTerms Maximum number of terms to include (default: 10)
     * @return Formatted FTS query for specific columns, or empty string if no valid terms
     */
    fun prepareForColumns(
        query: String,
        columns: List<String>,
        minTermLength: Int = DEFAULT_MIN_TERM_LENGTH,
        maxTerms: Int = DEFAULT_MAX_TERMS,
    ): String {
        if (query.isBlank() || columns.isEmpty()) return ""

        val sanitized =
            query
                .replace(FTS_SPECIAL_CHARS_REGEX, " ")
                .replace(FTS_OPERATORS_REGEX, " ")

        val terms =
            sanitized
                .split(WHITESPACE_REGEX)
                .filter { term ->
                    term.isNotBlank() && isValidSearchTerm(term, minTermLength)
                }
                .take(maxTerms)

        if (terms.isEmpty()) return ""

        return terms.joinToString(" OR ") { term ->
            columns.joinToString(" OR ") { column ->
                "$column:\"$term\"*"
            }
        }
    }

    /**
     * Prepares an emoji query for FTS4 MATCH.
     *
     * @param emoji The emoji character or string to search for
     * @param column The column containing emoji data (default: "emojiTagsJson")
     * @return Formatted FTS query for emoji search, or empty string if input is invalid
     */
    fun prepareEmojiQuery(
        emoji: String,
        column: String = "emojiTagsJson",
    ): String {
        if (emoji.isBlank()) return ""

        // Remove FTS special characters while preserving emoji
        val sanitized =
            emoji
                .replace(FTS_SPECIAL_CHARS_REGEX, "")
                .replace(VARIATION_SELECTORS_REGEX, "")
                .trim()

        if (sanitized.isBlank()) return ""

        return "$column:\"$sanitized\""
    }

    /**
     * Checks if a term is valid for FTS search.
     *
     * - Terms with non-ASCII characters (like emoji, CJK) are always valid
     * - ASCII-only terms must meet the minimum length requirement
     *
     * @param term The term to validate
     * @param minLength Minimum length for ASCII-only terms
     * @return true if the term is valid for searching
     */
    fun isValidSearchTerm(
        term: String,
        minLength: Int = DEFAULT_MIN_TERM_LENGTH,
    ): Boolean {
        if (term.isBlank()) return false

        // If the term contains any non-ASCII character, it's valid (emoji, CJK, etc.)
        if (term.any { it.code > MAX_ASCII_CODE }) {
            return true
        }

        // For ASCII-only terms, require minimum length
        return term.length >= minLength
    }

    /**
     * Checks if a query contains any potentially dangerous FTS patterns.
     *
     * Useful for logging or metrics around sanitization impact.
     *
     * @param query The query to check
     * @return true if the query contains patterns that would be sanitized
     */
    fun containsDangerousPatterns(query: String): Boolean {
        return FTS_SPECIAL_CHARS_REGEX.containsMatchIn(query) ||
            FTS_OPERATORS_REGEX.containsMatchIn(query) ||
            RTL_MARKS_REGEX.containsMatchIn(query) ||
            VARIATION_SELECTORS_REGEX.containsMatchIn(query)
    }
}
