package com.mememymood.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.mememymood.core.database.entity.MemeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for full-text search operations.
 */
@Dao
interface MemeSearchDao {

    /**
     * Full-text search across all searchable fields.
     * Uses FTS4 MATCH syntax for efficient text search.
     * 
     * @param query The search query. Supports FTS4 query syntax:
     *              - Simple words: "funny"
     *              - Phrases: "\"funny cat\""
     *              - Prefix: "fun*"
     *              - Boolean: "funny AND cat" or "funny OR dog"
     */
    @Query("""
        SELECT m.* FROM memes m
        INNER JOIN memes_fts fts ON m.rowid = fts.rowid
        WHERE memes_fts MATCH :query
        ORDER BY m.importedAt DESC
    """)
    fun searchMemes(query: String): Flow<List<MemeEntity>>

    /**
     * Search memes and return with relevance ranking.
     * Uses BM25 ranking for better search results.
     */
    @Query("""
        SELECT m.*, bm25(memes_fts) as `rank`
        FROM memes m
        INNER JOIN memes_fts fts ON m.rowid = fts.rowid
        WHERE memes_fts MATCH :query
        ORDER BY `rank`
    """)
    suspend fun searchMemesRanked(query: String): List<MemeWithRank>

    /**
     * Search by emoji character or name.
     */
    @Query("""
        SELECT m.* FROM memes m
        INNER JOIN memes_fts fts ON m.rowid = fts.rowid
        WHERE memes_fts MATCH :emojiQuery
        ORDER BY m.importedAt DESC
    """)
    fun searchByEmoji(emojiQuery: String): Flow<List<MemeEntity>>

    /**
     * Search only in title and description.
     */
    @Query("""
        SELECT m.* FROM memes m
        INNER JOIN memes_fts fts ON m.rowid = fts.rowid
        WHERE memes_fts MATCH 'title:' || :query || '* OR description:' || :query || '*'
        ORDER BY m.importedAt DESC
    """)
    fun searchTitleAndDescription(query: String): Flow<List<MemeEntity>>

    /**
     * Get search suggestions based on existing content.
     */
    @Query("""
        SELECT DISTINCT title FROM memes
        WHERE title LIKE :prefix || '%'
        LIMIT 10
    """)
    suspend fun getSearchSuggestions(prefix: String): List<String>
}

/**
 * Data class for meme with search rank.
 */
data class MemeWithRank(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val importedAt: Long,
    val emojiTagsJson: String,
    val title: String?,
    val description: String?,
    val textContent: String?,
    val embedding: ByteArray?,
    val isFavorite: Boolean,
    val rank: Double
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MemeWithRank
        return id == other.id && rank == other.rank
    }

    override fun hashCode(): Int {
        return 31 * id.hashCode() + rank.hashCode()
    }
}
