package com.mememymood.core.database.migration

import android.content.ContentValues
import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.mememymood.core.database.MemeDatabase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Edge case tests for database migrations.
 * 
 * These tests cover boundary conditions and special scenarios that
 * could potentially cause migration failures or data corruption:
 * - Empty databases
 * - Maximum field values
 * - Unicode and special characters
 * - Null vs empty values
 * - Large datasets
 */
@RunWith(AndroidJUnit4::class)
class MigrationEdgeCaseTest {

    private val testDbName = "migration-edge-case-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MemeDatabase::class.java
    )

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun teardown() {
        context.deleteDatabase(testDbName)
    }

    // region Empty Database Edge Cases

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 works on empty database`() {
        // Create empty v1 database
        helper.createDatabase(testDbName, 1).close()

        // Should not throw
        val db = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        val verifier = DataVerifier(db)
        assertThat(verifier.getRowCount("memes")).isEqualTo(0)
        assertThat(verifier.getRowCount("meme_embeddings")).isEqualTo(0)
    }

    @Test
    @Throws(IOException::class)
    fun `full migration works on empty database`() {
        helper.createDatabase(testDbName, 1).close()

        // Run all migrations
        val db = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        val verifier = DataVerifier(db)
        assertThat(verifier.getRowCount("memes")).isEqualTo(0)
    }

    // endregion

    // region Null Values Edge Cases

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 handles memes with null embeddings`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Insert meme with null embedding
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/null_embedding.png",
            embedding = null
        ))
        db1.close()

        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        // Should not have created an entry in meme_embeddings
        val verifier = DataVerifier(db2)
        assertThat(verifier.getRowCount("meme_embeddings")).isEqualTo(0)
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 handles memes with empty embedding byte array`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Insert meme with empty (but non-null) embedding
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/empty_embedding.png",
            embedding = byteArrayOf() // Empty but not null
        ))
        db1.close()

        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        // The migration should handle this - empty embeddings are technically non-null
        val verifier = DataVerifier(db2)
        // Verify no crash occurred and embedding was migrated
        assertThat(verifier.getRowCount("meme_embeddings")).isEqualTo(1)
    }

    // endregion

    // region Unicode and Special Characters Edge Cases

    @Test
    @Throws(IOException::class)
    fun `migration preserves Unicode characters in text fields`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Insert meme with various Unicode characters
        val unicodeTitle = "日本語タイトル 🎌 Émojis: 👍🏻👍🏿"
        val unicodeDescription = "Описание на русском • العربية • 中文"
        val unicodeTags = """["🇯🇵","🇷🇺","🇨🇳","🇸🇦"]"""
        
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/unicode.png",
            title = unicodeTitle,
            description = unicodeDescription,
            emojiTagsJson = unicodeTags
        ))
        db1.close()

        // Run all migrations
        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        // Verify Unicode is preserved
        db6.query("SELECT title, description, emojiTagsJson FROM memes").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo(unicodeTitle)
            assertThat(cursor.getString(1)).isEqualTo(unicodeDescription)
            assertThat(cursor.getString(2)).isEqualTo(unicodeTags)
        }
    }

    @Test
    @Throws(IOException::class)
    fun `migration handles special SQL characters in strings`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Strings with SQL-dangerous characters
        val dangerousTitle = "Test's \"meme\" with; DROP TABLE--"
        val dangerousPath = "/storage/meme's folder/test\"file.png"
        
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = dangerousPath,
            title = dangerousTitle
        ))
        db1.close()

        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        db6.query("SELECT title, filePath FROM memes").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo(dangerousTitle)
            assertThat(cursor.getString(1)).isEqualTo(dangerousPath)
        }
    }

    // endregion

    // region Large Values Edge Cases

    @Test
    @Throws(IOException::class)
    fun `migration handles large embedding arrays`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Create a large embedding (e.g., 512 dimensions * 4 bytes = 2KB)
        val largeEmbedding = ByteArray(2048) { (it % 256).toByte() }
        
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/large_embedding.png",
            embedding = largeEmbedding
        ))
        db1.close()

        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        // Verify embedding was migrated correctly
        db2.query("SELECT embedding FROM meme_embeddings").use { cursor ->
            cursor.moveToFirst()
            val migratedEmbedding = cursor.getBlob(0)
            assertThat(migratedEmbedding).isEqualTo(largeEmbedding)
        }
    }

    @Test
    @Throws(IOException::class)
    fun `migration handles very long text fields`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Create a very long description (10KB of text)
        val longDescription = "A".repeat(10240)
        val longTextContent = "B".repeat(10240)
        
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/long_text.png",
            description = longDescription,
            textContent = longTextContent
        ))
        db1.close()

        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        db6.query("SELECT description, textContent FROM memes").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).hasLength(10240)
            assertThat(cursor.getString(1)).hasLength(10240)
        }
    }

    @Test
    @Throws(IOException::class)
    fun `migration handles maximum integer values`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/max_values.png",
            width = Int.MAX_VALUE,
            height = Int.MAX_VALUE,
            fileSizeBytes = Long.MAX_VALUE,
            useCount = Int.MAX_VALUE
        ))
        db1.close()

        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        db6.query("SELECT width, height, fileSizeBytes, useCount FROM memes").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(Int.MAX_VALUE)
            assertThat(cursor.getInt(1)).isEqualTo(Int.MAX_VALUE)
            assertThat(cursor.getLong(2)).isEqualTo(Long.MAX_VALUE)
            assertThat(cursor.getInt(3)).isEqualTo(Int.MAX_VALUE)
        }
    }

    // endregion

    // region Multiple Records Edge Cases

    @Test
    @Throws(IOException::class)
    fun `migration handles many records efficiently`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Insert 100 memes with varying data
        repeat(100) { index ->
            db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
                filePath = "/test/meme_$index.png",
                fileName = "meme_$index.png",
                title = "Meme number $index",
                embedding = if (index % 2 == 0) byteArrayOf(index.toByte()) else null,
                isFavorite = index % 5 == 0,
                useCount = index * 10
            ))
        }
        db1.close()

        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        val verifier = DataVerifier(db6)
        assertThat(verifier.getRowCount("memes")).isEqualTo(100)
        // 50 memes had embeddings (even indices)
        assertThat(verifier.getRowCount("meme_embeddings")).isEqualTo(50)
    }

    // endregion

    // region Duplicate Detection Edge Cases (v2→v3 adds unique index)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_2_3 unique filePath index blocks duplicates`() {
        // Create v1 and migrate to v2
        val db1 = helper.createDatabase(testDbName, 1)
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/unique.png"
        ))
        db1.close()
        
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()

        val db3 = helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        )

        // Try to insert duplicate filePath - should fail
        val result = try {
            db3.insert("memes", 0, ContentValues().apply {
                put("filePath", "/test/unique.png")
                put("fileName", "duplicate.png")
                put("mimeType", "image/png")
                put("width", 100)
                put("height", 100)
                put("fileSizeBytes", 1000)
                put("importedAt", System.currentTimeMillis())
                put("emojiTagsJson", "[]")
                put("isFavorite", 0)
                put("createdAt", System.currentTimeMillis())
                put("useCount", 0)
            })
            "insert succeeded"
        } catch (e: Exception) {
            "insert failed: ${e.message}"
        }

        assertThat(result).contains("insert failed")
    }

    // endregion

    // region Empty String vs Null Edge Cases

    @Test
    @Throws(IOException::class)
    fun `migration distinguishes between empty string and null`() {
        val db1 = helper.createDatabase(testDbName, 1)
        
        // Meme with empty string title (not null)
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/empty_title.png"
        ).apply {
            put("title", "") // Empty string, not null
        })
        
        // Meme with null title
        db1.insert("memes", 0, MigrationTestFixtures.createMemeV1(
            filePath = "/test/null_title.png",
            title = null
        ))
        
        db1.close()

        val db6 = helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        )

        db6.query("SELECT filePath, title FROM memes ORDER BY filePath").use { cursor ->
            // First row: empty_title.png
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("/test/empty_title.png")
            assertThat(cursor.isNull(1)).isFalse()
            assertThat(cursor.getString(1)).isEmpty()
            
            // Second row: null_title.png
            cursor.moveToNext()
            assertThat(cursor.getString(0)).isEqualTo("/test/null_title.png")
            assertThat(cursor.isNull(1)).isTrue()
        }
    }

    // endregion
}
