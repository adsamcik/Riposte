package com.adsamcik.riposte.core.testing

import com.adsamcik.riposte.core.model.EmojiTag
import com.adsamcik.riposte.core.model.ImageFormat
import com.adsamcik.riposte.core.model.MatchType
import com.adsamcik.riposte.core.model.Meme
import com.adsamcik.riposte.core.model.MemeMetadata
import com.adsamcik.riposte.core.model.SearchResult
import com.adsamcik.riposte.core.model.ShareConfig
import com.adsamcik.riposte.core.model.SharingPreferences

/**
 * Factory object for creating test data with sensible defaults.
 *
 * Provides builder-style functions for all domain models used in the app.
 * All parameters have reasonable defaults that can be overridden as needed.
 *
 * Usage:
 * ```kotlin
 * val meme = TestDataFactory.createMeme(title = "Funny Cat")
 * val tag = TestDataFactory.createEmojiTag(emoji = "😂")
 * ```
 */
object TestDataFactory {
    private var idCounter = 0L

    /**
     * Resets the ID counter. Useful for test isolation.
     */
    fun resetIds() {
        idCounter = 0L
    }

    /**
     * Generates a unique ID for test entities.
     */
    fun nextId(): Long = ++idCounter

    // ============ Meme Creation ============

    /**
     * Creates a [Meme] with sensible test defaults.
     *
     * @param id Unique identifier. Defaults to auto-generated.
     * @param filePath Path to the meme image file.
     * @param fileName Name of the file.
     * @param mimeType MIME type of the image.
     * @param width Image width in pixels.
     * @param height Image height in pixels.
     * @param fileSizeBytes File size in bytes.
     * @param importedAt Timestamp when the meme was imported.
     * @param emojiTags List of emoji tags.
     * @param title Optional title.
     * @param description Optional description.
     * @param textContent Optional extracted text content.
     * @param isFavorite Whether the meme is favorited.
     */
    fun createMeme(
        id: Long = nextId(),
        filePath: String = "/storage/emulated/0/memes/meme_$id.jpg",
        fileName: String = "meme_$id.jpg",
        mimeType: String = "image/jpeg",
        width: Int = 1080,
        height: Int = 1080,
        fileSizeBytes: Long = 102400L,
        importedAt: Long = System.currentTimeMillis(),
        emojiTags: List<EmojiTag> = listOf(createEmojiTag()),
        title: String? = "Test Meme $id",
        description: String? = "A funny test meme",
        textContent: String? = null,
        isFavorite: Boolean = false,
    ): Meme =
        Meme(
            id = id,
            filePath = filePath,
            fileName = fileName,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            importedAt = importedAt,
            emojiTags = emojiTags,
            title = title,
            description = description,
            textContent = textContent,
            isFavorite = isFavorite,
        )

    /**
     * Creates a minimal meme with only required fields.
     */
    fun createMinimalMeme(
        id: Long = nextId(),
        filePath: String = "/test/meme_$id.jpg",
    ): Meme =
        Meme(
            id = id,
            filePath = filePath,
            fileName = "meme_$id.jpg",
            mimeType = "image/jpeg",
            width = 500,
            height = 500,
            fileSizeBytes = 50000L,
            importedAt = System.currentTimeMillis(),
            emojiTags = emptyList(),
        )

    /**
     * Creates a list of memes for testing grid/list displays.
     *
     * @param count Number of memes to create.
     * @param favoriteIndices Indices of memes that should be favorited.
     */
    fun createMemeList(
        count: Int = 10,
        favoriteIndices: Set<Int> = emptySet(),
    ): List<Meme> =
        (0 until count).map { index ->
            createMeme(
                id = nextId(),
                isFavorite = index in favoriteIndices,
                emojiTags =
                    listOf(
                        createEmojiTag(emoji = SAMPLE_EMOJIS[index % SAMPLE_EMOJIS.size]),
                    ),
            )
        }

    // ============ EmojiTag Creation ============

    /**
     * Creates an [EmojiTag] with sensible defaults.
     *
     * @param emoji The emoji character.
     * @param name The name of the emoji.
     * @param category Optional category.
     * @param keywords Search keywords.
     */
    fun createEmojiTag(
        emoji: String = "😂",
        name: String = "face_with_tears_of_joy",
        category: String? = "Smileys & Emotion",
        keywords: List<String> = listOf("funny", "laughing", "lol"),
    ): EmojiTag =
        EmojiTag(
            emoji = emoji,
            name = name,
            category = category,
            keywords = keywords,
        )

    /**
     * Creates a list of common emoji tags for testing.
     */
    fun createCommonEmojiTags(): List<EmojiTag> =
        listOf(
            createEmojiTag("😂", "face_with_tears_of_joy", "Smileys & Emotion"),
            createEmojiTag("😍", "smiling_face_with_heart_eyes", "Smileys & Emotion"),
            createEmojiTag("🔥", "fire", "Travel & Places"),
            createEmojiTag("💀", "skull", "Smileys & Emotion"),
            createEmojiTag("😭", "loudly_crying_face", "Smileys & Emotion"),
            createEmojiTag("🤣", "rolling_on_the_floor_laughing", "Smileys & Emotion"),
            createEmojiTag("❤️", "red_heart", "Smileys & Emotion"),
            createEmojiTag("✨", "sparkles", "Activities"),
            createEmojiTag("👍", "thumbs_up", "People & Body"),
            createEmojiTag("🎉", "party_popper", "Activities"),
        )

    // ============ SearchResult Creation ============

    /**
     * Creates a [SearchResult] with sensible defaults.
     *
     * @param meme The meme in this result.
     * @param relevanceScore Score from 0.0 to 1.0.
     * @param matchType Type of search match.
     */
    fun createSearchResult(
        meme: Meme = createMeme(),
        relevanceScore: Float = 0.95f,
        matchType: MatchType = MatchType.TEXT,
    ): SearchResult =
        SearchResult(
            meme = meme,
            relevanceScore = relevanceScore,
            matchType = matchType,
        )

    /**
     * Creates a list of search results with decreasing relevance scores.
     *
     * @param count Number of results.
     * @param matchType Type of match for all results.
     */
    fun createSearchResultList(
        count: Int = 5,
        matchType: MatchType = MatchType.HYBRID,
    ): List<SearchResult> =
        (0 until count).map { index ->
            createSearchResult(
                meme = createMeme(),
                relevanceScore = 1.0f - (index * 0.1f),
                matchType = matchType,
            )
        }

    // ============ MemeMetadata Creation ============

    /**
     * Creates [MemeMetadata] with sensible defaults.
     *
     * @param schemaVersion Schema version string.
     * @param emojis List of emoji characters.
     * @param title Optional title.
     * @param description Optional description.
     * @param createdAt ISO 8601 timestamp.
     * @param appVersion App version string.
     */
    fun createMemeMetadata(
        schemaVersion: String = "1.0",
        emojis: List<String> = listOf("😂", "🔥"),
        title: String? = "Test Meme",
        description: String? = "A test meme for unit testing",
        createdAt: String? = "2025-01-15T12:00:00Z",
        appVersion: String? = "1.0.0",
    ): MemeMetadata =
        MemeMetadata(
            schemaVersion = schemaVersion,
            emojis = emojis,
            title = title,
            description = description,
            createdAt = createdAt,
            appVersion = appVersion,
        )

    // ============ ShareConfig Creation ============

    /**
     * Creates a [ShareConfig] with sensible defaults.
     *
     * @param format Target image format.
     * @param quality Compression quality (0-100).
     * @param maxWidth Maximum width in pixels.
     * @param maxHeight Maximum height in pixels.
     * @param stripMetadata Whether to strip metadata.
     */
    fun createShareConfig(
        format: ImageFormat = ImageFormat.JPEG,
        quality: Int = 85,
        maxWidth: Int? = 1080,
        maxHeight: Int? = 1080,
        stripMetadata: Boolean = true,
    ): ShareConfig =
        ShareConfig(
            format = format,
            quality = quality,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            stripMetadata = stripMetadata,
        )

    /**
     * Creates a high-quality share config (no compression, no scaling).
     */
    fun createHighQualityShareConfig(): ShareConfig =
        createShareConfig(
            format = ImageFormat.PNG,
            quality = 100,
            maxWidth = null,
            maxHeight = null,
            stripMetadata = false,
        )

    // ============ SharingPreferences Creation ============

    /**
     * Creates [SharingPreferences] with sensible defaults.
     */
    fun createSharingPreferences(
        defaultFormat: ImageFormat = ImageFormat.JPEG,
        defaultQuality: Int = 85,
        maxWidth: Int = 1080,
        maxHeight: Int = 1080,
        stripMetadata: Boolean = true,
        recentShareTargets: List<String> = emptyList(),
        favoriteShareTargets: List<String> = emptyList(),
    ): SharingPreferences =
        SharingPreferences(
            defaultFormat = defaultFormat,
            defaultQuality = defaultQuality,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            stripMetadata = stripMetadata,
            recentShareTargets = recentShareTargets,
            favoriteShareTargets = favoriteShareTargets,
        )

    // ============ Test Embeddings ============

    /**
     * Creates a test embedding vector.
     *
     * @param dimensions Size of the embedding vector.
     * @param seed Seed for deterministic values.
     */
    fun createTestEmbedding(
        dimensions: Int = 128,
        seed: Int = 42,
    ): FloatArray {
        val random = java.util.Random(seed.toLong())
        return FloatArray(dimensions) { random.nextFloat() * 2 - 1 }
    }

    /**
     * Creates a normalized test embedding (unit vector).
     */
    fun createNormalizedEmbedding(
        dimensions: Int = 128,
        seed: Int = 42,
    ): FloatArray {
        val embedding = createTestEmbedding(dimensions, seed)
        val magnitude = kotlin.math.sqrt(embedding.map { it * it }.sum())
        return embedding.map { it / magnitude }.toFloatArray()
    }

    // ============ Sample Data ============

    private val SAMPLE_EMOJIS =
        listOf(
            "😂", "😍", "🔥", "💀", "😭", "🤣", "❤️", "✨", "👍", "🎉",
        )

    val SAMPLE_SEARCH_QUERIES =
        listOf(
            "funny cat",
            "reaction face",
            "laughing",
            "when you realize",
            "monday mood",
            "weekend vibes",
        )

    val SAMPLE_FILE_PATHS =
        listOf(
            "/storage/emulated/0/DCIM/meme1.jpg",
            "/storage/emulated/0/Pictures/meme2.png",
            "/storage/emulated/0/Download/meme3.webp",
        )
}
