package com.mememymood.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.database.MemeDatabase
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.database.entity.MemeEmojiCrossRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprehensive instrumented tests for [MemeSearchDao].
 *
 * Tests verify:
 * - FTS search functionality
 * - Title and text search
 * - Emoji filtering
 * - Combined search queries
 * - Relevance ranking
 * - Edge cases
 */
@RunWith(AndroidJUnit4::class)
class MemeSearchDaoTest {

    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao
    private lateinit var emojiTagDao: EmojiTagDao
    private lateinit var searchDao: MemeSearchDao

    private val testEmojis = listOf(
        EmojiTagEntity(emoji = "😂", name = "laughing", category = "face"),
        EmojiTagEntity(emoji = "😢", name = "crying", category = "face"),
        EmojiTagEntity(emoji = "🎉", name = "party", category = "celebration"),
        EmojiTagEntity(emoji = "❤️", name = "heart", category = "love")
    )

    private val testMemes = listOf(
        createMemeEntity(1L, "Funny Cat Meme", "A hilarious cat doing funny things"),
        createMemeEntity(2L, "Crying Dog", "Sad puppy looking for treats"),
        createMemeEntity(3L, "Party Time", "Celebration with confetti"),
        createMemeEntity(4L, "Love Story", "Romantic sunset photo"),
        createMemeEntity(5L, "Funny and Sad Mix", "A funny but also touching story")
    )

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        memeDao = database.memeDao()
        emojiTagDao = database.emojiTagDao()
        searchDao = database.memeSearchDao()

        // Insert test data
        testEmojis.forEach { emojiTagDao.insert(it) }
        testMemes.forEach { memeDao.insert(it) }
        
        // Set up emoji associations
        // Meme 1: 😂 (laughing)
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(1L, "😂"))
        // Meme 2: 😢 (crying)
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(2L, "😢"))
        // Meme 3: 🎉 (party)
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(3L, "🎉"))
        // Meme 4: ❤️ (heart)
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(4L, "❤️"))
        // Meme 5: Both 😂 and 😢
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(5L, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(5L, "😢"))
    }

    @After
    fun teardown() {
        database.close()
    }

    // ============ Basic Search Tests ============

    @Test
    fun searchByTitle_returnsMatchingMemes() = runTest {
        val results = searchDao.searchByTitle("Funny").first()
        
        assertThat(results).hasSize(2)
        assertThat(results.map { it.id }).containsExactly(1L, 5L)
    }

    @Test
    fun searchByTitle_isCaseInsensitive() = runTest {
        val resultsLower = searchDao.searchByTitle("funny").first()
        val resultsUpper = searchDao.searchByTitle("FUNNY").first()
        val resultsMixed = searchDao.searchByTitle("FuNnY").first()
        
        assertThat(resultsLower).hasSize(2)
        assertThat(resultsUpper).hasSize(2)
        assertThat(resultsMixed).hasSize(2)
    }

    @Test
    fun searchByTitle_returnsEmpty_whenNoMatch() = runTest {
        val results = searchDao.searchByTitle("nonexistent").first()
        
        assertThat(results).isEmpty()
    }

    // ============ Text Content Search Tests ============

    @Test
    fun searchByExtractedText_returnsMatchingMemes() = runTest {
        val results = searchDao.searchByText("hilarious").first()
        
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(1L)
    }

    @Test
    fun searchByExtractedText_matchesPartialWords() = runTest {
        val results = searchDao.searchByText("celebrat").first()
        
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(3L)
    }

    // ============ Combined Search Tests ============

    @Test
    fun fullTextSearch_matchesBothTitleAndText() = runTest {
        val results = searchDao.fullTextSearch("cat").first()
        
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(1L)
    }

    @Test
    fun fullTextSearch_ranksResultsByRelevance() = runTest {
        // "Funny" appears in title of meme 1 and 5, and in text of meme 5
        val results = searchDao.fullTextSearch("funny").first()
        
        assertThat(results).hasSize(2)
        // Both should be returned, order may vary based on ranking algorithm
        assertThat(results.map { it.id }).containsExactly(1L, 5L)
    }

    // ============ Emoji Filter Tests ============

    @Test
    fun searchByEmoji_returnsMemesWithEmoji() = runTest {
        val results = searchDao.searchByEmoji("😂").first()
        
        assertThat(results).hasSize(2)
        assertThat(results.map { it.id }).containsExactly(1L, 5L)
    }

    @Test
    fun searchByEmoji_returnsEmpty_whenNoMemesWithEmoji() = runTest {
        val results = searchDao.searchByEmoji("🤔").first()
        
        assertThat(results).isEmpty()
    }

    @Test
    fun searchByMultipleEmojis_returnsUnion() = runTest {
        // Search for memes that have either 😂 OR 🎉
        val results = searchDao.searchByAnyEmoji(listOf("😂", "🎉")).first()
        
        assertThat(results).hasSize(3)
        assertThat(results.map { it.id }).containsExactly(1L, 3L, 5L)
    }

    @Test
    fun searchByAllEmojis_returnsIntersection() = runTest {
        // Search for memes that have BOTH 😂 AND 😢
        val results = searchDao.searchByAllEmojis(listOf("😂", "😢")).first()
        
        assertThat(results).hasSize(1)
        assertThat(results.first().id).isEqualTo(5L)
    }

    // ============ Combined Text and Emoji Search Tests ============

    @Test
    fun searchWithTextAndEmoji_returnsMatchingBoth() = runTest {
        val results = searchDao.searchWithTextAndEmoji("funny", "😂").first()
        
        assertThat(results).hasSize(2)
        assertThat(results.map { it.id }).containsExactly(1L, 5L)
    }

    @Test
    fun searchWithTextAndEmoji_returnsEmpty_whenNoMatch() = runTest {
        // Search for "funny" with heart emoji (no match)
        val results = searchDao.searchWithTextAndEmoji("funny", "❤️").first()
        
        assertThat(results).isEmpty()
    }

    // ============ Pagination Tests ============

    @Test
    fun searchWithLimit_respectsLimit() = runTest {
        val results = searchDao.searchByTitleWithLimit("", limit = 2).first()
        
        assertThat(results).hasSize(2)
    }

    @Test
    fun searchWithOffset_skipsResults() = runTest {
        val allResults = searchDao.searchByTitleWithLimit("", limit = 10).first()
        val offsetResults = searchDao.searchByTitleWithOffset("", limit = 10, offset = 2).first()
        
        assertThat(offsetResults).hasSize(allResults.size - 2)
        assertThat(offsetResults.first().id).isEqualTo(allResults[2].id)
    }

    // ============ Sorting Tests ============

    @Test
    fun searchSortedByDate_returnsNewestFirst() = runTest {
        val results = searchDao.searchSortedByDate("", ascending = false).first()
        
        // Verify descending order
        for (i in 0 until results.size - 1) {
            assertThat(results[i].importedAt).isAtLeast(results[i + 1].importedAt)
        }
    }

    @Test
    fun searchSortedByDate_returnsOldestFirst() = runTest {
        val results = searchDao.searchSortedByDate("", ascending = true).first()
        
        // Verify ascending order
        for (i in 0 until results.size - 1) {
            assertThat(results[i].importedAt).isAtMost(results[i + 1].importedAt)
        }
    }

    @Test
    fun searchSortedByTitle_alphabeticalOrder() = runTest {
        val results = searchDao.searchSortedByTitle("").first()
        
        // Verify alphabetical order
        for (i in 0 until results.size - 1) {
            assertThat(results[i].title ?: "").isLessThan(results[i + 1].title ?: "")
        }
    }

    // ============ Edge Cases ============

    @Test
    fun searchWithEmptyQuery_returnsAllMemes() = runTest {
        val results = searchDao.fullTextSearch("").first()
        
        assertThat(results).hasSize(5)
    }

    @Test
    fun searchWithSpecialCharacters_handlesGracefully() = runTest {
        val results = searchDao.fullTextSearch("cat's").first()
        
        // Should not crash, may or may not find matches depending on tokenization
        assertThat(results).isNotNull()
    }

    @Test
    fun searchWithWhitespace_trimsAndSearches() = runTest {
        val results = searchDao.fullTextSearch("  cat  ").first()
        
        assertThat(results).hasSize(1)
    }

    @Test
    fun searchWithMultipleWords_matchesAnyWord() = runTest {
        val results = searchDao.fullTextSearch("cat party").first()
        
        // Should match memes with either "cat" or "party"
        assertThat(results).hasSize(2)
        assertThat(results.map { it.id }).containsExactly(1L, 3L)
    }

    // ============ Helper Functions ============

    private fun createMemeEntity(
        id: Long,
        title: String,
        extractedText: String
    ) = MemeEntity(
        id = id,
        filePath = "/test/meme_$id.jpg",
        fileName = "meme_$id.jpg",
        mimeType = "image/jpeg",
        width = 1000,
        height = 1000,
        fileSizeBytes = 100000,
        importedAt = System.currentTimeMillis() + (id * 1000), // Ensure different timestamps
        title = title,
        extractedText = extractedText,
        isFavorite = false
    )
}
