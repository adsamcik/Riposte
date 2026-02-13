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
val MIGRATION_1_2 =
    object : Migration(1, 2) {
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
            )""",
            )
            db.execSQL(
                """INSERT INTO meme_embeddings_new 
                (id, memeId, embeddingType, embedding, dimension, modelVersion, 
                 generatedAt, sourceTextHash, needsRegeneration, indexingAttempts, lastAttemptAt)
               SELECT id, memeId, 'content', embedding, dimension, modelVersion,
                      generatedAt, sourceTextHash, needsRegeneration, indexingAttempts, lastAttemptAt
               FROM meme_embeddings""",
            )
            db.execSQL("DROP TABLE meme_embeddings")
            db.execSQL("ALTER TABLE meme_embeddings_new RENAME TO meme_embeddings")
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS " +
                    "index_meme_embeddings_memeId_embeddingType ON meme_embeddings (memeId, embeddingType)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_memeId ON meme_embeddings (memeId)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_meme_embeddings_modelVersion ON meme_embeddings (modelVersion)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_meme_embeddings_generatedAt ON meme_embeddings (generatedAt)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS " +
                    "index_meme_embeddings_needsRegeneration ON meme_embeddings (needsRegeneration)",
            )

            // 3. Recreate FTS table with searchPhrasesJson column (FTS4 schema is fixed at creation)
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_BEFORE_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_BEFORE_DELETE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_AFTER_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_AFTER_INSERT")
            db.execSQL("DROP TABLE IF EXISTS memes_fts")

            db.execSQL(
                """CREATE VIRTUAL TABLE IF NOT EXISTS `memes_fts` USING FTS4(
                `fileName` TEXT NOT NULL,
                `emojiTagsJson` TEXT NOT NULL,
                `title` TEXT,
                `description` TEXT,
                `textContent` TEXT,
                `searchPhrasesJson` TEXT,
                content=`memes`
            )""",
            )

            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_BEFORE_UPDATE
               BEFORE UPDATE ON `memes`
               BEGIN DELETE FROM `memes_fts` WHERE `docid`=OLD.`rowid`; END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_BEFORE_DELETE
               BEFORE DELETE ON `memes`
               BEGIN DELETE FROM `memes_fts` WHERE `docid`=OLD.`rowid`; END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_AFTER_UPDATE
               AFTER UPDATE ON `memes`
               BEGIN INSERT INTO `memes_fts`(`docid`, `fileName`,
               `emojiTagsJson`, `title`, `description`, `textContent`,
               `searchPhrasesJson`)
               VALUES (NEW.`rowid`, NEW.`fileName`,
               NEW.`emojiTagsJson`, NEW.`title`, NEW.`description`,
               NEW.`textContent`, NEW.`searchPhrasesJson`); END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_AFTER_INSERT
               AFTER INSERT ON `memes`
               BEGIN INSERT INTO `memes_fts`(`docid`, `fileName`,
               `emojiTagsJson`, `title`, `description`, `textContent`,
               `searchPhrasesJson`)
               VALUES (NEW.`rowid`, NEW.`fileName`,
               NEW.`emojiTagsJson`, NEW.`title`, NEW.`description`,
               NEW.`textContent`, NEW.`searchPhrasesJson`); END""",
            )

            // 4. Rebuild FTS index with existing data
            db.execSQL("INSERT INTO memes_fts(memes_fts) VALUES('rebuild')")
        }
    }

/**
 * Migration from version 2 to 3:
 * - Adds import_requests table for persisting import work across process death
 * - Adds import_request_items table for per-image status tracking
 */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
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
 * - Recreates FTS virtual table with basedOn column (FTS4 tables cannot be altered)
 * - Recreates content sync triggers to include basedOn
 */
val MIGRATION_3_4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Add basedOn to memes table
            db.execSQL("ALTER TABLE memes ADD COLUMN basedOn TEXT DEFAULT NULL")

            // 2. Drop old FTS table and sync triggers (FTS4 schema is fixed at creation)
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_BEFORE_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_BEFORE_DELETE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_AFTER_UPDATE")
            db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_memes_fts_AFTER_INSERT")
            db.execSQL("DROP TABLE IF EXISTS memes_fts")

            // 3. Recreate FTS table with basedOn column
            db.execSQL(
                """CREATE VIRTUAL TABLE IF NOT EXISTS `memes_fts` USING FTS4(
                `fileName` TEXT NOT NULL,
                `emojiTagsJson` TEXT NOT NULL,
                `title` TEXT,
                `description` TEXT,
                `textContent` TEXT,
                `searchPhrasesJson` TEXT,
                `basedOn` TEXT,
                content=`memes`
            )""",
            )

            // 4. Recreate content sync triggers
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_BEFORE_UPDATE
               BEFORE UPDATE ON `memes`
               BEGIN DELETE FROM `memes_fts` WHERE `docid`=OLD.`rowid`; END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_BEFORE_DELETE
               BEFORE DELETE ON `memes`
               BEGIN DELETE FROM `memes_fts` WHERE `docid`=OLD.`rowid`; END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_AFTER_UPDATE
               AFTER UPDATE ON `memes`
               BEGIN INSERT INTO `memes_fts`(`docid`, `fileName`,
               `emojiTagsJson`, `title`, `description`, `textContent`,
               `searchPhrasesJson`, `basedOn`)
               VALUES (NEW.`rowid`, NEW.`fileName`,
               NEW.`emojiTagsJson`, NEW.`title`, NEW.`description`,
               NEW.`textContent`, NEW.`searchPhrasesJson`,
               NEW.`basedOn`); END""",
            )
            db.execSQL(
                """CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_memes_fts_AFTER_INSERT
               AFTER INSERT ON `memes`
               BEGIN INSERT INTO `memes_fts`(`docid`, `fileName`,
               `emojiTagsJson`, `title`, `description`, `textContent`,
               `searchPhrasesJson`, `basedOn`)
               VALUES (NEW.`rowid`, NEW.`fileName`,
               NEW.`emojiTagsJson`, NEW.`title`, NEW.`description`,
               NEW.`textContent`, NEW.`searchPhrasesJson`,
               NEW.`basedOn`); END""",
            )

            // 5. Rebuild FTS index with existing data
            db.execSQL("INSERT INTO memes_fts(memes_fts) VALUES('rebuild')")
        }
    }

/**
 * Migration from version 4 to 5:
 * - Adds metadataJson column to import_request_items for preserving full metadata through staging
 */
@Suppress("MagicNumber")
val MIGRATION_4_5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE import_request_items ADD COLUMN metadataJson TEXT DEFAULT NULL",
            )
        }
    }

/**
 * Migration from version 5 to 6:
 * - Recreates import_request_items table with a foreign key constraint
 *   referencing import_requests(id) ON DELETE CASCADE
 */
@Suppress("MagicNumber")
val MIGRATION_5_6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS import_request_items_new (
                id TEXT NOT NULL PRIMARY KEY,
                requestId TEXT NOT NULL,
                stagedFilePath TEXT NOT NULL,
                originalFileName TEXT NOT NULL,
                emojis TEXT NOT NULL,
                title TEXT,
                description TEXT,
                extractedText TEXT,
                status TEXT NOT NULL DEFAULT 'pending',
                errorMessage TEXT,
                metadataJson TEXT,
                FOREIGN KEY(requestId) REFERENCES import_requests(id) ON DELETE CASCADE
            )""",
            )
            db.execSQL(
                "INSERT INTO import_request_items_new SELECT * FROM import_request_items",
            )
            db.execSQL("DROP TABLE import_request_items")
            db.execSQL("ALTER TABLE import_request_items_new RENAME TO import_request_items")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_import_request_items_requestId ON import_request_items (requestId)",
            )
        }
    }

/**
 * All migrations in order. Used by [DatabaseModule] and migration tests
 * to ensure the full chain is registered and validated.
 */
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
