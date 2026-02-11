# -*- coding: utf-8 -*-
"""Fix ktlint violations in test files."""
import re
import os

os.chdir(r"G:\Github\meme-my-mood")

# ========== FILE 1: ImportRequestDaoTest.kt ==========
path1 = os.path.join("core", "database", "src", "test", "kotlin", "com", "adsamcik",
                      "riposte", "core", "database", "dao", "ImportRequestDaoTest.kt")
with open(path1, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Remove blank line at start of class body
content = content.replace(
    "class ImportRequestDaoTest {\n\n    private",
    "class ImportRequestDaoTest {\n    private",
)

# 2. Fix 'database = Room.inMemoryDatabaseBuilder(' multiline expression
content = content.replace(
    "        database = Room.inMemoryDatabaseBuilder(",
    "        database =\n            Room.inMemoryDatabaseBuilder(",
)

# 3. Fix createRequest expression body - replace entire block
old_cr = (
    "    ) = ImportRequestEntity(\n"
    "        id = id,\n"
    "        status = status,\n"
    "        imageCount = imageCount,\n"
    "        completedCount = completedCount,\n"
    "        failedCount = failedCount,\n"
    "        stagingDir = stagingDir,\n"
    "        createdAt = createdAt,\n"
    "        updatedAt = updatedAt,\n"
    "    )"
)
new_cr = (
    "    ) =\n"
    "        ImportRequestEntity(\n"
    "            id = id,\n"
    "            status = status,\n"
    "            imageCount = imageCount,\n"
    "            completedCount = completedCount,\n"
    "            failedCount = failedCount,\n"
    "            stagingDir = stagingDir,\n"
    "            createdAt = createdAt,\n"
    "            updatedAt = updatedAt,\n"
    "        )"
)
content = content.replace(old_cr, new_cr)

# 4. Fix createItem expression body
old_ci = (
    "    ) = ImportRequestItemEntity(\n"
    "        id = id,\n"
    "        requestId = requestId,\n"
    "        stagedFilePath = stagedFilePath,\n"
    "        originalFileName = originalFileName,\n"
    "        emojis = emojis,\n"
    "        title = title,\n"
    "        description = description,\n"
    "        extractedText = extractedText,\n"
    "        status = status,\n"
    "        errorMessage = errorMessage,\n"
    "    )"
)
new_ci = (
    "    ) =\n"
    "        ImportRequestItemEntity(\n"
    "            id = id,\n"
    "            requestId = requestId,\n"
    "            stagedFilePath = stagedFilePath,\n"
    "            originalFileName = originalFileName,\n"
    "            emojis = emojis,\n"
    "            title = title,\n"
    "            description = description,\n"
    "            extractedText = extractedText,\n"
    "            status = status,\n"
    "            errorMessage = errorMessage,\n"
    "        )"
)
content = content.replace(old_ci, new_ci)

# 5. Fix all '() = runTest {' to put runTest on new line
content = re.sub(
    r"(    fun `[^`]+`\(\)) = runTest \{",
    r"\1 =\n        runTest {",
    content,
)

# 6. Fix 'val items = listOf(' multiline
content = content.replace(
    "        val items = listOf(",
    "        val items =\n            listOf(",
)

# 7. Fix long createRequest lines (> 120 chars)
lines = content.split("\n")
new_lines = []
for line in lines:
    if len(line) > 120 and "dao.insertRequest(createRequest(" in line:
        m = re.match(r"^(\s+)dao\.insertRequest\(createRequest\((.*)\)\)$", line)
        if m:
            indent = m.group(1)
            args_str = m.group(2)
            args = [a.strip() for a in args_str.split(", ")]
            inner = indent + "    "
            args_indent = inner + "    "
            parts = [indent + "dao.insertRequest("]
            parts.append(inner + "createRequest(")
            for arg in args:
                parts.append(args_indent + arg + ",")
            parts.append(inner + "),")
            parts.append(indent + ")")
            new_lines.append("\n".join(parts))
            continue
    new_lines.append(line)
content = "\n".join(new_lines)

with open(path1, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("File 1 done")

# ========== FILE 2: MigrationCompletenessTest.kt ==========
path2 = os.path.join("core", "database", "src", "test", "kotlin", "com", "adsamcik",
                      "riposte", "core", "database", "migration", "MigrationCompletenessTest.kt")
with open(path2, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Remove unused import (assertThat - only assertWithMessage is used)
content = content.replace("import com.google.common.truth.Truth.assertThat\n", "")

# 2. Remove blank line at start of class body
content = content.replace(
    "class MigrationCompletenessTest {\n\n    @Test",
    "class MigrationCompletenessTest {\n    @Test",
)

# 3. Add trailing commas in assertWithMessage multiline string args
# Pattern: string ending with " followed by newline and )
# These are the closing quotes before ) in assertWithMessage calls

# Line 30 area: "Did you bump..."
content = content.replace(
    '"Did you bump the @Database version without adding a migration?"\n        )',
    '"Did you bump the @Database version without adding a migration?",\n        )',
)

# Line 42 area: "ends at..."
content = content.replace(
    '"ends at ${sorted[i].endVersion} but next migration starts at ${sorted[i + 1].startVersion}"\n            )',
    '"ends at ${sorted[i].endVersion} but next migration starts at ${sorted[i + 1].startVersion}",\n            )',
)

# Line 53 area: "should increment by 1"
content = content.replace(
    '"MIGRATION_${migration.startVersion}_${migration.endVersion} should increment by 1"\n            )',
    '"MIGRATION_${migration.startVersion}_${migration.endVersion} should increment by 1",\n            )',
)

# Line 69 area: "Run the build..."
content = content.replace(
    '"Run the build to generate it with Room\'s schemaDirectory configuration."\n            )',
    '"Run the build to generate it with Room\'s schemaDirectory configuration.",\n            )',
)

# Line 90 area: "Delete them..."
content = content.replace(
    '"Delete them to keep the schema directory clean."\n        )',
    '"Delete them to keep the schema directory clean.",\n        )',
)

# 4. Fix multiline expression: val schemaVersions = schemasDir.listFiles...
content = content.replace(
    "        val schemaVersions = schemasDir.listFiles",
    "        val schemaVersions =\n            schemasDir.listFiles",
)

with open(path2, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("File 2 done")

# ========== FILE 3: MigrationTest.kt ==========
path3 = os.path.join("core", "database", "src", "test", "kotlin", "com", "adsamcik",
                      "riposte", "core", "database", "migration", "MigrationTest.kt")
with open(path3, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Remove blank line at start of class body
content = content.replace(
    "class MigrationTest {\n\n    private",
    "class MigrationTest {\n    private",
)

# 2. Fix createDatabaseAtVersion method - the val helper = createOpenHelper block
# Need to read the exact content with the arrow character
# Find and replace the createOpenHelper block in createDatabaseAtVersion
old_block1_start = "        val helper = createOpenHelper(version, object : SupportSQLiteOpenHelper.Callback(version) {"
if old_block1_start in content:
    # Find the full block up to the closing })
    idx = content.index(old_block1_start)
    # Find the closing })
    block_end = content.index("\n        })", idx)
    block_end = content.index("\n", block_end + 1)  # include the }) line
    old_block1 = content[idx:block_end]

    # Get the onUpgrade line to preserve the arrow character
    upgrade_line_match = re.search(r'error\("Expected onCreate.*?".*?\)', old_block1)
    error_content = ""
    if upgrade_line_match:
        error_content = upgrade_line_match.group(0)

    new_block1 = (
        "        val helper =\n"
        "            createOpenHelper(\n"
        "                version,\n"
        "                object : SupportSQLiteOpenHelper.Callback(version) {\n"
        "                    override fun onCreate(db: SupportSQLiteDatabase) {\n"
        "                        executeSchemaCreate(db, schema)\n"
        "                    }\n"
        "\n"
        "                    override fun onUpgrade(\n"
        "                        db: SupportSQLiteDatabase,\n"
        "                        oldVersion: Int,\n"
        "                        newVersion: Int,\n"
        "                    ) {\n"
        "                        " + error_content + "\n"
        "                    }\n"
        "                },\n"
        "            )"
    )
    content = content[:idx] + new_block1 + content[block_end:]

# 3. Fix migrateToVersion method - the val helper = createOpenHelper block
old_block2_start = "        val helper = createOpenHelper(targetVersion, object : SupportSQLiteOpenHelper.Callback(targetVersion) {"
if old_block2_start in content:
    idx = content.index(old_block2_start)
    block_end = content.index("\n        })", idx)
    block_end = content.index("\n", block_end + 1)
    old_block2 = content[idx:block_end]

    # Get the error content for onCreate
    error_match = re.search(r'error\("Expected onUpgrade.*?".*?\)', old_block2)
    error_create = error_match.group(0) if error_match else ""

    # Get migration error
    migration_error = re.search(r'error\("No migration.*?".*?\)', old_block2)
    migration_err = migration_error.group(0) if migration_error else ""

    new_block2 = (
        "        val helper =\n"
        "            createOpenHelper(\n"
        "                targetVersion,\n"
        "                object : SupportSQLiteOpenHelper.Callback(targetVersion) {\n"
        "                    override fun onCreate(db: SupportSQLiteDatabase) {\n"
        "                        " + error_create + "\n"
        "                    }\n"
        "\n"
        "                    override fun onUpgrade(\n"
        "                        db: SupportSQLiteDatabase,\n"
        "                        oldVersion: Int,\n"
        "                        newVersion: Int,\n"
        "                    ) {\n"
        "                        var current = oldVersion\n"
        "                        while (current < newVersion) {\n"
        "                            val migration =\n"
        "                                migrations.firstOrNull { it.startVersion == current }\n"
        "                                    ?: " + migration_err + "\n"
        "                            migration.migrate(db)\n"
        "                            current = migration.endVersion\n"
        "                        }\n"
        "                    }\n"
        "                },\n"
        "            )"
    )
    content = content[:idx] + new_block2 + content[block_end:]

# 4. Fix createOpenHelper: val config = ... multiline
content = content.replace(
    "        val config = SupportSQLiteOpenHelper.Configuration.builder(context)",
    "        val config =\n            SupportSQLiteOpenHelper.Configuration.builder(context)",
)

# 5. Fix validateSchemaMatchesVersion params on separate lines
content = content.replace(
    "    private fun validateSchemaMatchesVersion(db: SupportSQLiteDatabase, version: Int) {",
    "    private fun validateSchemaMatchesVersion(\n"
    "        db: SupportSQLiteDatabase,\n"
    "        version: Int,\n"
    "    ) {",
)

# 6. Fix FTS block: val cursor = db.query( multiline
content = content.replace(
    "            val cursor = db.query(\n"
    '                "SELECT sql from sqlite_master',
    "            val cursor =\n"
    "                db.query(\n"
    '                    "SELECT sql from sqlite_master',
)
# Try alternate casing
content = content.replace(
    "            val cursor = db.query(\n"
    '                "SELECT sql FROM sqlite_master',
    "            val cursor =\n"
    "                db.query(\n"
    '                    "SELECT sql FROM sqlite_master',
)
# Fix arrayOf indent after the query split
content = content.replace(
    '                    "SELECT sql FROM sqlite_master WHERE type=\'table\' AND name=?",\n'
    "                arrayOf(tableName),\n"
    "            )",
    '                    "SELECT sql FROM sqlite_master WHERE type=\'table\' AND name=?",\n'
    "                    arrayOf(tableName),\n"
    "                )",
)

# 7. Fix loadSchema: val json = context.assets multiline
content = content.replace(
    "        val json = context.assets\n"
    "            .open(",
    "        val json =\n"
    "            context.assets\n"
    "                .open(",
)
content = content.replace(
    '                .open("com.adsamcik.riposte.core.database.MemeDatabase/$version.json")\n'
    "            .bufferedReader()\n"
    "            .readText()",
    '                .open("com.adsamcik.riposte.core.database.MemeDatabase/$version.json")\n'
    "                .bufferedReader()\n"
    "                .readText()",
)

# 8. Fix executeSchemaCreate params on separate lines
content = content.replace(
    "    private fun executeSchemaCreate(db: SupportSQLiteDatabase, schema: JSONObject) {",
    "    private fun executeSchemaCreate(\n"
    "        db: SupportSQLiteDatabase,\n"
    "        schema: JSONObject,\n"
    "    ) {",
)

# 9. Fix val createSql = entity.getString multiline
content = content.replace(
    '            val createSql = entity.getString("createSql")\n'
    "                .replace(",
    '            val createSql =\n'
    '                entity.getString("createSql")\n'
    "                    .replace(",
)

# 10. Fix val indexSql multiline
content = content.replace(
    '                    val indexSql = indices.getJSONObject(j).getString("createSql")\n'
    "                        .replace(",
    '                    val indexSql =\n'
    '                        indices.getJSONObject(j).getString("createSql")\n'
    "                            .replace(",
)

with open(path3, "w", encoding="utf-8", newline="\n") as f:
    f.write(content)
print("File 3 done")

print("\nAll files processed!")
