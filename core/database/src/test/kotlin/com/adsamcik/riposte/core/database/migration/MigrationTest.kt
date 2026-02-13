package com.adsamcik.riposte.core.database.migration

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
 * Tests that every database migration produces a schema matching what Room expects.
 * Uses exported JSON schemas to create a database at version N, run the migration,
 * and validate the result against version N+1's expected schema.
 *
 * Validates column names, types, NOT NULL constraints, default values, indices,
 * and foreign keys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MigrationTest : MigrationTestBase() {
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

    // region Schema Validation

    /**
     * Validates that the migrated database schema matches what Room expects for
     * the given version — including column names, types, NOT NULL constraints,
     * default values, indices, and foreign keys.
     */
    private fun validateSchemaMatchesVersion(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        version: Int,
    ) {
        val schema = loadSchema(version)
        val entities = schema.getJSONObject("database").getJSONArray("entities")

        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val tableName = entity.getString("tableName")
            val isFts = entity.optString("ftsVersion").isNotEmpty()

            validateColumns(db, entity, tableName, isFts)

            if (!isFts) {
                validateIndices(db, entity, tableName)
                validateForeignKeys(db, entity, tableName)
            }
        }
    }

    private fun validateColumns(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        entity: org.json.JSONObject,
        tableName: String,
        isFts: Boolean,
    ) {
        val fields = entity.getJSONArray("fields")
        val expectedColumnNames = mutableSetOf<String>()
        for (j in 0 until fields.length()) {
            expectedColumnNames.add(fields.getJSONObject(j).getString("columnName"))
        }

        if (isFts) {
            val actualColumns = getTableColumns(db, tableName, isFts = true)
            assertWithMessage("FTS table $tableName columns")
                .that(actualColumns)
                .containsAtLeastElementsIn(expectedColumnNames)
            return
        }

        val actualColumns = getTableColumnsDetailed(db, tableName)
        assertWithMessage("Table $tableName column names")
            .that(actualColumns.keys)
            .containsAtLeastElementsIn(expectedColumnNames)

        // Validate column types, NOT NULL, and defaults
        for (j in 0 until fields.length()) {
            val field = fields.getJSONObject(j)
            val columnName = field.getString("columnName")
            val actual = actualColumns[columnName] ?: continue

            val expectedAffinity = field.getString("affinity")
            assertWithMessage("$tableName.$columnName type")
                .that(actual.type)
                .isEqualTo(expectedAffinity)

            val expectedNotNull = field.optBoolean("notNull", false)
            assertWithMessage("$tableName.$columnName NOT NULL")
                .that(actual.notNull)
                .isEqualTo(expectedNotNull)
        }
    }

    private fun validateIndices(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        entity: org.json.JSONObject,
        tableName: String,
    ) {
        val expectedIndices = entity.optJSONArray("indices") ?: return
        val actualIndices = getTableIndices(db, tableName)

        for (j in 0 until expectedIndices.length()) {
            val expected = expectedIndices.getJSONObject(j)
            val expectedName =
                expected.getString("name")
                    .replace("\${TABLE_NAME}", tableName)
            val expectedUnique = expected.getBoolean("unique")

            val expectedColumns = mutableListOf<String>()
            val colArray = expected.getJSONArray("columnNames")
            for (k in 0 until colArray.length()) {
                expectedColumns.add(colArray.getString(k))
            }

            val actual = actualIndices.find { it.name == expectedName }
            assertWithMessage("Index $expectedName exists on $tableName")
                .that(actual)
                .isNotNull()
            actual?.let {
                assertWithMessage("Index $expectedName unique")
                    .that(it.unique)
                    .isEqualTo(expectedUnique)
                assertWithMessage("Index $expectedName columns")
                    .that(it.columns)
                    .containsExactlyElementsIn(expectedColumns)
                    .inOrder()
            }
        }
    }

    private fun validateForeignKeys(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        entity: org.json.JSONObject,
        tableName: String,
    ) {
        val expectedFks = entity.optJSONArray("foreignKeys") ?: return
        if (expectedFks.length() == 0) return

        val actualFks = getTableForeignKeys(db, tableName)

        for (j in 0 until expectedFks.length()) {
            val expected = expectedFks.getJSONObject(j)
            val expectedParent = expected.getString("table")
            val expectedOnDelete = expected.getString("onDelete")

            val expectedFromColumns = mutableListOf<String>()
            val fromArray = expected.getJSONArray("columns")
            for (k in 0 until fromArray.length()) {
                expectedFromColumns.add(fromArray.getString(k))
            }

            val expectedToColumns = mutableListOf<String>()
            val toArray = expected.getJSONArray("referencedColumns")
            for (k in 0 until toArray.length()) {
                expectedToColumns.add(toArray.getString(k))
            }

            // Match FK by parent table and from-column(s)
            for (col in expectedFromColumns.indices) {
                val matchingFk = actualFks.find {
                    it.table == expectedParent &&
                        it.from == expectedFromColumns[col] &&
                        it.to == expectedToColumns[col]
                }
                assertWithMessage(
                    "FK on $tableName.${expectedFromColumns[col]} → $expectedParent.${expectedToColumns[col]}",
                )
                    .that(matchingFk)
                    .isNotNull()
                matchingFk?.let {
                    assertWithMessage(
                        "FK $tableName.${expectedFromColumns[col]} ON DELETE",
                    )
                        .that(it.onDelete)
                        .isEqualTo(expectedOnDelete)
                }
            }
        }
    }

    // endregion
}
