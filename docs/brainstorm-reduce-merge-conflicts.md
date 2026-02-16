# Brainstorm: Reducing Merge Conflicts for Parallel AI Agents

> **Goal:** Make the Riposte repo structurally resistant to merge conflicts when multiple AI agents (or developers) work on separate features simultaneously.

---

## 1. File-Per-Migration + Auto-Discovery

**Name:** Split Migrations into Individual Files

**Elevator Pitch:** Move each `MIGRATION_X_Y` object from the monolithic `Migrations.kt` into its own file, then auto-discover them at build time so agents never touch the same migration file concurrently.

**User Story:** *As an AI agent adding a new database column, I want to create a single new file (`Migration_7_8.kt`) without editing any existing file, so my change cannot conflict with another agent's concurrent migration.*

**Technical Insight:** Kotlin top-level `val` declarations can be discovered at runtime via a naming convention, and Gradle `fileTree` can generate a source list at compile time. This pattern is underused because most Room tutorials show a single-file approach, and small projects never hit the conflict threshold.

**Implementation Sketch:**
- Create directory `core/database/src/main/kotlin/.../migration/` with one file per migration (e.g., `Migration_1_2.kt`, `Migration_2_3.kt`, …`Migration_6_7.kt`)
- Each file exports a single top-level `val MIGRATION_X_Y: Migration`
- Replace `ALL_MIGRATIONS` array in `Migrations.kt` with a new `MigrationRegistry.kt` that collects all `MIGRATION_*` vals using an explicit import list — OR use a `@MigrationEntry` annotation + KSP processor to auto-generate the array (annotation approach is heavier; start with explicit imports in a registry file)
- Update `DatabaseModule.kt` to call `MigrationRegistry.allMigrations()` instead of `ALL_MIGRATIONS`
- Delete `Migrations.kt` once all migrations are extracted

**Complexity Gut-Check:** Low. Purely mechanical file split + one registry file. No runtime behavior change. ~2 hours.

**One-line reuse note:** Any multi-module Room project with >3 migrations benefits immediately.

**Hotspot file removed:** `core/database/.../Migrations.kt` — currently 294 lines, every schema change forces a merge-conflict-prone append to the same file and the `ALL_MIGRATIONS` array literal.

---

## 2. Room AutoMigration / AutoMigrationSpec Opportunities

**Name:** Adopt Room AutoMigration for Simple Schema Changes

**Elevator Pitch:** Let Room auto-generate migrations for additive column/table changes (which represent ~50% of Riposte's migration history), eliminating hand-written SQL that agents must carefully merge.

**User Story:** *As an AI agent adding a nullable column to `MemeEntity`, I want to bump the `@Database` version and add an `@AutoMigration` annotation instead of writing raw SQL, so my change is a one-line annotation diff rather than a 30-line SQL block.*

**Technical Insight:** Room 2.4+ supports `@AutoMigration(from = X, to = Y)` with optional `AutoMigrationSpec` for renames/deletes. In Riposte, migrations 4→5 (add `metadataJson` column) and 6→7 (drop table) are straightforward auto-migration candidates. The feature is underused because existing hand-written migrations work and developers don't retroactively convert — but for *future* migrations, it dramatically shrinks diff surface.

**Implementation Sketch:**
- Add `autoMigrations` parameter to the `@Database` annotation in `MemeDatabase.kt` for future simple migrations (keep existing hand-written ones as-is for safety)
- For the *next* additive migration, use `@AutoMigration(from = 7, to = 8)` instead of a manual `Migration` object
- If a column rename or table delete is needed, create a minimal `AutoMigrationSpec` class in `core/database/.../migration/` (one file per spec)
- Document the decision rule in `CONTRIBUTING.md`: "Use AutoMigration for add-column, add-table, drop-table. Use manual Migration for FTS rebuilds, data transforms, index recreation."
- Ensure `exportSchema = true` remains set (already is) and Room schema JSON files are committed

**Complexity Gut-Check:** Low for future migrations. Do NOT retroactively convert migrations 1→7 (risk of subtle schema drift). ~1 hour for setup + docs.

**One-line reuse note:** Any Room 2.4+ project can adopt this for additive-only changes with zero runtime risk.

**Hotspot file removed:** Future entries in `Migrations.kt` / `MigrationRegistry` — each AutoMigration is a single annotation line on `MemeDatabase.kt` rather than a new 20–100 line SQL block file, reducing both file count and conflict surface.

---

## 3. Route-Per-Feature-Module

**Name:** Decentralize Route Declarations into Feature Modules

**Elevator Pitch:** Move each route's `@Serializable` declaration from the centralized `Routes.kt` into the feature module that owns the screen, so adding a new feature's route never touches shared files.

**User Story:** *As an AI agent building a new "Collections" feature module, I want to declare `CollectionsRoute` inside `feature/collections/` without editing `core/common/navigation/Routes.kt`, so my PR has zero overlap with other agents' feature work.*

**Technical Insight:** Navigation Compose 2.9+ resolves routes by their serialized class name at runtime — there is no compile-time registry. Routes can live anywhere on the classpath as long as the serialization plugin processes them. They're centralized in `core/common` today purely by convention, not necessity. Moving them to feature modules is safe because the app module already depends on all features.

**Implementation Sketch:**
- In each feature module, create a `navigation/Routes.kt` (e.g., `feature/gallery/.../navigation/GalleryRoutes.kt`) containing only that feature's routes
- Move `GalleryRoute` and `MemeDetailRoute` → `feature/gallery`, `ImportRoute` → `feature/import`, `SettingsRoute` → `feature/settings`
- Update imports in `RiposteNavHost.kt` and each feature's `*Navigation.kt` to point to the new locations
- Delete `core/common/navigation/Routes.kt`
- If a route is needed cross-feature (e.g., `GalleryRoute` referenced from `MemeDetailScreen`'s "back to gallery" logic), keep it in `core/common` or use the existing `NavController.popBackStack(route)` which doesn't need the route class

**Complexity Gut-Check:** Low. Mechanical move + import updates. Compile verifies correctness. ~1 hour.

**One-line reuse note:** Standard pattern in Now in Android and every multi-module Nav Compose project.

**Hotspot file removed:** `core/common/navigation/Routes.kt` — every new screen forces two agents to edit the same 23-line file and resolve conflicting `@Serializable` declarations.

---

## 4. Gradle Include Auto-Scanning

**Name:** Auto-Scan Module Includes in `settings.gradle.kts`

**Elevator Pitch:** Replace the manual `include(":core:X")` / `include(":feature:Y")` list with a directory-scanning loop, so creating a new module only requires creating a directory — no edit to `settings.gradle.kts`.

**User Story:** *As an AI agent scaffolding a new `feature/collections` module, I want to create the directory and `build.gradle.kts` and have Gradle auto-include it, so I never conflict with another agent adding `feature/tags` at the same line in `settings.gradle.kts`.*

**Technical Insight:** Gradle's `settings.gradle.kts` runs before the build and has full access to `java.io.File` APIs. A simple `file("feature").listFiles()?.filter { it.resolve("build.gradle.kts").exists() }?.forEach { include(":feature:${it.name}") }` loop replaces all manual feature includes. This is underused because it adds ~5 lines of slightly "magic" Gradle code that some teams consider less explicit.

**Implementation Sketch:**
- Replace the `// Feature modules` block in `settings.gradle.kts` with a `scanModules("feature")` helper function
- Do the same for `// Core modules` → `scanModules("core")` and `// AI Pack modules` → `scanModules("aipacks")`
- The helper: `fun scanModules(dir: String) { file(dir).listFiles()?.filter { it.isDirectory && it.resolve("build.gradle.kts").exists() }?.sorted()?.forEach { include(":$dir:${it.name}") } }`
- Keep `:app` as an explicit include (it's the only top-level module)
- Add a comment explaining the convention: "To add a module, create `<group>/<name>/build.gradle.kts`"

**Complexity Gut-Check:** Trivial. 10-line change in one file. Fully backwards-compatible. ~30 min.

**One-line reuse note:** Drop-in pattern for any multi-module Gradle project; Square and Slack use variants of this.

**Hotspot file removed:** `settings.gradle.kts` line-level conflicts — currently every new module requires appending an `include()` line in a region where all agents collide.

---

## 5. Version Catalog Splitting Strategy

**Name:** Split `libs.versions.toml` by Domain

**Elevator Pitch:** Break the monolithic 247-line version catalog into domain-specific catalogs (e.g., `libs.versions.toml`, `testing.versions.toml`, `ml.versions.toml`) so agents working on unrelated concerns never edit the same TOML file.

**User Story:** *As an AI agent upgrading ML Kit dependencies, I want to edit `gradle/ml.versions.toml` without conflicting with another agent adding a new Compose library in `gradle/libs.versions.toml`.*

**Technical Insight:** Gradle 8.0+ supports multiple version catalogs via `versionCatalogs { create("testing") { from(files("gradle/testing.versions.toml")) } }` in `settings.gradle.kts`. Each catalog becomes a separate accessor (`testing.xxx` vs `libs.xxx`). This is underused because single-catalog is the default and splitting requires updating all `build.gradle.kts` references.

**Implementation Sketch:**
- Create `gradle/testing.versions.toml` — extract all test-only dependencies (JUnit, MockK, Turbine, Truth, Robolectric, Espresso, Compose testing, Hilt testing)
- Create `gradle/ml.versions.toml` — extract ML Kit, MediaPipe, LiteRT, AI Edge RAG, DJL
- Keep `gradle/libs.versions.toml` for core/UI/infra dependencies (Compose, Room, Hilt, Navigation, Coroutines, Coil, etc.)
- Register catalogs in `settings.gradle.kts`: `dependencyResolutionManagement { versionCatalogs { create("testing") { from(files("gradle/testing.versions.toml")) }; create("ml") { from(files("gradle/ml.versions.toml")) } } }`
- Update `build.gradle.kts` references: `testImplementation(testing.junit)` instead of `testImplementation(libs.junit)`

**Complexity Gut-Check:** Medium. Mechanical but touches every `build.gradle.kts` that uses test/ML deps. ~3 hours with search-and-replace.

**One-line reuse note:** Beneficial for any project where the version catalog exceeds ~150 lines or has distinct dependency domains.

**Hotspot file removed:** `gradle/libs.versions.toml` as a single 247-line file — currently the #1 conflict source because every dependency addition/upgrade from any domain hits the same file.

---

## 6. Convention Plugin Creation (build-logic)

**Name:** Extract Shared Build Config into Convention Plugins

**Elevator Pitch:** Move the repeated plugin applications and dependency blocks from feature/core `build.gradle.kts` files into composable convention plugins in a `build-logic/` included build, so adding a module is a one-liner `plugins { id("riposte.android.feature") }`.

**User Story:** *As an AI agent creating a new feature module, I want to apply `riposte.android.feature` and get Hilt, Compose, Navigation, and all standard core dependencies automatically, so my `build.gradle.kts` is 5 lines instead of 40 and cannot conflict with build-config changes.*

**Technical Insight:** Gradle included builds (`includeBuild("build-logic")`) provide project-isolated convention plugins that compose via `plugins { }` blocks. This is the pattern used by Now in Android and recommended by Gradle docs. Riposte currently duplicates the same ~35 lines of plugins + dependencies across all 4 feature modules and ~25 lines across 8 core modules. The root `build.gradle.kts` `subprojects {}` block is a code smell that convention plugins eliminate.

**Implementation Sketch:**
- Create `build-logic/convention/build.gradle.kts` with `kotlin-dsl` plugin, depending on AGP + Kotlin Gradle plugin
- Create convention plugins: `riposte.android.library` (compileSdk, minSdk, JVM target, ktlint), `riposte.android.compose` (Compose compiler, BOM, Compose bundle), `riposte.android.hilt` (Hilt plugin, KSP, dependencies), `riposte.android.feature` (composes library + compose + hilt + standard core module deps), `riposte.android.testing` (test dependencies)
- Add `includeBuild("build-logic")` to `settings.gradle.kts`
- Refactor each feature module's `build.gradle.kts` from ~40 lines to ~5–8 lines
- Move `subprojects { }` config from root `build.gradle.kts` into appropriate convention plugins

**Complexity Gut-Check:** Medium-High. Significant initial effort (~6 hours) but highest long-term payoff. Well-documented pattern with reference implementations.

**One-line reuse note:** Industry-standard pattern; directly follows the Now in Android template.

**Hotspot file removed:** All 4 `feature/*/build.gradle.kts` files as conflict-prone 40-line duplicates — each becomes a 5-line file that only lists module-specific dependencies, and shared build logic lives in convention plugins that change rarely.

---

## 7. NavHost Auto-Wiring via Feature Discovery

**Name:** Auto-Wire Feature Navigation via Hilt Multibinding

**Elevator Pitch:** Replace the manually-maintained `RiposteNavHost.kt` screen list with a Hilt `@IntoSet` multibinding of `NavigationContributor` interfaces, so each feature module registers its own screens and the NavHost just iterates the set.

**User Story:** *As an AI agent adding a new feature screen, I want to implement a `NavigationContributor` in my feature module and have it automatically appear in the nav graph, without editing `RiposteNavHost.kt` or the app module.*

**Technical Insight:** Hilt's `@IntoSet` multibinding collects implementations from all modules at compile time. Combined with a `NavigationContributor` interface that exposes a `fun registerGraph(NavGraphBuilder, NavController)` method, each feature module contributes its screens independently. This is underused because Navigation Compose docs show a centralized `NavHost` and most projects have <10 screens. At Riposte's scale with parallel agents, the centralized file becomes a bottleneck.

**Implementation Sketch:**
- Define `interface NavigationContributor { fun registerGraph(builder: NavGraphBuilder, navController: NavController) }` in `core/common`
- In each feature module, implement the interface (e.g., `GalleryNavigationContributor`) and bind with `@IntoSet` in the feature's Hilt module
- Refactor `RiposteNavHost.kt` to inject `Set<NavigationContributor>` and iterate: `contributors.forEach { it.registerGraph(this, navController) }`
- Keep the `startDestination = GalleryRoute` explicit in the `NavHost` call (the start destination is an app-level decision)
- Move feature-specific navigation callbacks (e.g., `onNavigateToImport`) into each contributor's implementation using the injected `NavController`

**Complexity Gut-Check:** Medium. Requires Hilt wiring in Composable context (use `hiltViewModel()` pattern or pass contributors from Activity). Need to handle start destination and deep links carefully. ~4 hours.

**One-line reuse note:** Scales to any modular Nav Compose app; especially valuable when feature count exceeds 5.

**Hotspot file removed:** `app/.../RiposteNavHost.kt` — currently every new screen requires adding imports + a `featureScreen(...)` block here, guaranteeing conflicts when two agents add screens concurrently.

---

## 8. Database DAO Registration via Hilt Multibinding

**Name:** Decouple DAO Provision from Centralized DatabaseModule

**Elevator Pitch:** Split per-DAO `@Provides` methods out of the monolithic `DatabaseModule.kt` into feature-local or per-DAO Hilt modules, so adding a new DAO never requires editing the central database module.

**User Story:** *As an AI agent adding a new `CollectionDao` to the database, I want to add a `@Provides` function in a new `CollectionDaoModule.kt` next to the DAO, rather than appending to the 65-line `DatabaseModule.kt` that every other agent also edits.*

**Technical Insight:** Hilt allows `@Provides` functions for the same component (`SingletonComponent`) to be spread across unlimited `@Module` objects in different modules. The `MemeDatabase` instance is already a singleton — any module that depends on `core:database` can provide its own DAO by injecting `MemeDatabase` and calling the abstract method. This is underused because putting all DAOs in one file feels "organized," but it creates a merge-conflict chokepoint.

**Implementation Sketch:**
- Keep `DatabaseModule.kt` with only `provideMemeDatabase()` — the single factory for the Room instance
- Create one `@Module` per DAO group or per feature's DAO needs, e.g.:
  - `core/database/.../di/MemeDaoModule.kt` → provides `MemeDao`, `MemeSearchDao`
  - `core/database/.../di/EmojiTagDaoModule.kt` → provides `EmojiTagDao`
  - `core/database/.../di/EmbeddingDaoModule.kt` → provides `MemeEmbeddingDao`
  - `core/database/.../di/ImportDaoModule.kt` → provides `ImportRequestDao`
- Each file is <15 lines with a single `@Provides` function
- When a new entity + DAO is added, the agent creates a new `*DaoModule.kt` file — zero edits to existing files
- Alternative: use a `@Binds`-based pattern with DAO interfaces, but Room DAOs are already abstract — `@Provides` from the database instance is simpler

**Complexity Gut-Check:** Low. Extract 5 functions into 4 files + trim `DatabaseModule.kt`. No runtime behavior change. ~1 hour.

**One-line reuse note:** Applicable to any Room + Hilt project with ≥3 DAOs.

**Hotspot file removed:** `core/database/.../di/DatabaseModule.kt` — currently every new DAO forces an edit to this file (new import + new `@Provides` fun), colliding with other agents doing the same.

---

## Summary: Conflict-Hotspot Elimination Map

| # | Idea | Hotspot File Eliminated | Effort |
|---|------|------------------------|--------|
| 1 | File-per-migration | `Migrations.kt` (294 lines) | Low |
| 2 | Room AutoMigration | Future `Migrations.kt` entries | Low |
| 3 | Route-per-feature | `Routes.kt` (23 lines) | Low |
| 4 | Gradle include auto-scan | `settings.gradle.kts` includes block | Trivial |
| 5 | Version catalog split | `libs.versions.toml` (247 lines) | Medium |
| 6 | Convention plugins | All `feature/*/build.gradle.kts` | Medium-High |
| 7 | NavHost auto-wiring | `RiposteNavHost.kt` (85 lines) | Medium |
| 8 | DAO registration split | `DatabaseModule.kt` (65 lines) | Low |

**Recommended implementation order:** 4 → 1 → 3 → 8 → 2 → 7 → 5 → 6 (easiest/highest-ROI first)
