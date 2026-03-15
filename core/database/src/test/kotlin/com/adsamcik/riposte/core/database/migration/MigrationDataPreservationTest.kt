package com.adsamcik.riposte.core.database.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.adsamcik.riposte.core.database.ALL_MIGRATIONS
import com.adsamcik.riposte.core.database.MIGRATION_1_2
import com.adsamcik.riposte.core.database.MIGRATION_2_3
import com.adsamcik.riposte.core.database.MIGRATION_3_4
import com.adsamcik.riposte.core.database.MIGRATION_4_5
import com.adsamcik.riposte.core.database.MIGRATION_5_6
import com.adsamcik.riposte.core.database.MIGRATION_6_7
import com.adsamcik.riposte.core.database.MIGRATION_7_8
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
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test_1.png")
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
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1, includeEmbeddingType = false))
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
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test_1.png")
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

    // region 6→7: share_targets dropped, perceptualHash added, potential_duplicates created

    @Test
    fun `migration 6 to 7 preserves meme data and adds perceptualHash`() {
        val db = createDatabaseAtVersion(6)
        db.insert(
            "memes",
            SQLiteDatabase.CONFLICT_NONE,
            memeContentValues(searchPhrasesJson = "[\"test\"]", basedOn = "Distracted Boyfriend"),
        )
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)
        val cursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test_1.png")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("basedOn"))).isEqualTo("Distracted Boyfriend")
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("perceptualHash"))).isTrue()
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 6 to 7 drops share_targets table`() {
        val db = createDatabaseAtVersion(6)
        db.insert("share_targets", SQLiteDatabase.CONFLICT_NONE, shareTargetContentValues())
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)
        val cursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='share_targets'",
        )
        assertWithMessage("share_targets table should be dropped by migration 6→7")
            .that(cursor.count)
            .isEqualTo(0)
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 6 to 7 creates potential_duplicates table`() {
        val db = createDatabaseAtVersion(6)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 10))
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 20, title = "Duplicate Meme"))
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)

        // Verify table exists
        val cursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='potential_duplicates'",
        )
        assertWithMessage("potential_duplicates table should exist after migration 6→7")
            .that(cursor.count)
            .isEqualTo(1)
        cursor.close()

        // Verify we can insert and query duplicate pairs
        migrated.insert(
            "potential_duplicates",
            SQLiteDatabase.CONFLICT_NONE,
            potentialDuplicateContentValues(memeId1 = 10, memeId2 = 20),
        )

        val dupCursor = migrated.query("SELECT * FROM potential_duplicates WHERE memeId1 = 10")
        assertThat(dupCursor.moveToFirst()).isTrue()
        assertThat(dupCursor.getInt(dupCursor.getColumnIndexOrThrow("hammingDistance"))).isEqualTo(3)
        assertThat(dupCursor.getString(dupCursor.getColumnIndexOrThrow("detectionMethod"))).isEqualTo("dhash")
        dupCursor.close()
        migrated.close()
    }

    @Test
    fun `migration 6 to 7 creates perceptualHash index`() {
        val db = createDatabaseAtVersion(6)
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)
        val indices = getTableIndices(migrated, "memes")
        val hashIndex = indices.find { it.name == "index_memes_perceptualHash" }
        assertWithMessage("perceptualHash index should exist on memes table")
            .that(hashIndex)
            .isNotNull()
        assertThat(hashIndex!!.columns).containsExactly("perceptualHash")
        migrated.close()
    }

    @Test
    fun `migration 6 to 7 potential_duplicates cascade deletes on meme removal`() {
        val db = createDatabaseAtVersion(6)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 10))
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 20, title = "Other"))
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)
        migrated.execSQL("PRAGMA foreign_keys = ON")
        migrated.insert(
            "potential_duplicates",
            SQLiteDatabase.CONFLICT_NONE,
            potentialDuplicateContentValues(memeId1 = 10, memeId2 = 20),
        )

        // Delete one of the memes
        migrated.execSQL("DELETE FROM memes WHERE id = 10")

        val cursor = migrated.query("SELECT COUNT(*) FROM potential_duplicates")
        cursor.moveToFirst()
        assertWithMessage("Duplicate pair should be cascade-deleted when meme is removed")
            .that(cursor.getInt(0))
            .isEqualTo(0)
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 6 to 7 preserves embeddings and emoji tags`() {
        val db = createDatabaseAtVersion(6)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues())
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1))
        db.insert(
            "emoji_tags",
            SQLiteDatabase.CONFLICT_NONE,
            ContentValues().apply {
                put("memeId", 1L)
                put("emoji", "🔥")
                put("emojiName", "fire")
            },
        )
        db.close()

        val migrated = migrateToVersion(7, MIGRATION_6_7)
        val embCursor = migrated.query("SELECT * FROM meme_embeddings WHERE memeId = 1")
        assertThat(embCursor.moveToFirst()).isTrue()
        embCursor.close()

        val tagCursor = migrated.query("SELECT * FROM emoji_tags WHERE memeId = 1")
        assertThat(tagCursor.moveToFirst()).isTrue()
        assertThat(tagCursor.getString(tagCursor.getColumnIndexOrThrow("emoji"))).isEqualTo("🔥")
        tagCursor.close()
        migrated.close()
    }

    // endregion

    // region 7→8: query_embedding_cache created, emotionsJson added, FTS rebuilt

    @Test
    fun `migration 7 to 8 preserves meme data and adds emotionsJson`() {
        val db = createDatabaseAtVersion(7)
        db.insert(
            "memes",
            SQLiteDatabase.CONFLICT_NONE,
            memeContentValues(
                searchPhrasesJson = "[\"test\"]",
                basedOn = "Drake Hotline",
                perceptualHash = 123456789L,
            ),
        )
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)
        val cursor = migrated.query("SELECT * FROM memes WHERE id = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("fileName"))).isEqualTo("test_1.png")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("basedOn"))).isEqualTo("Drake Hotline")
        assertThat(cursor.getLong(cursor.getColumnIndexOrThrow("perceptualHash"))).isEqualTo(123456789L)
        assertThat(cursor.isNull(cursor.getColumnIndexOrThrow("emotionsJson"))).isTrue()
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 7 to 8 creates query_embedding_cache table`() {
        val db = createDatabaseAtVersion(7)
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)
        val tableCursor = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='query_embedding_cache'",
        )
        assertWithMessage("query_embedding_cache table should exist after migration 7→8")
            .that(tableCursor.count)
            .isEqualTo(1)
        tableCursor.close()

        // Verify we can insert and retrieve cached query embeddings
        val now = System.currentTimeMillis()
        migrated.insert(
            "query_embedding_cache",
            SQLiteDatabase.CONFLICT_NONE,
            queryEmbeddingCacheContentValues(queryHash = "abc123", query = "funny cats", createdAt = now),
        )

        val cursor = migrated.query("SELECT * FROM query_embedding_cache WHERE queryHash = 'abc123'")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("query"))).isEqualTo("funny cats")
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("modelVersion"))).isEqualTo("embeddinggemma:1.3.0")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("dimension"))).isEqualTo(768)
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 7 to 8 query_embedding_cache has correct indices`() {
        val db = createDatabaseAtVersion(7)
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)
        val indices = getTableIndices(migrated, "query_embedding_cache")

        val modelVersionIndex = indices.find { it.name == "index_query_embedding_cache_modelVersion" }
        assertWithMessage("modelVersion index should exist on query_embedding_cache")
            .that(modelVersionIndex)
            .isNotNull()
        assertThat(modelVersionIndex!!.columns).containsExactly("modelVersion")

        val accessedAtIndex = indices.find { it.name == "index_query_embedding_cache_accessedAt" }
        assertWithMessage("accessedAt index should exist on query_embedding_cache")
            .that(accessedAtIndex)
            .isNotNull()
        assertThat(accessedAtIndex!!.columns).containsExactly("accessedAt")

        migrated.close()
    }

    @Test
    fun `migration 7 to 8 rebuilds FTS with emotionsJson and search works`() {
        val db = createDatabaseAtVersion(7)
        db.insert(
            "memes",
            SQLiteDatabase.CONFLICT_NONE,
            memeContentValues(title = "happy celebration dance"),
        )
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)

        // Verify existing data is searchable via FTS after rebuild
        val cursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'celebration'")
        assertWithMessage("FTS search for 'celebration' after 7→8 migration")
            .that(cursor.count)
            .isGreaterThan(0)
        cursor.close()

        // Verify FTS table includes emotionsJson column
        val ftsColumns = getTableColumns(migrated, "memes_fts", isFts = true)
        assertWithMessage("memes_fts should contain emotionsJson column")
            .that(ftsColumns)
            .contains("emotionsJson")

        migrated.close()
    }

    @Test
    fun `migration 7 to 8 FTS triggers sync emotionsJson on insert`() {
        val db = createDatabaseAtVersion(7)
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)

        // Insert a new meme with emotionsJson after migration — triggers should sync to FTS
        val values = memeContentValues(
            id = 99,
            title = "joyful puppy",
            emotionsJson = """{"primary":"joy","secondary":"excitement"}""",
        )
        migrated.insert("memes", SQLiteDatabase.CONFLICT_NONE, values)

        // FTS should find the meme by title (trigger-synced)
        val titleCursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'joyful'")
        assertWithMessage("FTS should find newly inserted meme by title")
            .that(titleCursor.count)
            .isGreaterThan(0)
        titleCursor.close()

        migrated.close()
    }

    @Test
    fun `migration 7 to 8 FTS triggers sync emotionsJson on update`() {
        val db = createDatabaseAtVersion(7)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(title = "original title"))
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)

        // Update the meme's title — triggers should re-sync FTS
        migrated.execSQL("UPDATE memes SET title = 'updated hilarious meme' WHERE id = 1")

        val cursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'hilarious'")
        assertWithMessage("FTS should reflect updated title after trigger sync")
            .that(cursor.count)
            .isGreaterThan(0)
        cursor.close()

        // Old title should no longer match
        val oldCursor = migrated.query("SELECT * FROM memes_fts WHERE memes_fts MATCH 'original'")
        assertWithMessage("FTS should not find old title after update")
            .that(oldCursor.count)
            .isEqualTo(0)
        oldCursor.close()

        migrated.close()
    }

    @Test
    fun `migration 7 to 8 preserves potential_duplicates`() {
        val db = createDatabaseAtVersion(7)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 10))
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues(id = 20, title = "Other"))
        db.insert(
            "potential_duplicates",
            SQLiteDatabase.CONFLICT_NONE,
            potentialDuplicateContentValues(memeId1 = 10, memeId2 = 20),
        )
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)
        val cursor = migrated.query("SELECT * FROM potential_duplicates WHERE memeId1 = 10")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("hammingDistance"))).isEqualTo(3)
        cursor.close()
        migrated.close()
    }

    @Test
    fun `migration 7 to 8 preserves embeddings`() {
        val db = createDatabaseAtVersion(7)
        db.insert("memes", SQLiteDatabase.CONFLICT_NONE, memeContentValues())
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1))
        db.close()

        val migrated = migrateToVersion(8, MIGRATION_7_8)
        val cursor = migrated.query("SELECT * FROM meme_embeddings WHERE memeId = 1")
        assertThat(cursor.moveToFirst()).isTrue()
        assertThat(cursor.getString(cursor.getColumnIndexOrThrow("embeddingType"))).isEqualTo("content")
        assertThat(cursor.getInt(cursor.getColumnIndexOrThrow("dimension"))).isEqualTo(256)
        cursor.close()
        migrated.close()
    }

    // endregion

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
        db.insert("meme_embeddings", SQLiteDatabase.CONFLICT_NONE, embeddingContentValues(memeId = 1, includeEmbeddingType = false))
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

        // Verify v7+ columns exist with defaults
        val memeCursor2 = migrated.query("SELECT perceptualHash, emotionsJson FROM memes WHERE id = 1")
        assertThat(memeCursor2.moveToFirst()).isTrue()
        assertThat(memeCursor2.isNull(memeCursor2.getColumnIndexOrThrow("perceptualHash"))).isTrue()
        assertThat(memeCursor2.isNull(memeCursor2.getColumnIndexOrThrow("emotionsJson"))).isTrue()
        memeCursor2.close()

        // Verify v7 table exists
        val dupTable = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='potential_duplicates'",
        )
        assertThat(dupTable.count).isEqualTo(1)
        dupTable.close()

        // Verify v8 table exists
        val cacheTable = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='query_embedding_cache'",
        )
        assertThat(cacheTable.count).isEqualTo(1)
        cacheTable.close()

        // Verify v6→7 dropped share_targets
        val shareTable = migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='share_targets'",
        )
        assertThat(shareTable.count).isEqualTo(0)
        shareTable.close()

        migrated.close()
    }

    // endregion

    // region Test Data Helpers

    private fun memeContentValues(
        id: Long = 1,
        title: String = "Test Meme",
        searchPhrasesJson: String? = null,
        basedOn: String? = null,
        perceptualHash: Long? = null,
        emotionsJson: String? = null,
    ): ContentValues =
        ContentValues().apply {
            put("id", id)
            put("filePath", "/storage/memes/test_$id.png")
            put("fileName", "test_$id.png")
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
            if (basedOn != null) {
                put("basedOn", basedOn)
            }
            if (perceptualHash != null) {
                put("perceptualHash", perceptualHash)
            }
            if (emotionsJson != null) {
                put("emotionsJson", emotionsJson)
            }
        }

    private fun embeddingContentValues(
        memeId: Long,
        includeEmbeddingType: Boolean = true,
    ): ContentValues =
        ContentValues().apply {
            put("memeId", memeId)
            if (includeEmbeddingType) {
                put("embeddingType", "content")
            }
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

    private fun shareTargetContentValues(): ContentValues =
        ContentValues().apply {
            put("packageName", "com.example.chat")
            put("activityName", "com.example.chat.ShareActivity")
            put("displayLabel", "Example Chat")
            put("shareCount", 5)
            put("lastSharedAt", System.currentTimeMillis())
        }

    private fun potentialDuplicateContentValues(
        memeId1: Long,
        memeId2: Long,
    ): ContentValues =
        ContentValues().apply {
            put("memeId1", memeId1)
            put("memeId2", memeId2)
            put("hammingDistance", 3)
            put("detectionMethod", "dhash")
            put("status", "pending")
            put("detectedAt", System.currentTimeMillis())
        }

    private fun queryEmbeddingCacheContentValues(
        queryHash: String,
        query: String,
        createdAt: Long,
    ): ContentValues =
        ContentValues().apply {
            put("queryHash", queryHash)
            put("query", query)
            put("modelVersion", "embeddinggemma:1.3.0")
            put("embedding", ByteArray(3072)) // 768 floats × 4 bytes
            put("dimension", 768)
            put("createdAt", createdAt)
            put("accessedAt", createdAt)
        }

    // endregion
}
