package com.mememymood.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mememymood.core.database.dao.EmojiTagDao
import com.mememymood.core.database.dao.MemeDao
import com.mememymood.core.database.dao.MemeEmbeddingDao
import com.mememymood.core.database.dao.MemeSearchDao
import com.mememymood.core.database.dao.ShareTargetDao
import com.mememymood.core.database.entity.EmojiTagEntity
import com.mememymood.core.database.entity.MemeEmbeddingEntity
import com.mememymood.core.database.entity.MemeEntity
import com.mememymood.core.database.entity.MemeFtsEntity
import com.mememymood.core.database.entity.ShareTargetEntity

/**
 * Room database for Meme My Mood app.
 */
@Database(
    entities = [
        MemeEntity::class,
        MemeFtsEntity::class,
        EmojiTagEntity::class,
        MemeEmbeddingEntity::class,
        ShareTargetEntity::class,
    ],
    version = 9,
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

    /**
     * DAO for share target tracking.
     */
    abstract fun shareTargetDao(): ShareTargetDao

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
        
        /**
         * Migration from version 3 to 4: Add multilingual support columns.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add primaryLanguage column for the primary content language
                db.execSQL("ALTER TABLE memes ADD COLUMN primaryLanguage TEXT DEFAULT NULL")
                // Add localizationsJson column for storing additional language content
                db.execSQL("ALTER TABLE memes ADD COLUMN localizationsJson TEXT DEFAULT NULL")
            }
        }

        /**
         * Migration from version 4 to 5: Add indexing status tracking to embeddings.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add indexingAttempts column to track how many times embedding generation was attempted
                db.execSQL("ALTER TABLE meme_embeddings ADD COLUMN indexingAttempts INTEGER NOT NULL DEFAULT 0")
                // Add lastAttemptAt column to track when embedding generation was last attempted
                db.execSQL("ALTER TABLE meme_embeddings ADD COLUMN lastAttemptAt INTEGER DEFAULT NULL")
            }
        }

        /**
         * Migration from version 5 to 6: Add view tracking to memes.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add viewCount column for tracking how often a meme is viewed
                db.execSQL("ALTER TABLE memes ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0")
                // Add lastViewedAt column for tracking when last viewed
                db.execSQL("ALTER TABLE memes ADD COLUMN lastViewedAt INTEGER DEFAULT NULL")
                // Create indices for efficient sorting by view count and recency
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memes_viewCount ON memes(viewCount)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memes_lastViewedAt ON memes(lastViewedAt)")
            }
        }

        /**
         * Migration from version 6 to 7: Add index on needsRegeneration column.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_needsRegeneration ON meme_embeddings(needsRegeneration)")
            }
        }

        /**
         * Migration from version 7 to 8: Add fileHash column for duplicate detection.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE memes ADD COLUMN fileHash TEXT DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_memes_fileHash ON memes(fileHash)")
            }
        }

        /**
         * Migration from version 8 to 9: Add share_targets table.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS share_targets (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        activityName TEXT NOT NULL,
                        displayLabel TEXT NOT NULL,
                        shareCount INTEGER NOT NULL DEFAULT 0,
                        lastSharedAt INTEGER DEFAULT NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_share_targets_shareCount ON share_targets(shareCount)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_share_targets_lastSharedAt ON share_targets(lastSharedAt)")
            }
        }
    }
}
