plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.adsamcik.riposte.core.database"
    compileSdk = 36

    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("test").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

tasks.register("validateDatabaseSchema") {
    group = "verification"
    description = "Validates Room database schema version consistency across annotations, constants, schema exports, and migrations"

    val dbFilePath = layout.projectDirectory.file(
        "src/main/kotlin/com/adsamcik/riposte/core/database/MemeDatabase.kt"
    )
    val migrationsFilePath = layout.projectDirectory.file(
        "src/main/kotlin/com/adsamcik/riposte/core/database/Migrations.kt"
    )
    val schemaDirPath = layout.projectDirectory.dir(
        "schemas/com.adsamcik.riposte.core.database.MemeDatabase"
    )

    inputs.file(dbFilePath)
    inputs.file(migrationsFilePath)
    inputs.dir(schemaDirPath)

    doLast {
        val dbContent = dbFilePath.asFile.readText()

        val annotationVersion = Regex("""version\s*=\s*(\d+)""")
            .find(dbContent)?.groupValues?.get(1)?.toInt()
            ?: throw GradleException("Could not find version in @Database annotation")

        val latestVersion = Regex("""LATEST_VERSION\s*=\s*(\d+)""")
            .find(dbContent)?.groupValues?.get(1)?.toInt()
            ?: throw GradleException("Could not find LATEST_VERSION constant")

        if (annotationVersion != latestVersion) {
            throw GradleException(
                "Version mismatch: @Database(version=$annotationVersion) != LATEST_VERSION=$latestVersion"
            )
        }

        val schemaDir = schemaDirPath.asFile
        if (!schemaDir.exists()) {
            throw GradleException("Schema directory not found: $schemaDir")
        }

        val schemaVersions = schemaDir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { it.nameWithoutExtension.toIntOrNull() }
            ?.sorted()
            ?: throw GradleException("No schema files found")

        val expectedVersions = (1..annotationVersion).toList()
        if (schemaVersions != expectedVersions) {
            val extra = schemaVersions - expectedVersions.toSet()
            val missing = expectedVersions - schemaVersions.toSet()
            val message = buildString {
                appendLine("Schema files do not match database version $annotationVersion:")
                if (extra.isNotEmpty()) appendLine("  Orphaned schema files: ${extra.map { "$it.json" }}")
                if (missing.isNotEmpty()) appendLine("  Missing schema files: ${missing.map { "$it.json" }}")
                appendLine("  Expected versions 1..$annotationVersion, found: $schemaVersions")
            }
            throw GradleException(message)
        }

        val migrationsContent = migrationsFilePath.asFile.readText()
        val migrationVersions = Regex("""Migration\((\d+),\s*(\d+)\)""")
            .findAll(migrationsContent)
            .map { it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            .toList()
            .sortedBy { it.first }

        if (migrationVersions.isNotEmpty()) {
            if (migrationVersions.first().first != 1) {
                throw GradleException("Migration chain does not start at version 1")
            }
            if (migrationVersions.last().second != annotationVersion) {
                throw GradleException(
                    "Migration chain ends at ${migrationVersions.last().second} " +
                        "but database version is $annotationVersion"
                )
            }
        }

        logger.lifecycle("Database schema version $annotationVersion is consistent:")
        logger.lifecycle("  @Database annotation: $annotationVersion")
        logger.lifecycle("  LATEST_VERSION constant: $latestVersion")
        logger.lifecycle("  Schema exports: ${schemaVersions.size} files (1..$annotationVersion)")
        logger.lifecycle(
            "  Migrations: ${migrationVersions.size} " +
                "(${migrationVersions.joinToString(" -> ") { "${it.first}->${it.second}" }})"
        )
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))

    // Room
    implementation(libs.bundles.room)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // Paging
    implementation(libs.paging.common)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Testing
    testImplementation(libs.bundles.testing)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.core.ktx)
}
