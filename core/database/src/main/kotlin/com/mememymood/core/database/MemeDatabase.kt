package com.mememymood.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeSearchDao
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.database.entity.MemeFtsEntity

/**
 * Room database for Meme My Mood app.
 */
@Database(
    entities = [
        MemeEntity::class,
        MemeFtsEntity::class,
        EmojiTagEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MemeDatabase : RoomDatabase() {
    
    /**
     * DAO for meme CRUD operations.
     */
    abstract fun memeDao(): MemeDao
    
    /**
     * DAO for full-text search operations.
     */
    abstract fun memeSearchDao(): MemeSearchDao
    
    /**
     * DAO for emoji tag operations.
     */
    abstract fun emojiTagDao(): EmojiTagDao
}
