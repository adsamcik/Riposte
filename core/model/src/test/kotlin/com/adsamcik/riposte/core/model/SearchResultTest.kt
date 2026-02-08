package com.adsamcik.riposte.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchResultTest {

    private fun createTestMeme(
        id: Long = 1L,
        title: String? = "Test Meme"
    ) = Meme(
        id = id,
        filePath = "/storage/memes/test.jpg",
        fileName = "test.jpg",
        mimeType = "image/jpeg",
        width = 1920,
        height = 1080,
        fileSizeBytes = 1024L,
        importedAt = 1234567890L,
        emojiTags = emptyList(),
        title = title
    )

    // Constructor and property tests
    @Test
    fun `searchResult stores meme correctly`() {
        val meme = createTestMeme()
        val result = SearchResult(
            meme = meme,
            relevanceScore = 0.9f,
            matchType = MatchType.TEXT
        )

        assertThat(result.meme).isEqualTo(meme)
    }

    @Test
    fun `searchResult stores relevanceScore correctly`() {
        val result = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.75f,
            matchType = MatchType.TEXT
        )

        assertThat(result.relevanceScore).isEqualTo(0.75f)
    }

    @Test
    fun `searchResult stores matchType correctly`() {
        val result = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.5f,
            matchType = MatchType.EMOJI
        )

        assertThat(result.matchType).isEqualTo(MatchType.EMOJI)
    }

    @Test
    fun `searchResult accepts minimum relevance score`() {
        val result = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.0f,
            matchType = MatchType.TEXT
        )

        assertThat(result.relevanceScore).isEqualTo(0.0f)
    }

    @Test
    fun `searchResult accepts maximum relevance score`() {
        val result = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 1.0f,
            matchType = MatchType.SEMANTIC
        )

        assertThat(result.relevanceScore).isEqualTo(1.0f)
    }

    // Copy tests
    @Test
    fun `copy creates identical result when no changes`() {
        val original = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.8f,
            matchType = MatchType.HYBRID
        )
        val copied = original.copy()

        assertThat(copied).isEqualTo(original)
    }

    @Test
    fun `copy can change relevanceScore`() {
        val original = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.5f,
            matchType = MatchType.TEXT
        )
        val copied = original.copy(relevanceScore = 0.9f)

        assertThat(copied.relevanceScore).isEqualTo(0.9f)
        assertThat(copied.meme).isEqualTo(original.meme)
        assertThat(copied.matchType).isEqualTo(original.matchType)
    }

    @Test
    fun `copy can change matchType`() {
        val original = SearchResult(
            meme = createTestMeme(),
            relevanceScore = 0.7f,
            matchType = MatchType.TEXT
        )
        val copied = original.copy(matchType = MatchType.HYBRID)

        assertThat(copied.matchType).isEqualTo(MatchType.HYBRID)
        assertThat(copied.relevanceScore).isEqualTo(original.relevanceScore)
    }

    @Test
    fun `copy can change meme`() {
        val meme1 = createTestMeme(id = 1L, title = "Meme 1")
        val meme2 = createTestMeme(id = 2L, title = "Meme 2")
        val original = SearchResult(
            meme = meme1,
            relevanceScore = 0.6f,
            matchType = MatchType.EMOJI
        )
        val copied = original.copy(meme = meme2)

        assertThat(copied.meme).isEqualTo(meme2)
        assertThat(copied.meme.id).isEqualTo(2L)
    }

    // Equality tests
    @Test
    fun `searchResults with same properties are equal`() {
        val meme = createTestMeme()
        val result1 = SearchResult(
            meme = meme,
            relevanceScore = 0.8f,
            matchType = MatchType.TEXT
        )
        val result2 = SearchResult(
            meme = meme,
            relevanceScore = 0.8f,
            matchType = MatchType.TEXT
        )

        assertThat(result1).isEqualTo(result2)
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode())
    }

    @Test
    fun `searchResults with different memes are not equal`() {
        val result1 = SearchResult(
            meme = createTestMeme(id = 1L),
            relevanceScore = 0.8f,
            matchType = MatchType.TEXT
        )
        val result2 = SearchResult(
            meme = createTestMeme(id = 2L),
            relevanceScore = 0.8f,
            matchType = MatchType.TEXT
        )

        assertThat(result1).isNotEqualTo(result2)
    }

    @Test
    fun `searchResults with different scores are not equal`() {
        val meme = createTestMeme()
        val result1 = SearchResult(
            meme = meme,
            relevanceScore = 0.5f,
            matchType = MatchType.TEXT
        )
        val result2 = SearchResult(
            meme = meme,
            relevanceScore = 0.9f,
            matchType = MatchType.TEXT
        )

        assertThat(result1).isNotEqualTo(result2)
    }

    @Test
    fun `searchResults with different matchTypes are not equal`() {
        val meme = createTestMeme()
        val result1 = SearchResult(
            meme = meme,
            relevanceScore = 0.8f,
            matchType = MatchType.TEXT
        )
        val result2 = SearchResult(
            meme = meme,
            relevanceScore = 0.8f,
            matchType = MatchType.EMOJI
        )

        assertThat(result1).isNotEqualTo(result2)
    }
}

class MatchTypeTest {

    @Test
    fun `matchType has TEXT value`() {
        assertThat(MatchType.TEXT).isNotNull()
        assertThat(MatchType.TEXT.name).isEqualTo("TEXT")
    }

    @Test
    fun `matchType has EMOJI value`() {
        assertThat(MatchType.EMOJI).isNotNull()
        assertThat(MatchType.EMOJI.name).isEqualTo("EMOJI")
    }

    @Test
    fun `matchType has SEMANTIC value`() {
        assertThat(MatchType.SEMANTIC).isNotNull()
        assertThat(MatchType.SEMANTIC.name).isEqualTo("SEMANTIC")
    }

    @Test
    fun `matchType has HYBRID value`() {
        assertThat(MatchType.HYBRID).isNotNull()
        assertThat(MatchType.HYBRID.name).isEqualTo("HYBRID")
    }

    @Test
    fun `matchType has exactly four values`() {
        assertThat(MatchType.entries).hasSize(4)
    }

    @Test
    fun `matchType values are in expected order`() {
        val values = MatchType.entries.toTypedArray()
        
        assertThat(values[0]).isEqualTo(MatchType.TEXT)
        assertThat(values[1]).isEqualTo(MatchType.EMOJI)
        assertThat(values[2]).isEqualTo(MatchType.SEMANTIC)
        assertThat(values[3]).isEqualTo(MatchType.HYBRID)
    }

    @Test
    fun `matchType valueOf returns correct value for TEXT`() {
        assertThat(MatchType.valueOf("TEXT")).isEqualTo(MatchType.TEXT)
    }

    @Test
    fun `matchType valueOf returns correct value for EMOJI`() {
        assertThat(MatchType.valueOf("EMOJI")).isEqualTo(MatchType.EMOJI)
    }

    @Test
    fun `matchType valueOf returns correct value for SEMANTIC`() {
        assertThat(MatchType.valueOf("SEMANTIC")).isEqualTo(MatchType.SEMANTIC)
    }

    @Test
    fun `matchType valueOf returns correct value for HYBRID`() {
        assertThat(MatchType.valueOf("HYBRID")).isEqualTo(MatchType.HYBRID)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `matchType valueOf throws for invalid value`() {
        MatchType.valueOf("INVALID")
    }

    @Test
    fun `matchType ordinal values are sequential`() {
        assertThat(MatchType.TEXT.ordinal).isEqualTo(0)
        assertThat(MatchType.EMOJI.ordinal).isEqualTo(1)
        assertThat(MatchType.SEMANTIC.ordinal).isEqualTo(2)
        assertThat(MatchType.HYBRID.ordinal).isEqualTo(3)
    }
}
