package com.adsamcik.riposte.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration from version 1 to 2:
 * - Adds searchPhrasesJson column to memes table
 * - Adds embeddingType column to meme_embeddings table
 * - Recreates meme_embeddings with new unique index (memeId, embeddingType)
 * - Rebuilds FTS index to include searchPhrasesJson
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Add searchPhrasesJson to memes table
        db.execSQL("ALTER TABLE memes ADD COLUMN searchPhrasesJson TEXT DEFAULT NULL")

        // 2. Recreate meme_embeddings with embeddingType column and new index
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS meme_embeddings_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                memeId INTEGER NOT NULL,
                embeddingType TEXT NOT NULL DEFAULT 'content',
                embedding BLOB NOT NULL,
                dimension INTEGER NOT NULL,
                modelVersion TEXT NOT NULL,
                generatedAt INTEGER NOT NULL,
                sourceTextHash TEXT,
                needsRegeneration INTEGER NOT NULL DEFAULT 0,
                indexingAttempts INTEGER NOT NULL DEFAULT 0,
                lastAttemptAt INTEGER,
                FOREIGN KEY(memeId) REFERENCES memes(id) ON DELETE CASCADE
            )"""
        )
        db.execSQL(
            """INSERT INTO meme_embeddings_new 
                (id, memeId, embeddingType, embedding, dimension, modelVersion, 
                 generatedAt, sourceTextHash, needsRegeneration, indexingAttempts, lastAttemptAt)
               SELECT id, memeId, 'content', embedding, dimension, modelVersion,
                      generatedAt, sourceTextHash, needsRegeneration, indexingAttempts, lastAttemptAt
               FROM meme_embeddings"""
        )
        db.execSQL("DROP TABLE meme_embeddings")
        db.execSQL("ALTER TABLE meme_embeddings_new RENAME TO meme_embeddings")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_meme_embeddings_memeId_embeddingType ON meme_embeddings (memeId, embeddingType)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_memeId ON meme_embeddings (memeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_modelVersion ON meme_embeddings (modelVersion)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_generatedAt ON meme_embeddings (generatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_needsRegeneration ON meme_embeddings (needsRegeneration)")

        // 3. Rebuild FTS index to pick up new searchPhrasesJson column
        db.execSQL("INSERT INTO memes_fts(memes_fts) VALUES('rebuild')")
    }
}

/**
 * Migration from version 2 to 3:
 * - Adds import_requests table for persisting import work across process death
 * - Adds import_request_items table for per-image status tracking
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS import_requests (
                id TEXT NOT NULL PRIMARY KEY,
                status TEXT NOT NULL,
                imageCount INTEGER NOT NULL,
                completedCount INTEGER NOT NULL DEFAULT 0,
                failedCount INTEGER NOT NULL DEFAULT 0,
                stagingDir TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS import_request_items (
                id TEXT NOT NULL PRIMARY KEY,
                requestId TEXT NOT NULL,
                stagedFilePath TEXT NOT NULL,
                originalFileName TEXT NOT NULL,
                emojis TEXT NOT NULL,
                title TEXT,
                description TEXT,
                extractedText TEXT,
                status TEXT NOT NULL DEFAULT 'pending',
                errorMessage TEXT
            )""",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_import_request_items_requestId ON import_request_items (requestId)",
        )
    }
}

/**
 * Migration from version 3 to 4:
 * - Adds basedOn column to memes table for cultural source recognition
 * - Rebuilds FTS index to include basedOn
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memes ADD COLUMN basedOn TEXT DEFAULT NULL")
        db.execSQL("INSERT INTO memes_fts(memes_fts) VALUES('rebuild')")
    }
}
