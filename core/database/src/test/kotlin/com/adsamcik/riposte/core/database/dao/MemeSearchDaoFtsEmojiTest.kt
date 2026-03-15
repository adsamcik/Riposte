package com.adsamcik.riposte.core.database.dao

import android.content.Context
import androidx.room.Room
import app.cash.turbine.test
import com.adsamcik.riposte.core.database.MemeDatabase
import com.adsamcik.riposte.core.database.entity.MemeEntity
import com.adsamcik.riposte.core.database.util.FtsQuerySanitizer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that FTS4 emoji search actually works end-to-end through the Room database.
 *
 * Regression tests for:
 * - bm25() being FTS5-only (must not be used on FTS4 tables)
 * - Emoji characters being properly tokenized and matched by FTS4's simple tokenizer
 * - Variation selector normalization consistency between storage and query
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MemeSearchDaoFtsEmojiTest {
    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao
    private lateinit var searchDao: MemeSearchDao

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, MemeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        memeDao = database.memeDao()
        searchDao = database.memeSearchDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    // region FTS4 Emoji MATCH Tests

    @Test
    fun `searchByEmoji returns memes with matching emoji in emojiTagsJson`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/emoji1.png",
                    emojiTagsJson = """["😂","🔥"]""",
                    title = "Laughing fire",
                ),
            )
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/emoji2.png",
                    emojiTagsJson = """["😢","💔"]""",
                    title = "Sad broken heart",
                ),
            )

            val emojiQuery = FtsQuerySanitizer.prepareEmojiQuery("😂")

            searchDao.searchByEmoji(emojiQuery).test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                assertThat(results[0].title).isEqualTo("Laughing fire")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `searchMemesRanked does not crash on FTS4 table`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/ranked.png",
                    title = "Funny cat",
                    description = "A funny cat meme",
                ),
            )

            // This previously used bm25() which is FTS5-only and always threw
            // "no such function: bm25" on FTS4, causing silent search failure.
            val ftsQuery = FtsQuerySanitizer.prepareForMatch("funny")
            val results = searchDao.searchMemesRanked(ftsQuery)

            assertThat(results).hasSize(1)
            assertThat(results[0].title).isEqualTo("Funny cat")
        }

    @Test
    fun `searchMemesRanked finds emoji via prepareForMatch`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/emoji_ranked.png",
                    emojiTagsJson = """["😂","🎉"]""",
                    title = "Party laugh",
                ),
            )

            // Emoji search via hybrid path uses prepareForMatch, not prepareEmojiQuery
            val ftsQuery = FtsQuerySanitizer.prepareForMatch("😂")
            val results = searchDao.searchMemesRanked(ftsQuery)

            assertThat(results).hasSize(1)
            assertThat(results[0].title).isEqualTo("Party laugh")
        }

    @Test
    fun `searchByEmoji finds multiple memes with same emoji`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/multi1.png",
                    emojiTagsJson = """["😂","✨"]""",
                ),
            )
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/multi2.png",
                    emojiTagsJson = """["😂","🔥"]""",
                ),
            )
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/multi3.png",
                    emojiTagsJson = """["😢","💔"]""",
                ),
            )

            val emojiQuery = FtsQuerySanitizer.prepareEmojiQuery("😂")

            searchDao.searchByEmoji(emojiQuery).test {
                val results = awaitItem()
                assertThat(results).hasSize(2)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `searchByEmoji returns empty for non-existent emoji`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/no_match.png",
                    emojiTagsJson = """["😂"]""",
                ),
            )

            val emojiQuery = FtsQuerySanitizer.prepareEmojiQuery("🦄")

            searchDao.searchByEmoji(emojiQuery).test {
                val results = awaitItem()
                assertThat(results).isEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `variation selector stripped emoji matches stored data without variation selector`() =
        runTest {
            // Store emoji without variation selector (normalized at import time)
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/vs_stripped.png",
                    emojiTagsJson = """["❤"]""",
                ),
            )

            // Query with variation selector — FtsQuerySanitizer strips it
            val emojiWithVS = "❤\uFE0F"
            val emojiQuery = FtsQuerySanitizer.prepareEmojiQuery(emojiWithVS)

            searchDao.searchByEmoji(emojiQuery).test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    // region FTS4 Text + Emoji Combined Tests

    @Test
    fun `searchMemes via MATCH finds by title text`() =
        runTest {
            memeDao.insertMeme(
                createMeme(
                    filePath = "/test/text1.png",
                    title = "Hilarious meme",
                ),
            )

            val ftsQuery = FtsQuerySanitizer.prepareForMatch("hilarious")

            searchDao.searchMemes(ftsQuery).test {
                val results = awaitItem()
                assertThat(results).hasSize(1)
                assertThat(results[0].title).isEqualTo("Hilarious meme")
                cancelAndIgnoreRemainingEvents()
            }
        }

    // endregion

    private fun createMeme(
        filePath: String = "/test/meme.png",
        emojiTagsJson: String = "[]",
        title: String? = null,
        description: String? = null,
    ) = MemeEntity(
        filePath = filePath,
        fileName = filePath.substringAfterLast("/"),
        mimeType = "image/png",
        width = 1024,
        height = 768,
        fileSizeBytes = 102400,
        importedAt = System.currentTimeMillis(),
        emojiTagsJson = emojiTagsJson,
        title = title,
        description = description,
    )
}
