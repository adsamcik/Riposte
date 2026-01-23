package com.mememymood.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeEmbeddingDao
import com.mememymood.core.database.dao.MemeSearchDao
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEmbeddingEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.database.entity.MemeFtsEntity

/**
 * Room database for Meme My Mood app.
 */
@Database(
    entities = [
        MemeEntity::class,
        MemeFtsEntity::class,
        EmojiTagEntity::class,
        MemeEmbeddingEntity::class
    ],
    version = 3,
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
    
    /**
     * DAO for embedding operations.
     */
    abstract fun memeEmbeddingDao(): MemeEmbeddingDao

    companion object {
        /**
         * Migration from version 1 to 2: Add meme_embeddings table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create the meme_embeddings table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS meme_embeddings (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        memeId INTEGER NOT NULL,
                        embedding BLOB NOT NULL,
                        dimension INTEGER NOT NULL,
                        modelVersion TEXT NOT NULL,
                        generatedAt INTEGER NOT NULL,
                        sourceTextHash TEXT,
                        needsRegeneration INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (memeId) REFERENCES memes(id) ON DELETE CASCADE
                    )
                """)
                
                // Create indices
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_meme_embeddings_memeId ON meme_embeddings(memeId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_modelVersion ON meme_embeddings(modelVersion)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_generatedAt ON meme_embeddings(generatedAt)")
                
                // Migrate existing embeddings from memes table to meme_embeddings table
                db.execSQL("""
                    INSERT INTO meme_embeddings (memeId, embedding, dimension, modelVersion, generatedAt, needsRegeneration)
                    SELECT id, embedding, 128, 'simple_hash:1.0.0', importedAt, 1
                    FROM memes
                    WHERE embedding IS NOT NULL
                """)
            }
        }

        /**
         * Migration from version 2 to 3: Add indexes for common query patterns.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add index on importedAt for sorting queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memes_importedAt ON memes(importedAt)")
                // Add index on isFavorite for filtering queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memes_isFavorite ON memes(isFavorite)")
                // Add unique index on filePath for duplicate detection
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_memes_filePath ON memes(filePath)")
            }
        }
    }
}
