package com.adsamcik.riposte.core.database.migration

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.adsamcik.riposte.core.database.ALL_MIGRATIONS
import com.adsamcik.riposte.core.database.MIGRATION_1_2
import com.adsamcik.riposte.core.database.MIGRATION_2_3
import com.adsamcik.riposte.core.database.MIGRATION_3_4
import com.adsamcik.riposte.core.database.MIGRATION_4_5
import com.adsamcik.riposte.core.database.MIGRATION_5_6
import com.adsamcik.riposte.core.database.MemeDatabase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests that every database migration produces a schema matching what Room expects.
 * Uses exported JSON schemas to create a database at version N, run the migration,
 * and validate the result against version N+1's expected schema.
 *
 * Uses direct [FrameworkSQLiteOpenHelperFactory] instead of Room's [MigrationTestHelper]
 * to avoid Room 2.8+ / Robolectric SupportSQLiteDriver path-name mismatch issues.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val openHelpers = mutableListOf<SupportSQLiteOpenHelper>()

    @After
    fun teardown() {
        openHelpers.forEach { runCatching { it.close() } }
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun `migrate from 1 to 2`() {
        createDatabaseAtVersion(1).close()
        val db = migrateToVersion(2, MIGRATION_1_2)
        validateSchemaMatchesVersion(db, 2)
        db.close()
    }

    @Test
    fun `migrate from 2 to 3`() {
        createDatabaseAtVersion(2).close()
        val db = migrateToVersion(3, MIGRATION_2_3)
        validateSchemaMatchesVersion(db, 3)
        db.close()
    }

    @Test
    fun `migrate from 3 to 4`() {
        createDatabaseAtVersion(3).close()
        val db = migrateToVersion(4, MIGRATION_3_4)
        validateSchemaMatchesVersion(db, 4)
        db.close()
    }

    @Test
    fun `migrate from 4 to 5`() {
        createDatabaseAtVersion(4).close()
        val db = migrateToVersion(5, MIGRATION_4_5)
        validateSchemaMatchesVersion(db, 5)
        db.close()
    }

    @Test
    fun `migrate from 5 to 6`() {
        createDatabaseAtVersion(5).close()
        val db = migrateToVersion(6, MIGRATION_5_6)
        validateSchemaMatchesVersion(db, 6)
        db.close()
    }

    @Test
    fun `migrate from 1 to latest version`() {
        createDatabaseAtVersion(1).close()
        val db = migrateToVersion(MemeDatabase.LATEST_VERSION, *ALL_MIGRATIONS)
        validateSchemaMatchesVersion(db, MemeDatabase.LATEST_VERSION)
        db.close()
    }

    // region Schema Helpers

    /**
     * Creates a database at the given version using CREATE statements from the
     * exported Room schema JSON.
     */
    private fun createDatabaseAtVersion(version: Int): SupportSQLiteDatabase {
        val schema = loadSchema(version)
        val helper =
            createOpenHelper(
                version,
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        executeSchemaCreate(db, schema)
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        error("Expected onCreate for version $version, got onUpgrade($oldVersion→$newVersion)")
                    }
                },
            )
        return helper.writableDatabase
    }

    /**
     * Opens the existing database and applies migrations to reach [targetVersion].
     */
    private fun migrateToVersion(
        targetVersion: Int,
        vararg migrations: androidx.room.migration.Migration,
    ): SupportSQLiteDatabase {
        val helper =
            createOpenHelper(
                targetVersion,
                object : SupportSQLiteOpenHelper.Callback(targetVersion) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        error("Expected onUpgrade to version $targetVersion, not onCreate")
                    }

                    override fun onUpgrade(
                        db: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        var current = oldVersion
                        while (current < newVersion) {
                            val migration =
                                migrations.firstOrNull { it.startVersion == current }
                                    ?: error("No migration from version $current (target: $newVersion)")
                            migration.migrate(db)
                            current = migration.endVersion
                        }
                    }
                },
            )
        return helper.writableDatabase
    }

    private fun createOpenHelper(
        version: Int,
        callback: SupportSQLiteOpenHelper.Callback,
    ): SupportSQLiteOpenHelper {
        val config =
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(callback)
                .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).also {
            openHelpers.add(it)
        }
    }

    /**
     * Validates that the migrated database schema matches what Room expects for
     * the given version by comparing table names and column sets.
     */
    private fun validateSchemaMatchesVersion(
        db: SupportSQLiteDatabase,
        version: Int,
    ) {
        val schema = loadSchema(version)
        val entities = schema.getJSONObject("database").getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val isFts = entity.optString("ftsVersion").isNotEmpty()

            val expectedColumns = mutableSetOf<String>()
            val fields = entity.getJSONArray("fields")
            for (j in 0 until fields.length()) {
                expectedColumns.add(fields.getJSONObject(j).getString("columnName"))
            }

            val actualColumns = getTableColumns(db, tableName, isFts)

            assertThat(actualColumns)
                .containsAtLeastElementsIn(expectedColumns)
        }
    }

    private fun getTableColumns(
        db: SupportSQLiteDatabase,
        tableName: String,
        isFts: Boolean,
    ): Set<String> {
        if (isFts) {
            // FTS tables don't support PRAGMA table_info; query the creation SQL instead
            val cursor =
                db.query(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(tableName),
                )
            val columns = mutableSetOf<String>()
            if (cursor.moveToFirst()) {
                val sql = cursor.getString(0)
                // Extract column names from: USING FTS4(`col1` TYPE, `col2` TYPE, ...)
                val columnPattern = Regex("""`(\w+)`\s+TEXT""")
                columnPattern.findAll(sql).forEach { columns.add(it.groupValues[1]) }
            }
            cursor.close()
            return columns
        }

        val cursor = db.query("PRAGMA table_info(`$tableName`)")
        val columns = mutableSetOf<String>()
        while (cursor.moveToNext()) {
            columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        cursor.close()
        return columns
    }

    private fun loadSchema(version: Int): JSONObject {
        val json =
            context.assets
                .open("com.adsamcik.riposte.core.database.MemeDatabase/$version.json")
                .bufferedReader()
                .readText()
        return JSONObject(json)
    }

    private fun executeSchemaCreate(
        db: SupportSQLiteDatabase,
        schema: JSONObject,
    ) {
        val entities = schema.getJSONObject("database").getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val createSql =
                entity.getString("createSql")
                    .replace("\${TABLE_NAME}", tableName)
            db.execSQL(createSql)

            // Create indices
            val indices = entity.optJSONArray("indices")
            if (indices != null) {
                for (j in 0 until indices.length()) {
                    val indexSql =
                        indices.getJSONObject(j).getString("createSql")
                            .replace("\${TABLE_NAME}", tableName)
                    db.execSQL(indexSql)
                }
            }

            // Create FTS content sync triggers
            val triggers = entity.optJSONArray("contentSyncTriggers")
            if (triggers != null) {
                for (j in 0 until triggers.length()) {
                    db.execSQL(triggers.getString(j))
                }
            }
        }
    }

    // endregion

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
