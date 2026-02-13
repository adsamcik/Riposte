package com.adsamcik.riposte.core.database.migration

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After

/**
 * Shared infrastructure for migration tests.
 * Provides helpers to create databases at specific versions, apply migrations,
 * load exported Room schemas, and validate the resulting schema.
 */
abstract class MigrationTestBase {
    protected val context: Context = ApplicationProvider.getApplicationContext()
    private val openHelpers = mutableListOf<SupportSQLiteOpenHelper>()

    @After
    fun teardown() {
        openHelpers.forEach { runCatching { it.close() } }
        context.deleteDatabase(TEST_DB)
    }

    /**
     * Creates a database at the given version using CREATE statements from the
     * exported Room schema JSON.
     */
    protected fun createDatabaseAtVersion(version: Int): SupportSQLiteDatabase {
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
    protected fun migrateToVersion(
        targetVersion: Int,
        vararg migrations: Migration,
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

    protected fun loadSchema(version: Int): JSONObject {
        val json =
            context.assets
                .open("com.adsamcik.riposte.core.database.MemeDatabase/$version.json")
                .bufferedReader()
                .readText()
        return JSONObject(json)
    }

    protected fun executeSchemaCreate(
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

    /**
     * Returns columns for a table as a map of columnName → [ColumnInfo].
     */
    protected fun getTableColumnsDetailed(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): Map<String, ColumnInfo> {
        val cursor = db.query("PRAGMA table_info(`$tableName`)")
        val columns = mutableMapOf<String, ColumnInfo>()
        while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
            val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1
            val defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
            val pk = cursor.getInt(cursor.getColumnIndexOrThrow("pk"))
            columns[name] = ColumnInfo(
                name = name,
                type = type,
                notNull = notNull,
                defaultValue = defaultValue,
                primaryKey = pk > 0,
            )
        }
        cursor.close()
        return columns
    }

    /**
     * Returns column names for a table (simple version, handles FTS tables).
     */
    protected fun getTableColumns(
        db: SupportSQLiteDatabase,
        tableName: String,
        isFts: Boolean,
    ): Set<String> {
        if (isFts) {
            val cursor =
                db.query(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(tableName),
                )
            val columns = mutableSetOf<String>()
            if (cursor.moveToFirst()) {
                val sql = cursor.getString(0)
                val columnPattern = Regex("""`(\w+)`\s+TEXT""")
                columnPattern.findAll(sql).forEach { columns.add(it.groupValues[1]) }
            }
            cursor.close()
            return columns
        }

        return getTableColumnsDetailed(db, tableName).keys
    }

    /**
     * Returns indices for a table as a list of [IndexInfo].
     */
    protected fun getTableIndices(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): List<IndexInfo> {
        val cursor = db.query("PRAGMA index_list(`$tableName`)")
        val indices = mutableListOf<IndexInfo>()
        while (cursor.moveToNext()) {
            val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1

            // Skip SQLite auto-indices
            if (indexName.startsWith("sqlite_autoindex_")) continue

            val columnsCursor = db.query("PRAGMA index_info(`$indexName`)")
            val indexColumns = mutableListOf<String>()
            while (columnsCursor.moveToNext()) {
                indexColumns.add(columnsCursor.getString(columnsCursor.getColumnIndexOrThrow("name")))
            }
            columnsCursor.close()

            indices.add(IndexInfo(name = indexName, columns = indexColumns, unique = unique))
        }
        cursor.close()
        return indices
    }

    /**
     * Returns foreign keys for a table as a list of [ForeignKeyInfo].
     */
    protected fun getTableForeignKeys(
        db: SupportSQLiteDatabase,
        tableName: String,
    ): List<ForeignKeyInfo> {
        val cursor = db.query("PRAGMA foreign_key_list(`$tableName`)")
        val foreignKeys = mutableListOf<ForeignKeyInfo>()
        while (cursor.moveToNext()) {
            foreignKeys.add(
                ForeignKeyInfo(
                    table = cursor.getString(cursor.getColumnIndexOrThrow("table")),
                    from = cursor.getString(cursor.getColumnIndexOrThrow("from")),
                    to = cursor.getString(cursor.getColumnIndexOrThrow("to")),
                    onDelete = cursor.getString(cursor.getColumnIndexOrThrow("on_delete")),
                ),
            )
        }
        cursor.close()
        return foreignKeys
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

    data class ColumnInfo(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKey: Boolean,
    )

    data class IndexInfo(
        val name: String,
        val columns: List<String>,
        val unique: Boolean,
    )

    data class ForeignKeyInfo(
        val table: String,
        val from: String,
        val to: String,
        val onDelete: String,
    )

    companion object {
        const val TEST_DB = "migration-test"
    }
}
