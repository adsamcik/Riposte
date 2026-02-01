package com.mememymood.core.database.migration

import android.content.ContentValues
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage

/**
 * Test fixtures for database migration testing.
 * 
 * Provides factory methods for creating consistent test data across
 * different schema versions and utilities for data verification.
 */
object MigrationTestFixtures {

    // region V1 Schema Constants
    
    const val GOLDEN_MEME_COUNT = 5
    
    // endregion

    // region V1 Meme Creation
    
    /**
     * Creates ContentValues for a meme entity at schema version 1.
     * Schema v1 has: id, filePath, fileName, mimeType, width, height,
     * fileSizeBytes, importedAt, emojiTagsJson, title, description,
     * textContent, embedding, isFavorite, createdAt, useCount
     */
    fun createMemeV1(
        id: Long = 0,
        filePath: String = "/storage/memes/test.png",
        fileName: String = "test.png",
        mimeType: String = "image/png",
        width: Int = 1024,
        height: Int = 768,
        fileSizeBytes: Long = 102400,
        importedAt: Long = System.currentTimeMillis(),
        emojiTagsJson: String = "[]",
        title: String? = null,
        description: String? = null,
        textContent: String? = null,
        embedding: ByteArray? = null,
        isFavorite: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
        useCount: Int = 0,
    ): ContentValues = ContentValues().apply {
        // Don't put id=0, let SQLite auto-generate
        if (id > 0) put("id", id)
        put("filePath", filePath)
        put("fileName", fileName)
        put("mimeType", mimeType)
        put("width", width)
        put("height", height)
        put("fileSizeBytes", fileSizeBytes)
        put("importedAt", importedAt)
        put("emojiTagsJson", emojiTagsJson)
        if (title != null) put("title", title) else putNull("title")
        if (description != null) put("description", description) else putNull("description")
        if (textContent != null) put("textContent", textContent) else putNull("textContent")
        if (embedding != null) put("embedding", embedding) else putNull("embedding")
        put("isFavorite", if (isFavorite) 1 else 0)
        put("createdAt", createdAt)
        put("useCount", useCount)
    }

    /**
     * Inserts a set of golden test memes into a v1 database.
     * These memes cover various edge cases:
     * - Meme with all fields populated
     * - Meme with null optional fields
     * - Meme with embedding but no textContent
     * - Meme with high useCount
     * - Meme marked as favorite
     */
    fun insertGoldenMemesV1(db: SupportSQLiteDatabase): List<Long> {
        val insertedIds = mutableListOf<Long>()
        
        // Meme 1: All fields populated
        insertedIds += db.insert(
            "memes",
            0,
            createMemeV1(
                filePath = "/storage/memes/complete.png",
                fileName = "complete.png",
                mimeType = "image/png",
                width = 1920,
                height = 1080,
                fileSizeBytes = 500000,
                importedAt = 1700000000000,
                emojiTagsJson = """["😂","🔥","💀"]""",
                title = "Complete Test Meme",
                description = "A meme with all fields populated for testing",
                textContent = "When you realize it's Monday again",
                embedding = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05),
                isFavorite = true,
                createdAt = 1700000000000,
                useCount = 25,
            )
        )

        // Meme 2: Minimal fields (null optionals)
        insertedIds += db.insert(
            "memes",
            0,
            createMemeV1(
                filePath = "/storage/memes/minimal.jpg",
                fileName = "minimal.jpg",
                mimeType = "image/jpeg",
                width = 800,
                height = 600,
                fileSizeBytes = 50000,
                importedAt = 1700001000000,
                emojiTagsJson = "[]",
                title = null,
                description = null,
                textContent = null,
                embedding = null,
                isFavorite = false,
                createdAt = 1700001000000,
                useCount = 0,
            )
        )

        // Meme 3: Has embedding but no textContent
        insertedIds += db.insert(
            "memes",
            0,
            createMemeV1(
                filePath = "/storage/memes/embedded.gif",
                fileName = "embedded.gif",
                mimeType = "image/gif",
                width = 500,
                height = 500,
                fileSizeBytes = 200000,
                importedAt = 1700002000000,
                emojiTagsJson = """["🎉"]""",
                title = "Embedded Meme",
                description = null,
                textContent = null,
                embedding = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()),
                isFavorite = false,
                createdAt = 1700002000000,
                useCount = 10,
            )
        )

        // Meme 4: High useCount (popular meme)
        insertedIds += db.insert(
            "memes",
            0,
            createMemeV1(
                filePath = "/storage/memes/popular.png",
                fileName = "popular.png",
                mimeType = "image/png",
                width = 1200,
                height = 900,
                fileSizeBytes = 300000,
                importedAt = 1699900000000,
                emojiTagsJson = """["😎","🚀"]""",
                title = "Popular Meme",
                description = "This meme has been shared many times",
                textContent = "Stonks only go up",
                embedding = null,
                isFavorite = true,
                createdAt = 1699900000000,
                useCount = 100,
            )
        )

        // Meme 5: Empty emoji tags
        insertedIds += db.insert(
            "memes",
            0,
            createMemeV1(
                filePath = "/storage/memes/untagged.webp",
                fileName = "untagged.webp",
                mimeType = "image/webp",
                width = 640,
                height = 480,
                fileSizeBytes = 75000,
                importedAt = 1700003000000,
                emojiTagsJson = "[]",
                title = null,
                description = null,
                textContent = null,
                embedding = null,
                isFavorite = false,
                createdAt = 1700003000000,
                useCount = 2,
            )
        )

        return insertedIds
    }

    // endregion

    // region V4 Schema Additions (primaryLanguage, localizationsJson)

    /**
     * Updates memes with multilingual data for testing v3→v4 migration.
     * Call this AFTER migrating to v4 to verify new columns work correctly.
     */
    fun updateMemesWithMultilingualDataV4(db: SupportSQLiteDatabase, memeId: Long) {
        db.execSQL(
            """
            UPDATE memes SET 
                primaryLanguage = 'en',
                localizationsJson = '{"cs":{"title":"Testovací meme","description":"Popis v češtině"}}'
            WHERE id = ?
            """.trimIndent(),
            arrayOf(memeId)
        )
    }

    // endregion

    // region V5 Schema Additions (meme_embeddings table extensions)

    /**
     * Creates ContentValues for a meme_embeddings entity at schema version 2+.
     */
    fun createEmbeddingV2(
        memeId: Long,
        embedding: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
        dimension: Int = 128,
        modelVersion: String = "test:1.0.0",
        generatedAt: Long = System.currentTimeMillis(),
        sourceTextHash: String? = null,
        needsRegeneration: Boolean = false,
    ): ContentValues = ContentValues().apply {
        put("memeId", memeId)
        put("embedding", embedding)
        put("dimension", dimension)
        put("modelVersion", modelVersion)
        put("generatedAt", generatedAt)
        if (sourceTextHash != null) put("sourceTextHash", sourceTextHash) else putNull("sourceTextHash")
        put("needsRegeneration", if (needsRegeneration) 1 else 0)
    }

    /**
     * Creates ContentValues for a meme_embeddings entity at schema version 5+.
     * Includes indexingAttempts and lastAttemptAt columns.
     */
    fun createEmbeddingV5(
        memeId: Long,
        embedding: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
        dimension: Int = 128,
        modelVersion: String = "test:1.0.0",
        generatedAt: Long = System.currentTimeMillis(),
        sourceTextHash: String? = null,
        needsRegeneration: Boolean = false,
        indexingAttempts: Int = 0,
        lastAttemptAt: Long? = null,
    ): ContentValues = ContentValues().apply {
        put("memeId", memeId)
        put("embedding", embedding)
        put("dimension", dimension)
        put("modelVersion", modelVersion)
        put("generatedAt", generatedAt)
        if (sourceTextHash != null) put("sourceTextHash", sourceTextHash) else putNull("sourceTextHash")
        put("needsRegeneration", if (needsRegeneration) 1 else 0)
        put("indexingAttempts", indexingAttempts)
        if (lastAttemptAt != null) put("lastAttemptAt", lastAttemptAt) else putNull("lastAttemptAt")
    }

    // endregion
}

/**
 * Utility class for verifying data integrity after migrations.
 */
class DataVerifier(private val db: SupportSQLiteDatabase) {

    /**
     * Extracts all meme records from the database as a list of maps.
     * Column names are preserved for comparison.
     */
    fun extractAllMemes(): List<Map<String, Any?>> {
        return db.query("SELECT * FROM memes ORDER BY id").use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map { cursorToMap(it) }
                .toList()
        }
    }

    /**
     * Extracts all meme_embeddings records from the database.
     */
    fun extractAllEmbeddings(): List<Map<String, Any?>> {
        return db.query("SELECT * FROM meme_embeddings ORDER BY id").use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map { cursorToMap(it) }
                .toList()
        }
    }

    /**
     * Verifies that core meme data is preserved across migrations.
     * Ignores columns that are expected to change or be added.
     */
    fun assertMemesPreserved(
        before: List<Map<String, Any?>>,
        after: List<Map<String, Any?>>,
        ignoredColumns: Set<String> = emptySet(),
    ) {
        assertThat(after.size).isEqualTo(before.size)
        
        before.zip(after).forEachIndexed { index, (beforeMeme, afterMeme) ->
            val beforeFiltered = beforeMeme.filterKeys { it !in ignoredColumns }
            val afterFiltered = afterMeme.filterKeys { it !in ignoredColumns }
            
            // Check each column individually for better error messages
            beforeFiltered.forEach { (key, expectedValue) ->
                val actualValue = afterFiltered[key]
                if (expectedValue is ByteArray && actualValue is ByteArray) {
                    assertWithMessage("Meme[$index].$key")
                        .that(actualValue.contentEquals(expectedValue))
                        .isTrue()
                } else {
                    assertWithMessage("Meme[$index].$key")
                        .that(actualValue)
                        .isEqualTo(expectedValue)
                }
            }
        }
    }

    /**
     * Gets the count of rows in a table.
     */
    fun getRowCount(tableName: String): Int {
        return db.query("SELECT COUNT(*) FROM $tableName").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
    }

    /**
     * Checks if a table exists in the database.
     */
    fun tableExists(tableName: String): Boolean {
        return db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(tableName)
        ).use { cursor ->
            cursor.count > 0
        }
    }

    /**
     * Checks if an index exists in the database.
     */
    fun indexExists(indexName: String): Boolean {
        return db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(indexName)
        ).use { cursor ->
            cursor.count > 0
        }
    }

    /**
     * Gets all column names for a table.
     */
    fun getColumnNames(tableName: String): Set<String> {
        return db.query("PRAGMA table_info($tableName)").use { cursor ->
            generateSequence { cursor.takeIf { it.moveToNext() } }
                .map { it.getString(it.getColumnIndexOrThrow("name")) }
                .toSet()
        }
    }

    /**
     * Verifies that a column has the expected default value for new rows.
     */
    fun verifyColumnDefault(
        tableName: String,
        columnName: String,
        expectedDefault: Any?,
    ) {
        db.query("PRAGMA table_info($tableName)").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == columnName) {
                    val defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    when (expectedDefault) {
                        null -> assertThat(defaultValue).isNull()
                        is Int, is Long -> assertThat(defaultValue).isEqualTo(expectedDefault.toString())
                        is String -> assertThat(defaultValue).isEqualTo("'$expectedDefault'")
                        else -> assertThat(defaultValue).isEqualTo(expectedDefault.toString())
                    }
                    return
                }
            }
            throw AssertionError("Column $columnName not found in table $tableName")
        }
    }

    private fun cursorToMap(cursor: Cursor): Map<String, Any?> {
        return buildMap {
            for (i in 0 until cursor.columnCount) {
                val columnName = cursor.getColumnName(i)
                val value: Any? = when (cursor.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> null
                    Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                    Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(i)
                    else -> null
                }
                put(columnName, value)
            }
        }
    }
}
