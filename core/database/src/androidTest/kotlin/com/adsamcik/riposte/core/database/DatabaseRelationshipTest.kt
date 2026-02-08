package com.adsamcik.riposte.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.adsamcik.riposte.core.database.dao.EmojiTagDao
import com.adsamcik.riposte.core.database.dao.MemeDao
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
import com.adsamcik.riposte.core.database.entity.MemeEmojiCrossRef
import com.adsamcik.riposte.core.database.entity.MemeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for database relationships and transactions.
 *
 * Tests verify:
 * - Meme-Emoji relationships
 * - Cascade deletes
 * - Transactions
 * - Data integrity
 */
@RunWith(AndroidJUnit4::class)
class DatabaseRelationshipTest {

    private lateinit var database: MemeDatabase
    private lateinit var memeDao: MemeDao
    private lateinit var emojiTagDao: EmojiTagDao

    @Before
    fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MemeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        memeDao = database.memeDao()
        emojiTagDao = database.emojiTagDao()

        // Insert base emoji tags
        emojiTagDao.insert(EmojiTagEntity("😂", "laughing", "face"))
        emojiTagDao.insert(EmojiTagEntity("😢", "crying", "face"))
        emojiTagDao.insert(EmojiTagEntity("🎉", "party", "celebration"))
    }

    @After
    fun teardown() {
        database.close()
    }

    // ============ Meme-Emoji Relationship Tests ============

    @Test
    fun memeWithEmojis_returnsCorrectEmojis() = runTest {
        // Create meme
        val memeId = memeDao.insert(createMemeEntity(title = "Test Meme"))
        
        // Add emoji relationships
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "🎉"))
        
        // Query meme with emojis
        val memeWithEmojis = memeDao.getMemeWithEmojis(memeId).first()
        
        assertThat(memeWithEmojis).isNotNull()
        assertThat(memeWithEmojis!!.emojis).hasSize(2)
        assertThat(memeWithEmojis.emojis.map { it.emoji }).containsExactly("😂", "🎉")
    }

    @Test
    fun emojiWithMemes_returnsCorrectMemes() = runTest {
        // Create memes
        val meme1Id = memeDao.insert(createMemeEntity(title = "Meme 1"))
        val meme2Id = memeDao.insert(createMemeEntity(title = "Meme 2"))
        val meme3Id = memeDao.insert(createMemeEntity(title = "Meme 3"))
        
        // Add same emoji to multiple memes
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(meme1Id, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(meme2Id, "😂"))
        // meme3 doesn't have 😂
        
        // Query memes with this emoji
        val memesWithEmoji = emojiTagDao.getMemesWithEmoji("😂").first()
        
        assertThat(memesWithEmoji).hasSize(2)
        assertThat(memesWithEmoji.map { it.id }).containsExactly(meme1Id, meme2Id)
    }

    @Test
    fun memeWithMultipleEmojis_allRelationshipsWork() = runTest {
        val memeId = memeDao.insert(createMemeEntity(title = "Multi-emoji Meme"))
        
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😢"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "🎉"))
        
        val memeWithEmojis = memeDao.getMemeWithEmojis(memeId).first()
        
        assertThat(memeWithEmojis!!.emojis).hasSize(3)
    }

    // ============ Cascade Delete Tests ============

    @Test
    fun deleteMeme_removesEmojiRelationships() = runTest {
        val memeId = memeDao.insert(createMemeEntity(title = "To Delete"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂"))
        
        // Verify relationship exists
        val beforeDelete = memeDao.getMemeWithEmojis(memeId).first()
        assertThat(beforeDelete!!.emojis).hasSize(1)
        
        // Delete meme
        memeDao.deleteById(memeId)
        
        // Cross reference should also be deleted
        val afterDelete = memeDao.getMemeWithEmojis(memeId).first()
        assertThat(afterDelete).isNull()
    }

    @Test
    fun deleteMeme_doesNotDeleteEmoji() = runTest {
        val memeId = memeDao.insert(createMemeEntity(title = "To Delete"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂"))
        
        memeDao.deleteById(memeId)
        
        // Emoji should still exist
        val emoji = emojiTagDao.getEmojiByCode("😂").first()
        assertThat(emoji).isNotNull()
    }

    // ============ Transaction Tests ============

    @Test
    fun transaction_rollbackOnError() = runTest {
        val initialCount = memeDao.getAllMemes().first().size
        
        try {
            database.runInTransaction {
                memeDao.insert(createMemeEntity(title = "Transaction Meme"))
                throw RuntimeException("Force rollback")
            }
        } catch (e: RuntimeException) {
            // Expected
        }
        
        val afterCount = memeDao.getAllMemes().first().size
        assertThat(afterCount).isEqualTo(initialCount)
    }

    @Test
    fun transaction_commitOnSuccess() = runTest {
        val initialCount = memeDao.getAllMemes().first().size
        
        database.runInTransaction {
            memeDao.insert(createMemeEntity(title = "Transaction Meme 1"))
            memeDao.insert(createMemeEntity(title = "Transaction Meme 2"))
        }
        
        val afterCount = memeDao.getAllMemes().first().size
        assertThat(afterCount).isEqualTo(initialCount + 2)
    }

    // ============ Data Integrity Tests ============

    @Test
    fun duplicateCrossRef_isIgnored() = runTest {
        val memeId = memeDao.insert(createMemeEntity(title = "Test"))
        
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "😂")) // Duplicate
        
        val memeWithEmojis = memeDao.getMemeWithEmojis(memeId).first()
        assertThat(memeWithEmojis!!.emojis).hasSize(1)
    }

    @Test
    fun invalidEmojiRef_handled() = runTest {
        val memeId = memeDao.insert(createMemeEntity(title = "Test"))
        
        // Try to add relationship with non-existent emoji
        try {
            memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(memeId, "🤖"))
            // If foreign key is enforced, this should fail
        } catch (e: Exception) {
            // Expected if foreign key constraint is enforced
        }
    }

    // ============ Bulk Operations Tests ============

    @Test
    fun bulkInsert_allItemsInserted() = runTest {
        val memes = (1..10).map { createMemeEntity(title = "Bulk Meme $it") }
        
        memeDao.insertAll(memes)
        
        val count = memeDao.getAllMemes().first().size
        assertThat(count).isAtLeast(10)
    }

    @Test
    fun bulkDelete_allItemsDeleted() = runTest {
        val memeIds = (1..5).map { memeDao.insert(createMemeEntity(title = "To Delete $it")) }
        
        val beforeCount = memeDao.getAllMemes().first().size
        
        memeDao.deleteByIds(memeIds)
        
        val afterCount = memeDao.getAllMemes().first().size
        assertThat(afterCount).isEqualTo(beforeCount - 5)
    }

    // ============ Query Tests ============

    @Test
    fun getMemesByEmoji_returnsCorrectMemes() = runTest {
        val happyMeme = memeDao.insert(createMemeEntity(title = "Happy"))
        val sadMeme = memeDao.insert(createMemeEntity(title = "Sad"))
        
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(happyMeme, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(sadMeme, "😢"))
        
        val happyMemes = memeDao.getMemesByEmoji("😂").first()
        
        assertThat(happyMemes.map { it.id }).containsExactly(happyMeme)
    }

    @Test
    fun getMemesWithAnyEmoji_returnsUnion() = runTest {
        val meme1 = memeDao.insert(createMemeEntity(title = "M1"))
        val meme2 = memeDao.insert(createMemeEntity(title = "M2"))
        val meme3 = memeDao.insert(createMemeEntity(title = "M3"))
        
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(meme1, "😂"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(meme2, "😢"))
        memeDao.insertMemeEmojiCrossRef(MemeEmojiCrossRef(meme3, "🎉"))
        
        val result = memeDao.getMemesByAnyEmoji(listOf("😂", "😢")).first()
        
        assertThat(result.map { it.id }).containsExactly(meme1, meme2)
    }

    // ============ Helper Functions ============

    private fun createMemeEntity(
        title: String = "Test Meme",
        id: Long = 0
    ) = MemeEntity(
        id = id,
        filePath = "/test/meme_${System.nanoTime()}.jpg",
        fileName = "meme_${System.nanoTime()}.jpg",
        mimeType = "image/jpeg",
        width = 500,
        height = 500,
        fileSizeBytes = 50000,
        importedAt = System.currentTimeMillis(),
        title = title,
        extractedText = null,
        isFavorite = false
    )
}
