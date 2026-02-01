package com.mememymood.core.database.migration

import android.content.Context
import androidx.room.Room
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
 * Comprehensive tests for database migrations.
 * 
 * These tests verify:
 * 1. Schema correctness after each migration
 * 2. Data integrity is preserved
 * 3. New columns have correct defaults
 * 4. Indices are created properly
 * 
 * Tests use [MigrationTestHelper] which validates the migrated schema
 * against the exported schema JSON files.
 */
@RunWith(AndroidJUnit4::class)
class MemeDatabaseMigrationTest {

    private val testDbName = "migration-test-db"

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
        // Clean up test database
        context.deleteDatabase(testDbName)
    }

    // region MIGRATION_1_2 Tests (Add meme_embeddings table)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 creates meme_embeddings table`() {
        // Create v1 database
        helper.createDatabase(testDbName, 1).apply {
            close()
        }

        // Run migration 1 -> 2
        val db = helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            MemeDatabase.MIGRATION_1_2
        )

        // Verify meme_embeddings table exists with correct columns
        val verifier = DataVerifier(db)
        assertThat(verifier.tableExists("meme_embeddings")).isTrue()
        
        val columns = verifier.getColumnNames("meme_embeddings")
        assertThat(columns).containsAtLeast(
            "id", "memeId", "embedding", "dimension", 
            "modelVersion", "generatedAt", "sourceTextHash", "needsRegeneration"
        )
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 creates required indices on meme_embeddings`() {
        helper.createDatabase(testDbName, 1).apply { close() }
        val db = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        val verifier = DataVerifier(db)
        assertThat(verifier.indexExists("index_meme_embeddings_memeId")).isTrue()
        assertThat(verifier.indexExists("index_meme_embeddings_modelVersion")).isTrue()
        assertThat(verifier.indexExists("index_meme_embeddings_generatedAt")).isTrue()
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 migrates existing embeddings from memes table`() {
        // Create v1 database with memes that have embeddings
        val db1 = helper.createDatabase(testDbName, 1)
        val insertedIds = MigrationTestFixtures.insertGoldenMemesV1(db1)
        
        // Count memes with non-null embeddings
        val memesWithEmbeddings = db1.query(
            "SELECT COUNT(*) FROM memes WHERE embedding IS NOT NULL"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        db1.close()

        // Run migration
        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        // Verify embeddings were migrated to new table
        val verifier = DataVerifier(db2)
        assertThat(verifier.getRowCount("meme_embeddings")).isEqualTo(memesWithEmbeddings)

        // Verify migrated embeddings have needsRegeneration = 1 (flagged for re-indexing)
        db2.query("SELECT needsRegeneration FROM meme_embeddings").use { cursor ->
            while (cursor.moveToNext()) {
                assertThat(cursor.getInt(0)).isEqualTo(1)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_1_2 preserves all meme data`() {
        // Create v1 database with test data
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        val beforeMemes = DataVerifier(db1).extractAllMemes()
        db1.close()

        // Run migration
        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )

        // Verify meme data is unchanged
        val afterMemes = DataVerifier(db2).extractAllMemes()
        DataVerifier(db2).assertMemesPreserved(beforeMemes, afterMemes)
    }

    // endregion

    // region MIGRATION_2_3 Tests (Add indices)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_2_3 creates indices on memes table`() {
        // Create and migrate to v2
        helper.createDatabase(testDbName, 1).apply { close() }
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()

        // Run migration 2 -> 3
        val db = helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        )

        val verifier = DataVerifier(db)
        assertThat(verifier.indexExists("index_memes_importedAt")).isTrue()
        assertThat(verifier.indexExists("index_memes_isFavorite")).isTrue()
        assertThat(verifier.indexExists("index_memes_filePath")).isTrue()
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_2_3 preserves all data`() {
        // Create v1 with data, migrate to v2
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )
        val beforeMemes = DataVerifier(db2).extractAllMemes()
        db2.close()

        // Run migration 2 -> 3
        val db3 = helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        )

        val afterMemes = DataVerifier(db3).extractAllMemes()
        DataVerifier(db3).assertMemesPreserved(beforeMemes, afterMemes)
    }

    // endregion

    // region MIGRATION_3_4 Tests (Add multilingual support)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_3_4 adds primaryLanguage and localizationsJson columns`() {
        // Create and migrate through v3
        helper.createDatabase(testDbName, 1).apply { close() }
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        ).close()

        // Run migration 3 -> 4
        val db = helper.runMigrationsAndValidate(
            testDbName, 4, true, MemeDatabase.MIGRATION_3_4
        )

        val verifier = DataVerifier(db)
        val columns = verifier.getColumnNames("memes")
        assertThat(columns).contains("primaryLanguage")
        assertThat(columns).contains("localizationsJson")
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_3_4 new columns default to NULL for existing rows`() {
        // Create v1 with data and migrate through v3
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        ).close()

        // Run migration 3 -> 4
        val db4 = helper.runMigrationsAndValidate(
            testDbName, 4, true, MemeDatabase.MIGRATION_3_4
        )

        // Verify existing rows have NULL for new columns
        db4.query("SELECT primaryLanguage, localizationsJson FROM memes").use { cursor ->
            while (cursor.moveToNext()) {
                assertThat(cursor.isNull(0)).isTrue()
                assertThat(cursor.isNull(1)).isTrue()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_3_4 preserves all existing data`() {
        // Create v1 with data and migrate through v3
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        
        val db3 = helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        )
        val beforeMemes = DataVerifier(db3).extractAllMemes()
        db3.close()

        // Run migration 3 -> 4
        val db4 = helper.runMigrationsAndValidate(
            testDbName, 4, true, MemeDatabase.MIGRATION_3_4
        )

        val afterMemes = DataVerifier(db4).extractAllMemes()
        // Ignore new columns in comparison
        DataVerifier(db4).assertMemesPreserved(
            beforeMemes,
            afterMemes,
            ignoredColumns = setOf("primaryLanguage", "localizationsJson")
        )
    }

    // endregion

    // region MIGRATION_4_5 Tests (Add indexing status tracking)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_4_5 adds indexingAttempts and lastAttemptAt columns to meme_embeddings`() {
        // Create and migrate through v4
        helper.createDatabase(testDbName, 1).apply { close() }
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 4, true, MemeDatabase.MIGRATION_3_4
        ).close()

        // Run migration 4 -> 5
        val db = helper.runMigrationsAndValidate(
            testDbName, 5, true, MemeDatabase.MIGRATION_4_5
        )

        val verifier = DataVerifier(db)
        val columns = verifier.getColumnNames("meme_embeddings")
        assertThat(columns).contains("indexingAttempts")
        assertThat(columns).contains("lastAttemptAt")
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_4_5 initializes indexingAttempts to 0`() {
        // Create v1 with memes that have embeddings
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        // Migrate through v4
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 4, true, MemeDatabase.MIGRATION_3_4
        ).close()

        // Run migration 4 -> 5
        val db5 = helper.runMigrationsAndValidate(
            testDbName, 5, true, MemeDatabase.MIGRATION_4_5
        )

        // Verify indexingAttempts defaults to 0
        db5.query("SELECT indexingAttempts FROM meme_embeddings").use { cursor ->
            while (cursor.moveToNext()) {
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    // endregion

    // region MIGRATION_5_6 Tests (Add view tracking)

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_5_6 adds viewCount and lastViewedAt columns`() {
        // Create and migrate through v5
        migrateToVersion(5)

        // Run migration 5 -> 6
        val db = helper.runMigrationsAndValidate(
            testDbName, 6, true, MemeDatabase.MIGRATION_5_6
        )

        val verifier = DataVerifier(db)
        val columns = verifier.getColumnNames("memes")
        assertThat(columns).contains("viewCount")
        assertThat(columns).contains("lastViewedAt")
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_5_6 creates indices for view tracking`() {
        migrateToVersion(5)

        val db = helper.runMigrationsAndValidate(
            testDbName, 6, true, MemeDatabase.MIGRATION_5_6
        )

        val verifier = DataVerifier(db)
        assertThat(verifier.indexExists("index_memes_viewCount")).isTrue()
        assertThat(verifier.indexExists("index_memes_lastViewedAt")).isTrue()
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_5_6 initializes viewCount to 0 and lastViewedAt to NULL`() {
        // Create v1 with data
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        // Migrate through v5
        migrateToVersion(5)

        // Run migration 5 -> 6
        val db6 = helper.runMigrationsAndValidate(
            testDbName, 6, true, MemeDatabase.MIGRATION_5_6
        )

        // Verify defaults
        db6.query("SELECT viewCount, lastViewedAt FROM memes").use { cursor ->
            while (cursor.moveToNext()) {
                assertThat(cursor.getInt(0)).isEqualTo(0)
                assertThat(cursor.isNull(1)).isTrue()
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun `MIGRATION_5_6 preserves all existing data`() {
        // Create v1 with data
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()
        
        // Migrate through v5
        val db5 = migrateToVersion(5)
        val beforeMemes = DataVerifier(db5).extractAllMemes()
        db5.close()

        // Run migration 5 -> 6
        val db6 = helper.runMigrationsAndValidate(
            testDbName, 6, true, MemeDatabase.MIGRATION_5_6
        )

        val afterMemes = DataVerifier(db6).extractAllMemes()
        DataVerifier(db6).assertMemesPreserved(
            beforeMemes,
            afterMemes,
            ignoredColumns = setOf("viewCount", "lastViewedAt")
        )
    }

    // endregion

    // region Full Migration Chain Tests

    @Test
    @Throws(IOException::class)
    fun `full migration from v1 to v6 preserves data and creates correct schema`() {
        // Create v1 with comprehensive test data
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        val originalMemeCount = DataVerifier(db1).getRowCount("memes")
        db1.close()

        // Run all migrations and validate against final schema
        helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        ).use { db ->
            val verifier = DataVerifier(db)
            
            // Verify meme count preserved
            assertThat(verifier.getRowCount("memes")).isEqualTo(originalMemeCount)
            
            // Verify all expected tables exist
            assertThat(verifier.tableExists("memes")).isTrue()
            assertThat(verifier.tableExists("meme_embeddings")).isTrue()
            assertThat(verifier.tableExists("memes_fts")).isTrue()
            
            // Verify final schema columns
            val memeColumns = verifier.getColumnNames("memes")
            assertThat(memeColumns).containsAtLeast(
                "id", "filePath", "fileName", "mimeType",
                "width", "height", "fileSizeBytes", "importedAt",
                "emojiTagsJson", "title", "description", "textContent",
                "embedding", "isFavorite", "createdAt", "useCount",
                "primaryLanguage", "localizationsJson",
                "viewCount", "lastViewedAt"
            )
        }
    }

    @Test
    @Throws(IOException::class)
    fun `Room can open fully migrated database`() {
        // Create v1 with data
        val db1 = helper.createDatabase(testDbName, 1)
        MigrationTestFixtures.insertGoldenMemesV1(db1)
        db1.close()

        // Run all migrations
        helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_1_2,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        ).close()

        // Open with Room and verify it works
        val database = Room.databaseBuilder(
            context,
            MemeDatabase::class.java,
            testDbName
        )
            .addMigrations(
                MemeDatabase.MIGRATION_1_2,
                MemeDatabase.MIGRATION_2_3,
                MemeDatabase.MIGRATION_3_4,
                MemeDatabase.MIGRATION_4_5,
                MemeDatabase.MIGRATION_5_6
            )
            .build()

        // Verify we can query using DAOs
        database.openHelper.writableDatabase.use { db ->
            val count = db.query("SELECT COUNT(*) FROM memes").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertThat(count).isEqualTo(MigrationTestFixtures.GOLDEN_MEME_COUNT)
        }
        
        database.close()
    }

    // endregion

    // region Partial Migration Chain Tests (Skip versions)

    @Test
    @Throws(IOException::class)
    fun `migration from v2 to v6 works correctly`() {
        // Create v1 and migrate to v2
        helper.createDatabase(testDbName, 1).apply { close() }
        val db2 = helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        )
        db2.close()

        // Migrate from v2 to v6
        helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_2_3,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        ).use { db ->
            // Schema validation is automatic
            val verifier = DataVerifier(db)
            assertThat(verifier.tableExists("memes")).isTrue()
            assertThat(verifier.getColumnNames("memes")).contains("viewCount")
        }
    }

    @Test
    @Throws(IOException::class)
    fun `migration from v3 to v6 works correctly`() {
        // Create and migrate to v3
        helper.createDatabase(testDbName, 1).apply { close() }
        helper.runMigrationsAndValidate(
            testDbName, 2, true, MemeDatabase.MIGRATION_1_2
        ).close()
        helper.runMigrationsAndValidate(
            testDbName, 3, true, MemeDatabase.MIGRATION_2_3
        ).close()

        // Migrate from v3 to v6
        helper.runMigrationsAndValidate(
            testDbName,
            6,
            true,
            MemeDatabase.MIGRATION_3_4,
            MemeDatabase.MIGRATION_4_5,
            MemeDatabase.MIGRATION_5_6
        ).use { db ->
            val verifier = DataVerifier(db)
            assertThat(verifier.getColumnNames("memes")).contains("primaryLanguage")
            assertThat(verifier.getColumnNames("memes")).contains("viewCount")
        }
    }

    // endregion

    // region Helper Methods

    /**
     * Helper to migrate database to a specific version.
     * Creates from v1 if database doesn't exist.
     */
    private fun migrateToVersion(targetVersion: Int): androidx.sqlite.db.SupportSQLiteDatabase {
        if (targetVersion < 1) throw IllegalArgumentException("Version must be >= 1")
        
        // Create at v1 if not exists
        try {
            helper.createDatabase(testDbName, 1).close()
        } catch (_: Exception) {
            // Database already exists
        }

        var db: androidx.sqlite.db.SupportSQLiteDatabase? = null
        
        if (targetVersion >= 2) {
            db = helper.runMigrationsAndValidate(
                testDbName, 2, true, MemeDatabase.MIGRATION_1_2
            )
            if (targetVersion == 2) return db
            db.close()
        }
        
        if (targetVersion >= 3) {
            db = helper.runMigrationsAndValidate(
                testDbName, 3, true, MemeDatabase.MIGRATION_2_3
            )
            if (targetVersion == 3) return db
            db.close()
        }
        
        if (targetVersion >= 4) {
            db = helper.runMigrationsAndValidate(
                testDbName, 4, true, MemeDatabase.MIGRATION_3_4
            )
            if (targetVersion == 4) return db
            db.close()
        }
        
        if (targetVersion >= 5) {
            db = helper.runMigrationsAndValidate(
                testDbName, 5, true, MemeDatabase.MIGRATION_4_5
            )
            if (targetVersion == 5) return db
            db.close()
        }
        
        if (targetVersion >= 6) {
            db = helper.runMigrationsAndValidate(
                testDbName, 6, true, MemeDatabase.MIGRATION_5_6
            )
        }
        
        return db ?: throw IllegalStateException("Failed to migrate to version $targetVersion")
    }

    // endregion
}
