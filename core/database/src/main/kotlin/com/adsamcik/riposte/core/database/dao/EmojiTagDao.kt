package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.riposte.core.database.entity.EmojiTagEntity
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
    @Query(
        """
        SELECT emoji, emojiName, COUNT(*) as count 
        FROM emoji_tags 
        GROUP BY emoji 
        ORDER BY count DESC
    """,
    )
    fun getAllEmojisWithCounts(): Flow<List<EmojiUsageStats>>

    /**
     * Get all unique emojis ordered by the total usage (share count) of their tagged memes.
     * Emojis whose memes are shared/used most often appear first.
     * Falls back to tag count, then alphabetical order for ties.
     */
    @Query(
        """
        SELECT e.emoji, e.emojiName, COALESCE(SUM(m.useCount), 0) as totalUsage
        FROM emoji_tags e
        INNER JOIN memes m ON e.memeId = m.id
        GROUP BY e.emoji
        ORDER BY totalUsage DESC, COUNT(*) DESC, e.emoji ASC
    """,
    )
    fun getEmojisOrderedByUsage(): Flow<List<EmojiUsageBySharing>>

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
    @Query(
        """
        SELECT memeId FROM emoji_tags 
        WHERE emoji IN (:emojis)
        GROUP BY memeId 
        HAVING COUNT(DISTINCT emoji) = :count
    """,
    )
    suspend fun getMemeIdsWithAllEmojis(
        emojis: List<String>,
        count: Int,
    ): List<Long>
}

/**
 * Data class for emoji usage statistics.
 */
data class EmojiUsageStats(
    val emoji: String,
    val emojiName: String,
    val count: Int,
)

/**
 * Data class for emoji usage ranked by total share/use count of tagged memes.
 */
data class EmojiUsageBySharing(
    val emoji: String,
    val emojiName: String,
    val totalUsage: Int,
)
