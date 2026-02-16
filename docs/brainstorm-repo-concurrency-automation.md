# Riposte: Repo Concurrency Automation - Technical Brainstorm

**Goal**: Eliminate manual central registry edits across 6 key touch-points, enabling parallel feature development without merge conflicts.

---

## 1. Migration Auto-Discovery via File Convention

**Elevator Pitch**: Each migration lives in `core/database/migrations/Migration_X_Y.kt` and Room discovers them via filesystem scan at compile-time, eliminating `Migrations.kt` as a merge bottleneck.

**User Story**: As a developer adding database schema changes, I create a single migration file without touching shared registries, and my changes work immediately after rebase.

**Technical Insight**: 
- **Pattern**: Gradle task + KSP processor
- **API**: Custom `@RoomMigration` annotation + `MigrationCollector` KSP processor
- **Build Integration**: `generateMigrationRegistry` task produces `GeneratedMigrations.kt` pre-compilation

**Implementation Sketch**:
```kotlin
// NEW: core/database/src/main/kotlin/migrations/Migration_6_7.kt
@RoomMigration(from = 6, to = 7)
class Migration_6_7 : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memes ADD COLUMN primaryLanguage TEXT")
    }
}

// NEW: buildSrc/src/main/kotlin/com/adsamcik/riposte/gradle/MigrationCollector.kt
class MigrationCollectorProcessor : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val migrations = resolver.getSymbolsWithAnnotation("RoomMigration")
        generateMigrationsKt(migrations) // Writes GeneratedMigrations.kt
    }
}

// GENERATED: core/database/build/generated/ksp/.../GeneratedMigrations.kt
val ALL_MIGRATIONS = arrayOf(
    Migration_1_2(), Migration_2_3(), /* ... auto-discovered */
)

// MODIFIED: core/database/src/main/kotlin/MemeDatabase.kt
// - Remove manual migration array
// + Use GeneratedMigrations.ALL_MIGRATIONS

// NO LONGER EDITED: core/database/src/main/kotlin/Migrations.kt (DELETED)
```

**Complexity**: Medium (KSP processor, Gradle task orchestration, migration ordering validation)

**Existing Infrastructure Reuse**:
- Room's `addMigrations(*arrayOf())` API unchanged
- Existing migration test infrastructure works as-is
- Database module's KSP setup (already present for Room)

**Files No Longer Edited**:
- `core/database/src/main/kotlin/Migrations.kt` → **DELETED**

**New Files Created**:
- `core/database/src/main/kotlin/migrations/Migration_X_Y.kt` (per migration)
- `buildSrc/src/main/kotlin/.../MigrationCollectorProcessor.kt`
- `buildSrc/src/main/kotlin/.../MigrationAnnotation.kt`
- `core/database/build/generated/ksp/.../GeneratedMigrations.kt` (generated)

---

## 2. Per-Feature Route Definition with NavGraphBuilder Extensions

**Elevator Pitch**: Each feature module declares its own navigation extension function on `NavGraphBuilder`, and the app's NavHost discovers them via Gradle classpath reflection, eliminating `RiposteNavHost.kt` as a central registry.

**User Story**: As a feature developer, I define `fun NavGraphBuilder.myFeatureGraph()` in my module, and it's automatically wired into the app's navigation graph without modifying app-layer files.

**Technical Insight**:
- **Pattern**: Gradle plugin scans feature modules for `@NavGraphContributor` annotation
- **API**: `NavGraphBuilder.contributeFeature(NavContributor)` extension + ServiceLoader-style discovery
- **Compose Integration**: `RiposteNavHost` iterates discovered contributors via `ServiceLoader`

**Implementation Sketch**:
```kotlin
// NEW: core/common/src/main/kotlin/.../NavGraphContributor.kt
@Target(AnnotationTarget.CLASS)
annotation class NavGraphContributor

interface NavigationContributor {
    fun NavGraphBuilder.contribute(navController: NavController)
}

// NEW: feature/gallery/src/main/kotlin/.../GalleryNavigation.kt
@NavGraphContributor
class GalleryNavigationContributor : NavigationContributor {
    override fun NavGraphBuilder.contribute(navController: NavController) {
        composable<GalleryRoute> { GalleryScreen(/*...*/) }
        composable<MemeDetailRoute> { MemeDetailScreen(/*...*/) }
    }
}

// NEW: buildSrc/src/main/kotlin/.../NavGraphDiscoveryPlugin.kt
// Scans feature modules at configuration time, generates META-INF/services

// MODIFIED: app/src/main/kotlin/.../RiposteNavHost.kt
@Composable
fun RiposteNavHost(/*...*/) {
    NavHost(startDestination = GalleryRoute) {
        ServiceLoader.load(NavigationContributor::class.java).forEach {
            it.run { contribute(navController) }
        }
    }
}

// NO LONGER EDITED: app/.../RiposteNavHost.kt
// (still exists but no per-feature edits)
```

**Complexity**: High (ServiceLoader integration, annotation processing, correct classpath ordering)

**Existing Infrastructure Reuse**:
- Navigation Compose's `NavGraphBuilder` API unchanged
- Type-safe routes (`@Serializable`) still work
- Existing `navigateTo*()` helper functions preserved

**Files No Longer Edited**:
- `app/src/main/kotlin/.../RiposteNavHost.kt` (still exists, but no per-feature additions)

**New Files Created**:
- `core/common/src/main/kotlin/.../NavGraphContributor.kt`
- `core/common/src/main/kotlin/.../NavigationContributor.kt`
- `feature/*/src/main/kotlin/.../*/Navigation.kt` (per feature)
- `buildSrc/src/main/kotlin/.../NavGraphDiscoveryPlugin.kt`
- `feature/*/src/main/resources/META-INF/services/...NavigationContributor` (generated)

---

## 3. Hilt Multibindings with `@IntoSet` for Navigation Contributors

**Elevator Pitch**: Replace ServiceLoader with Hilt's `@IntoSet` to inject a `Set<NavigationContributor>`, enabling compile-safe feature navigation without META-INF boilerplate.

**User Story**: As a feature developer, I annotate my navigation module with `@Binds @IntoSet`, and Hilt automatically provides my routes to the app's NavHost without build-time code generation.

**Technical Insight**:
- **Pattern**: Hilt Multibindings (`@IntoSet`, `@ElementsIntoSet`)
- **API**: Inject `Set<NavigationContributor>` into `RiposteNavHost`
- **DI Scope**: `@Singleton` for contributors (stateless)

**Implementation Sketch**:
```kotlin
// NEW: core/common/src/main/kotlin/.../NavigationContributor.kt
interface NavigationContributor {
    fun NavGraphBuilder.contribute(navController: NavController)
}

// NEW: feature/gallery/src/main/kotlin/.../di/GalleryNavigationModule.kt
@Module
@InstallIn(SingletonComponent::class)
abstract class GalleryNavigationModule {
    @Binds
    @IntoSet
    abstract fun bindGalleryNavigation(
        impl: GalleryNavigationContributor
    ): NavigationContributor
}

// NEW: feature/gallery/src/main/kotlin/.../GalleryNavigationContributor.kt
class GalleryNavigationContributor @Inject constructor() : NavigationContributor {
    override fun NavGraphBuilder.contribute(navController: NavController) {
        composable<GalleryRoute> { GalleryScreen(/*...*/) }
    }
}

// MODIFIED: app/src/main/kotlin/.../RiposteNavHost.kt
@Composable
fun RiposteNavHost(
    navController: NavHostController,
    contributors: Set<NavigationContributor> // Injected via Hilt
) {
    NavHost(startDestination = GalleryRoute) {
        contributors.forEach { it.run { contribute(navController) } }
    }
}

// NO LONGER EDITED: app/.../RiposteNavHost.kt (param changed but no per-feature edits)
```

**Complexity**: Medium (Hilt setup familiar, ordering guarantees trickier)

**Existing Infrastructure Reuse**:
- Hilt already configured in all modules
- `@IntoSet` pattern used elsewhere (WorkerModule binds Worker factories)
- Navigation Compose APIs unchanged

**Files No Longer Edited**:
- `app/src/main/kotlin/.../RiposteNavHost.kt` (modified once for injection, then stable)

**New Files Created**:
- `core/common/src/main/kotlin/.../NavigationContributor.kt`
- `feature/*/src/main/kotlin/.../di/*NavigationModule.kt` (per feature)
- `feature/*/src/main/kotlin/.../*NavigationContributor.kt` (per feature)

---

## 4. Gradle Plugin: Auto-Include Feature Modules via Source Set Scanning

**Elevator Pitch**: A convention plugin scans `feature/*/` and `core/*/` directories at configuration time, auto-generating `include()` statements so `settings.gradle.kts` never needs manual edits when modules are added.

**User Story**: As a developer creating a new feature module, I scaffold the directory structure (`feature/export/`), and Gradle automatically recognizes it on next sync without touching `settings.gradle.kts`.

**Technical Insight**:
- **Pattern**: Gradle `Settings` plugin with `rootDir.resolve("feature").listFiles()`
- **API**: Custom `FeatureModuleDiscoveryPlugin` applied in `settings.gradle.kts`
- **Validation**: Requires `build.gradle.kts` presence to confirm valid module

**Implementation Sketch**:
```kotlin
// NEW: buildSrc/src/main/kotlin/com/adsamcik/riposte/gradle/FeatureModuleDiscoveryPlugin.kt
class FeatureModuleDiscoveryPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootDir = settings.rootDir
        
        // Auto-discover core modules
        rootDir.resolve("core").listFiles()?.forEach { coreDir ->
            if (coreDir.resolve("build.gradle.kts").exists()) {
                settings.include(":core:${coreDir.name}")
            }
        }
        
        // Auto-discover feature modules
        rootDir.resolve("feature").listFiles()?.forEach { featureDir ->
            if (featureDir.resolve("build.gradle.kts").exists()) {
                settings.include(":feature:${featureDir.name}")
            }
        }
        
        // Auto-discover aipack modules
        rootDir.resolve("aipacks").listFiles()?.forEach { aipackDir ->
            if (aipackDir.resolve("build.gradle.kts").exists()) {
                settings.include(":aipacks:${aipackDir.name}")
            }
        }
    }
}

// MODIFIED: settings.gradle.kts
pluginManagement { /* ... */ }
dependencyResolutionManagement { /* ... */ }

plugins {
    id("com.adsamcik.riposte.feature-discovery")
}

rootProject.name = "Riposte"
// NO MORE MANUAL include() CALLS
// include(":app") // Keep app, baselineprofile manually

include(":app")
include(":baselineprofile")

// NO LONGER EDITED: All feature/core includes removed
```

**Complexity**: Low (straightforward filesystem scan, well-established pattern)

**Existing Infrastructure Reuse**:
- Gradle Settings plugin API standard
- Existing module structure unchanged
- Convention plugins (riposte.android.library) still apply per-module

**Files No Longer Edited**:
- `settings.gradle.kts` → Reduced from ~15 `include()` calls to 2 + plugin apply

**New Files Created**:
- `buildSrc/src/main/kotlin/.../FeatureModuleDiscoveryPlugin.kt`
- `buildSrc/src/main/resources/META-INF/gradle-plugins/com.adsamcik.riposte.feature-discovery.properties`

---

## 5. Convention Plugin: Auto-Wire Feature Dependencies in App Module

**Elevator Pitch**: A Gradle convention plugin applied to `app/build.gradle.kts` automatically adds `implementation(project(":feature:*"))` for all discovered feature modules, eliminating manual dependency declarations when features are added.

**User Story**: As a developer adding a new feature module, the app module automatically depends on it after Gradle sync, and I can immediately navigate to my feature's screens without editing `app/build.gradle.kts`.

**Technical Insight**:
- **Pattern**: Convention plugin with `afterEvaluate` hook scanning `settings.gradle.kts` includes
- **API**: Custom `FeatureAggregationPlugin` queries `rootProject.subprojects`
- **Filtering**: Only includes modules matching `:feature:*` pattern

**Implementation Sketch**:
```kotlin
// NEW: buildSrc/src/main/kotlin/com/adsamcik/riposte/gradle/FeatureAggregationPlugin.kt
class FeatureAggregationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.plugins.apply("com.android.application")
        
        project.afterEvaluate {
            val featureModules = project.rootProject.subprojects
                .filter { it.path.startsWith(":feature:") }
            
            featureModules.forEach { featureModule ->
                project.dependencies.add("implementation", featureModule)
            }
            
            // Also auto-add all core modules
            val coreModules = project.rootProject.subprojects
                .filter { it.path.startsWith(":core:") }
            
            coreModules.forEach { coreModule ->
                project.dependencies.add("implementation", coreModule)
            }
        }
    }
}

// MODIFIED: app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    id("com.adsamcik.riposte.feature-aggregation")
}

android { /* ... */ }

dependencies {
    // NO MORE MANUAL implementation(project(":feature:gallery"))
    // NO MORE MANUAL implementation(project(":core:common"))
    
    // External libs still manual
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.ui)
    // ...
}

// NO LONGER EDITED: All project() dependencies removed
```

**Complexity**: Medium (dependency resolution timing, build configuration caching compatibility)

**Existing Infrastructure Reuse**:
- Gradle's `Project.dependencies` API standard
- Existing module structure unchanged
- Works with current multi-module setup

**Files No Longer Edited**:
- `app/build.gradle.kts` → Remove ~12 `implementation(project())` lines

**New Files Created**:
- `buildSrc/src/main/kotlin/.../FeatureAggregationPlugin.kt`
- `buildSrc/src/main/resources/META-INF/gradle-plugins/com.adsamcik.riposte.feature-aggregation.properties`

---

## 6. Version Catalog Decomposition: Feature-Scoped Mini-Catalogs

**Elevator Pitch**: Split `libs.versions.toml` into per-concern catalogs (`feature-libs.toml`, `core-libs.toml`, `test-libs.toml`) composed via Gradle's catalog includes, allowing parallel edits without merge conflicts on the monolithic version file.

**User Story**: As a feature developer adding a new dependency, I edit only `feature-libs.toml` in my module's directory, avoiding conflicts with platform team's `core-libs.toml` changes.

**Technical Insight**:
- **Pattern**: Gradle 8.1+ version catalog composition (`from(files())`)
- **API**: Multiple TOML files in `gradle/catalogs/`, aggregated in `settings.gradle.kts`
- **Namespacing**: Prefixes like `feature-*`, `core-*`, `test-*` prevent collision

**Implementation Sketch**:
```toml
# NEW: gradle/catalogs/core-libs.toml
[versions]
compose-bom = "2025.12.00"
room = "2.8.4"
hilt = "2.58"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }

[bundles]
room = ["room-runtime", "room-ktx"]

# NEW: gradle/catalogs/feature-libs.toml
[versions]
coil = "3.3.0"
paging = "3.3.5"

[libraries]
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }
paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }

# NEW: gradle/catalogs/test-libs.toml
[libraries]
junit = { group = "junit", name = "junit", version = "4.13.2" }
mockk = { group = "io.mockk", name = "mockk", version = "1.13.14" }
```

```kotlin
// MODIFIED: settings.gradle.kts
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("gradle/catalogs/core-libs.toml"))
        }
        create("featureLibs") {
            from(files("gradle/catalogs/feature-libs.toml"))
        }
        create("testLibs") {
            from(files("gradle/catalogs/test-libs.toml"))
        }
    }
}

// USAGE: feature/gallery/build.gradle.kts
dependencies {
    implementation(libs.compose.ui)              // Core catalog
    implementation(featureLibs.coil.compose)     // Feature catalog
    testImplementation(testLibs.junit)           // Test catalog
}

// NO LONGER EDITED: gradle/libs.versions.toml (split into 3 files)
```

**Complexity**: Low (Gradle 8.13+ native support, minimal refactor)

**Existing Infrastructure Reuse**:
- All existing `libs.*` references work if namespaced in `core-libs.toml`
- Build files change alias prefixes (`libs` → `featureLibs` for feature deps)
- Version management workflow familiar

**Files No Longer Edited**:
- `gradle/libs.versions.toml` → **DELETED** or archived

**New Files Created**:
- `gradle/catalogs/core-libs.toml`
- `gradle/catalogs/feature-libs.toml`
- `gradle/catalogs/test-libs.toml`
- `gradle/catalogs/ai-libs.toml` (optional, for ML Kit/MediaPipe)
- `gradle/catalogs/build-libs.toml` (optional, for plugins)

---

## Cross-Cutting Benefits

| Automation | Merge Conflicts Eliminated | Parallel Dev Enabled | Onboarding Complexity |
|------------|----------------------------|----------------------|----------------------|
| Migration Auto-Discovery | `Migrations.kt` bottleneck removed | ✅ DB schema changes | Medium (KSP learning curve) |
| Per-Feature Route Definitions | `RiposteNavHost.kt` stable | ✅ Navigation additions | High (ServiceLoader/Hilt setup) |
| Hilt NavGraph Contributors | `RiposteNavHost.kt` stable | ✅ Navigation additions | Medium (familiar Hilt pattern) |
| settings.gradle Auto-Include | `settings.gradle.kts` stable | ✅ Module creation | Low (invisible once setup) |
| App Module Auto-Deps | `app/build.gradle.kts` stable | ✅ Feature wiring | Medium (timing edge cases) |
| Version Catalog Decomposition | `libs.versions.toml` split | ✅ Dependency additions | Low (standard Gradle feature) |

## Implementation Priority

1. **Low-hanging fruit**: #4 (settings.gradle auto-include) + #6 (version catalog split) → No runtime impact, pure build config
2. **High-value, medium risk**: #3 (Hilt IntoSet navigation) → Leverages existing DI, clear ownership
3. **High complexity**: #1 (migration auto-discovery) + #2 (ServiceLoader navigation) → Requires KSP/annotation infrastructure

## Validation Criteria

- **Build Performance**: Configuration time increase <5% (measure with `--profile`)
- **IDE Support**: Android Studio sync recognizes modules without manual Gradle sync
- **Test Coverage**: All existing unit/integration tests pass without modification
- **Documentation**: Each automation pattern documented in `.github/instructions/gradle.instructions.md`
