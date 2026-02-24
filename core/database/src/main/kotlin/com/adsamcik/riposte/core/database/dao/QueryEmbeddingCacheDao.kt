package com.adsamcik.riposte.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.adsamcik.riposte.core.database.entity.QueryEmbeddingCacheEntity

@Dao
interface QueryEmbeddingCacheDao {
    /** Get cached embedding by query hash and model version. */
    @Query(
        "SELECT * FROM query_embedding_cache WHERE queryHash = :queryHash AND modelVersion = :modelVersion LIMIT 1",
    )
    suspend fun get(queryHash: String, modelVersion: String): QueryEmbeddingCacheEntity?

    /** Insert or replace a cache entry. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: QueryEmbeddingCacheEntity)

    /** Update the accessedAt timestamp (LRU tracking). */
    @Query("UPDATE query_embedding_cache SET accessedAt = :accessedAt WHERE queryHash = :queryHash")
    suspend fun touchAccessTime(queryHash: String, accessedAt: Long)

    /** Delete all entries for an outdated model version. */
    @Query("DELETE FROM query_embedding_cache WHERE modelVersion != :currentVersion")
    suspend fun deleteOutdatedEntries(currentVersion: String)

    /** Delete the oldest entries to keep the cache at max size. */
    @Query(
        """DELETE FROM query_embedding_cache WHERE queryHash IN 
        (SELECT queryHash FROM query_embedding_cache ORDER BY accessedAt ASC LIMIT :count)""",
    )
    suspend fun deleteOldest(count: Int)

    /** Count total cache entries. */
    @Query("SELECT COUNT(*) FROM query_embedding_cache")
    suspend fun count(): Int

    /** Clear the entire cache. */
    @Query("DELETE FROM query_embedding_cache")
    suspend fun clearAll()
}
