package com.adsamcik.riposte.core.model

/**
 * Defines the type of embedding stored for a meme.
 * Each type represents a different semantic "slot" optimized for
 * different aspects of search.
 *
 * Multiple embeddings per meme allow focused, high-quality vectors
 * that stay within the embedding model's optimal input length (~128 tokens).
 */
enum class EmbeddingType(
    /**
     * Stable string identifier stored in the database.
     */
    val key: String,
) {
    /**
     * Content embedding: captures what the meme IS.
     * Built from: title + description (separated by ". ").
     */
    CONTENT("content"),

    /**
     * Intent embedding: captures how someone would SEARCH for this meme.
     * Built from: searchPhrases joined with ". ".
     */
    INTENT("intent"),
    ;

    companion object {
        fun fromKey(key: String): EmbeddingType? = entries.find { it.key == key }
    }
}
