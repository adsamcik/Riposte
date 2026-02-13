package com.adsamcik.riposte.core.database.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.adsamcik.riposte.core.database.ALL_MIGRATIONS
import com.adsamcik.riposte.core.database.MIGRATION_1_2
import com.adsamcik.riposte.core.database.MIGRATION_2_3
import com.adsamcik.riposte.core.database.MIGRATION_3_4
import com.adsamcik.riposte.core.database.MIGRATION_4_5
import com.adsamcik.riposte.core.database.MIGRATION_5_6
import com.adsamcik.riposte.core.database.MemeDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that data survives each migration intact.
 * Inserts representative rows at version N, migrates to N+1,
 * and verifies the data is preserved (including new columns,
 * FK cascades, and FTS queryability).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MigrationDataPreservationTest : MigrationTestBase() {

    // region 1→2: memes + embeddings survive, new columns have defaults

    @Test
    fun `migration 1 to 2 preserves meme data`() {
        val db = createDatabaseAtVersion(1)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues())
        db.close()

        val migrated = migrateToVersion(2, MIGRATION_1_2)
        val cursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test.png")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("title"))).isEqualTo("Test Meme")
        // New column should be null
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("searchPhrasesJson"))).isTrue()
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 1 to 2 preserves embedding data with new embeddingType`() {
        val db = createDatabaseAtVersion(1)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues())
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1))
        db.close()

        val migrated = migrateToVersion(2, MIGRATION_1_2)
        val cursor = migrated.query("SELECT * FROM meme_embeddings WHERE memeId = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("embeddingType"))).isEqualTo("content")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("dimension"))).isEqualTo(256)
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 1 to 2 rebuilds FTS and search works`() {
        val db = createDatabaseAtVersion(1)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(title = "funny cat meme"))
        db.close()

        val migrated = migrateToVersion(2, MIGRATION_1_2)
        val cursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'funny'")
        assertWithMessage("FTS search for 'funny' after 1→2 migration")
            .that(cursor.count)
            .isGreaterThan(0)
        cursor.close()
        migrated.close()
    }

    // endregion

    // region 2→3: existing data preserved, new import tables created

    @Test
    fun `migration 2 to 3 preserves meme data`() {
        val db = createDatabaseAtVersion(2)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(searchPhrasesJson = "[\"funny cat\"]"))
        db.close()

        val migrated = migrateToVersion(3, MIGRATION_2_3)
        val cursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("searchPhrasesJson"))).isEqualTo("[\"funny cat\"]")
        cursor.close()

        // New tables should exist and be empty
        val importCursor = migrated.query("SELECT COUNT(*) FROM import_requests")
        importCursor.moveToFirst()
        assertThat(importCursor.getInt(0)).isEqualTo(0)
        importCursor.close()
        migrated.close()
    }

    // endregion

    // region 3→4: basedOn column added, FTS rebuilt with it

    @Test
    fun `migration 3 to 4 preserves meme data and adds basedOn`() {
        val db = createDatabaseAtVersion(3)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(searchPhrasesJson = "[\"test\"]"))
        db.close()

        val migrated = migrateToVersion(4, MIGRATION_3_4)
        val cursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test.png")
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("basedOn"))).isTrue()
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 3 to 4 rebuilds FTS and search still works`() {
        val db = createDatabaseAtVersion(3)
        db.insert(
            "memes",
            SQLiteDatabase.CONFLICT_NONE,
            memeContentValues(title = "drake hotline meme", searchPhrasesJson = "[\"drake\"]"),
        )
        db.close()

        val migrated = migrateToVersion(4, MIGRATION_3_4)
        val cursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'drake'")
        assertWithMessage("FTS search for 'drake' after 3→4 migration")
            .that(cursor.count)
            .isGreaterThan(0)
        cursor.close()
        migrated.close()
    }

    // endregion

    // region 4→5: import_request_items gets metadataJson column

    @Test
    fun `migration 4 to 5 preserves import request items`() {
        val db = createDatabaseAtVersion(4)
        db.insert("import_requests", SQLiteDatabase.CONFLICT_NONE, importRequestContentValues())
        db.insert("import_request_items", SQLiteDatabase.CONFLICT_NONE, importRequestItemContentValues())
        db.close()

        val migrated = migrateToVersion(5, MIGRATION_4_5)
        val cursor = migrated.query("SELECT * FROM import_request_items WHERE id = 'item-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("originalFileName"))).isEqualTo("cat.png")
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("metadataJson"))).isTrue()
        cursor.close()
        migrated.close()
    }

    // endregion

    // region 5→6: import_request_items gets FK CASCADE, data preserved

    @Test
    fun `migration 5 to 6 preserves import request items`() {
        val db = createDatabaseAtVersion(5)
        db.insert("import_requests", SQLiteDatabase.CONFLICT_NONE, importRequestContentValues())
        db.insert(
            "import_request_items",
            SQLiteDatabase.CONFLICT_NONE,
            importRequestItemContentValues(metadataJson = "{\"schema\":\"1.3\"}"),
        )
        db.close()

        val migrated = migrateToVersion(6, MIGRATION_5_6)
        val cursor = migrated.query("SELECT * FROM import_request_items WHERE id = 'item-1'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("metadataJson"))).isEqualTo("{\"schema\":\"1.3\"}")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("requestId"))).isEqualTo("req-1")
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 5 to 6 enables FK cascade delete`() {
        val db = createDatabaseAtVersion(5)
        db.insert("import_requests", SQLiteDatabase.CONFLICT_NONE, importRequestContentValues())
        db.insert("import_request_items", SQLiteDatabase.CONFLICT_NONE, importRequestItemContentValues())
        db.close()

        val migrated = migrateToVersion(6, MIGRATION_5_6)
        // Enable FK enforcement (SQLite requires this per-connection)
        migrated.execSQL("PRAGMA foreign_keys = ON")

        // Delete the parent request
        migrated.execSQL("DELETE FROM import_requests WHERE id = 'req-1'")

        // Child items should be cascade-deleted
        val cursor = migrated.query("SELECT COUNT(*) FROM import_request_items WHERE requestId = 'req-1'")
        cursor.moveToFirst()
        assertWithMessage("Import items should be cascade-deleted when parent request is deleted")
            .that(cursor.getInt(0))
            .isEqualTo(0)
        cursor.close()
        migrated.close()
    }

    // endregion

    // region Full chain: v1 data survives migration to latest

    @Test
    fun `data inserted at version 1 survives migration to latest`() {
        val db = createDatabaseAtVersion(1)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(title = "Survivor Meme"))
        db.insert(
            "emoji_tags",
            SQLiteDatabase.CONFLICT_NONE,
            ContentValues().apply {
                put("memeId", 1L)
                put("emoji", "😂")
                put("emojiName", "face_with_tears_of_joy")
            },
        )
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1))
        db.close()

        val migrated = migrateToVersion(MemeDatabase.LATEST_VERSION, *ALL_MIGRATIONS)

        // Verify meme survived
        val memeCursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(memeCursor.moveToFirst()).isTrue()
        assertThat(memeCursor.getString(memeCursor.getColumnIndexOrThrow("title"))).isEqualTo("Survivor Meme")
        memeCursor.close()

        // Verify emoji tag survived
        val tagCursor = migrated.query("SELECT * FROM emoji_tags WHERE memeId = 1")
        assertThat(tagCursor.moveToFirst()).isTrue()
        assertThat(tagCursor.getString(tagCursor.getColumnIndexOrThrow("emoji"))).isEqualTo("😂")
        tagCursor.close()

        // Verify embedding survived with embeddingType
        val embCursor = migrated.query("SELECT * FROM meme_embeddings WHERE memeId = 1")
        assertThat(embCursor.moveToFirst()).isTrue()
        assertThat(embCursor.getString(embCursor.getColumnIndexOrThrow("embeddingType"))).isEqualTo("content")
        embCursor.close()

        // Verify FTS is queryable
        val ftsCursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'Survivor'")
        assertWithMessage("FTS search for 'Survivor' after full migration chain")
            .that(ftsCursor.count)
            .isGreaterThan(0)
        ftsCursor.close()

        migrated.close()
    }

    // endregion

    // region Test Data Helpers

    private fun memeContentValues(
        id: Long = 1,
        title: String = "Test Meme",
        searchPhrasesJson: String? = null,
    ): ContentValues =
        ContentValues().apply {
            put("id", id)
            put("filePath", "/storage/memes/test.png")
            put("fileName", "test.png")
            put("mimeType", "image/png")
            put("width", 1024)
            put("height", 768)
            put("fileSizeBytes", 102400L)
            put("importedAt", System.currentTimeMillis())
            put("emojiTagsJson", "[\"😂\"]")
            put("title", title)
            put("description", "A test meme")
            put("isFavorite", 0)
            put("createdAt", System.currentTimeMillis())
            put("useCount", 0)
            put("viewCount", 0)
            if (searchPhrasesJson != null) {
                put("searchPhrasesJson", searchPhrasesJson)
            }
        }

    private fun embeddingContentValues(memeId: Long): ContentValues =
        ContentValues().apply {
            put("memeId", memeId)
            put("embedding", ByteArray(1024))
            put("dimension", 256)
            put("modelVersion", "v1.0")
            put("generatedAt", System.currentTimeMillis())
            put("needsRegeneration", 0)
            put("indexingAttempts", 0)
        }

    private fun importRequestContentValues(): ContentValues =
        ContentValues().apply {
            put("id", "req-1")
            put("status", "pending")
            put("imageCount", 1)
            put("completedCount", 0)
            put("failedCount", 0)
            put("stagingDir", "/data/staging/req-1")
            put("createdAt", System.currentTimeMillis())
            put("updatedAt", System.currentTimeMillis())
        }

    private fun importRequestItemContentValues(metadataJson: String? = null): ContentValues =
        ContentValues().apply {
            put("id", "item-1")
            put("requestId", "req-1")
            put("stagedFilePath", "/data/staging/req-1/cat.png")
            put("originalFileName", "cat.png")
            put("emojis", "[\"😺\"]")
            put("title", "Cat meme")
            put("status", "pending")
            if (metadataJson != null) {
                put("metadataJson", metadataJson)
            }
        }

    // endregion
}
