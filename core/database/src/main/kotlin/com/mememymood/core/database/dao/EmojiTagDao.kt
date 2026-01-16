package com.mememymood.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mememymood.core.database.entity.EmojiTagEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for emoji tag operations.
 */
@Dao
interface EmojiTagDao {

    /**
     * Get all emoji tags for a specific meme.
     */
    @Query("SELECT * FROM emoji_tags WHERE memeId = :memeId")
    suspend fun getEmojiTagsForMeme(memeId: Long): List<EmojiTagEntity>

    /**
     * Get all emoji tags for a specific meme as a Flow.
     */
    @Query("SELECT * FROM emoji_tags WHERE memeId = :memeId")
    fun observeEmojiTagsForMeme(memeId: Long): Flow<List<EmojiTagEntity>>

    /**
     * Insert emoji tags for a meme.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmojiTags(tags: List<EmojiTagEntity>)

    /**
     * Delete all emoji tags for a meme.
     */
    @Query("DELETE FROM emoji_tags WHERE memeId = :memeId")
    suspend fun deleteEmojiTagsForMeme(memeId: Long)

    /**
     * Get all unique emojis used in the database with their counts.
     */
    @Query("""
        SELECT emoji, emojiName, COUNT(*) as count 
        FROM emoji_tags 
        GROUP BY emoji 
        ORDER BY count DESC
    """)
    fun getAllEmojisWithCounts(): Flow<List<EmojiUsageStats>>

    /**
     * Get meme IDs that have a specific emoji.
     */
    @Query("SELECT memeId FROM emoji_tags WHERE emoji = :emoji")
    suspend fun getMemeIdsWithEmoji(emoji: String): List<Long>

    /**
     * Get meme IDs that have any of the specified emojis.
     */
    @Query("SELECT DISTINCT memeId FROM emoji_tags WHERE emoji IN (:emojis)")
    suspend fun getMemeIdsWithAnyEmoji(emojis: List<String>): List<Long>

    /**
     * Get meme IDs that have all of the specified emojis.
     */
    @Query("""
        SELECT memeId FROM emoji_tags 
        WHERE emoji IN (:emojis)
        GROUP BY memeId 
        HAVING COUNT(DISTINCT emoji) = :count
    """)
    suspend fun getMemeIdsWithAllEmojis(emojis: List<String>, count: Int): List<Long>
}

/**
 * Data class for emoji usage statistics.
 */
data class EmojiUsageStats(
    val emoji: String,
    val emojiName: String,
    val count: Int
)
