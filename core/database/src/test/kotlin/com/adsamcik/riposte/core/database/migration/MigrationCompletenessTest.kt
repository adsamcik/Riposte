package com.adsamcik.riposte.core.database.migration

import com.adsamcik.riposte.core.database.ALL_MIGRATIONS
import com.adsamcik.riposte.core.database.MemeDatabase
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import java.io.File

/**
 * Validates that the migration chain is complete and consistent.
 * These tests catch the case where someone bumps the database version
 * but forgets to add a corresponding migration.
 */
class MigrationCompletenessTest {

    @Test
    fun `migration chain starts at version 1`() {
        val firstMigration = ALL_MIGRATIONS.minByOrNull { it.startVersion }
        assertWithMessage("First migration should start at version 1")
            .that(firstMigration?.startVersion)
            .isEqualTo(1)
    }

    @Test
    fun `migration chain ends at latest database version`() {
        val lastMigration = ALL_MIGRATIONS.maxByOrNull { it.endVersion }
        assertWithMessage(
            "Last migration should end at database version ${MemeDatabase.LATEST_VERSION}. " +
                "Did you bump the @Database version without adding a migration?"
        )
            .that(lastMigration?.endVersion)
            .isEqualTo(MemeDatabase.LATEST_VERSION)
    }

    @Test
    fun `migration chain has no gaps`() {
        val sorted = ALL_MIGRATIONS.sortedBy { it.startVersion }
        for (i in 0 until sorted.size - 1) {
            assertWithMessage(
                "Gap in migration chain: MIGRATION_${sorted[i].startVersion}_${sorted[i].endVersion} " +
                    "ends at ${sorted[i].endVersion} but next migration starts at ${sorted[i + 1].startVersion}"
            )
                .that(sorted[i].endVersion)
                .isEqualTo(sorted[i + 1].startVersion)
        }
    }

    @Test
    fun `each migration increments version by 1`() {
        ALL_MIGRATIONS.forEach { migration ->
            assertWithMessage(
                "MIGRATION_${migration.startVersion}_${migration.endVersion} should increment by 1"
            )
                .that(migration.endVersion)
                .isEqualTo(migration.startVersion + 1)
        }
    }

    @Test
    fun `exported schema exists for every version`() {
        val schemasDir = File("schemas/com.adsamcik.riposte.core.database.MemeDatabase")
        if (!schemasDir.exists()) return // Skip if schemas not available (CI without checkout)

        for (version in 1..MemeDatabase.LATEST_VERSION) {
            val schemaFile = File(schemasDir, "$version.json")
            assertWithMessage(
                "Missing exported schema for version $version. " +
                    "Run the build to generate it with Room's schemaDirectory configuration."
            )
                .that(schemaFile.exists())
                .isTrue()
        }
    }

    @Test
    fun `no orphaned schema files exist beyond latest version`() {
        val schemasDir = File("schemas/com.adsamcik.riposte.core.database.MemeDatabase")
        if (!schemasDir.exists()) return // Skip if schemas not available (CI without checkout)

        val schemaVersions = schemasDir.listFiles { file -> file.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            ?: return

        val orphaned = schemaVersions.filter { it > MemeDatabase.LATEST_VERSION }
        assertWithMessage(
            "Orphaned schema files found: ${orphaned.map { "$it.json" }}. " +
                "These are from a reverted or conflicting version bump. " +
                "Delete them to keep the schema directory clean."
        )
            .that(orphaned)
            .isEmpty()
    }

    @Test
    fun `no duplicate migrations exist`() {
        val migrationKeys = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertWithMessage("Duplicate migrations found in ALL_MIGRATIONS")
            .that(migrationKeys)
            .containsNoDuplicates()
    }
}
