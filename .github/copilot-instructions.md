# Riposte - GitHub Copilot Instructions

**Riposte** is a multi-module Android app for organizing, searching, and sharing memes with emoji-based categorization and AI-powered semantic search.

## Build, Test & Lint Commands

```bash
# Build (standard flavor recommended for development)
./gradlew :app:assembleStandardDebug

# Run all unit tests
./gradlew test

# Run a single module's tests
./gradlew :feature:gallery:test

# Run a single test class
./gradlew :feature:gallery:test --tests "*.GalleryViewModelTest"

# Lint
./gradlew lint

# Static analysis
./gradlew detekt

# Format code (auto-fixes)
./gradlew ktlintFormat

# Check formatting without fixing
./gradlew ktlintCheck

# Test coverage report
./gradlew testDebugUnitTestCoverage
```

### Build Flavors

The `embedding` product flavor dimension controls which on-device ML models are bundled:

| Flavor | APK Size | Description |
|--------|----------|-------------|
| `lite` | ~177 MB | No embedding models, basic search only |
| `standard` | ~350 MB | Generic model only — **recommended for dev** |
| `qualcomm` | ~880 MB | Generic + Qualcomm-optimized |
| `mediatek` | ~555 MB | Generic + MediaTek-optimized |
| `full` | ~1.3 GB | All models — for testing |

Flavor is part of the task name: `assembleStandardDebug`, `assembleLiteRelease`, etc.

## Architecture

Clean Architecture + MVI, split across multi-module Gradle project (`app/`, `core/`, `feature/`).

### Module Dependency Rules

- **Feature → Core only.** Feature modules must NOT depend on other features.
- **Core modules must NOT depend on features.**
- `app` module wires everything together (includes all features + all core modules).

### MVI Per Screen

Each feature screen has three sealed types plus a ViewModel:

- **UiState**: Single immutable data class holding all screen state
- **Intent**: Sealed interface of user actions
- **Effect**: Sealed interface for one-time side effects (navigation, snackbars)
- **ViewModel**: Processes intents → updates `StateFlow<UiState>` + emits effects via `Channel`

Business logic lives in single-purpose **Use Case** classes (`operator fun invoke`), not in ViewModels.

### Navigation

Type-safe routes defined as `@Serializable` objects/data classes in `core/common`. The nav graph lives in `app/.../RiposteNavHost.kt`.

### Pagination

Gallery uses Paging3 for the "All" filter (1000+ memes). DAO returns `PagingSource`, repository wraps in `Pager`, ViewModel caches with `cachedIn(viewModelScope)`, UI collects with `collectAsLazyPagingItems()`. Filtered views use regular lists.

### Search

Three search modes: FTS4 text search, emoji tag filtering, and semantic vector search (MediaPipe/EmbeddingGemma embeddings). Hybrid search implementation in `core/ml/SemanticSearchEngine.kt`.

### WorkManager + Hilt

The app implements `Configuration.Provider` for `HiltWorkerFactory`. Workers use `@HiltWorker` annotation.

## Key Conventions

### Domain-Specific Instruction Files

`.github/instructions/` contains detailed coding guidelines scoped by file pattern. These are automatically applied based on the file being edited:

| File | Applies To |
|------|-----------|
| `compose.instructions.md` | UI composables, screen files |
| `hilt.instructions.md` | DI modules, ViewModels |
| `room-database.instructions.md` | DAOs, entities, database |
| `testing.instructions.md` | Test files |
| `kotlin.instructions.md` | All Kotlin files |
| `gradle.instructions.md` | Build files, version catalogs |

### Security

- **FTS query sanitization**: Always remove special chars (`"*():`) and operators (`OR`, `AND`, `NOT`) from user input before FTS MATCH clauses. Never concatenate raw input.
- **ZIP Slip prevention**: Validate ZIP entry paths with canonical path checking before extraction.
- **No cleartext HTTP**: Enforced via `network_security_config.xml`.

### Gotchas

- Kotlin source dirs use `src/main/kotlin/`, not `src/main/java/`
- `MemeFtsEntity` must stay in sync with `MemeEntity` field changes
- When ML model version changes, embeddings are flagged `needsRegeneration`
- Compose `modifier` parameter: always `modifier: Modifier = Modifier` as the **last** parameter
- Dependencies go in `gradle/libs.versions.toml` (version catalog), not inline
- Custom convention plugins live in `buildSrc/` (e.g., `riposte.android.library`, `riposte.android.compose`, `riposte.android.hilt`)
- Metadata schema is v1.3 — supports `primaryLanguage`, `localizations` for i18n, and `basedOn` for meme origin
- **Database schema versioning**: Only one schema version bump per release cycle. If the database version was already bumped since the last release, add changes to the existing migration instead of creating a new one. The released version is tracked in `core/database/released-schema-version.txt` and enforced by the `validateDatabaseSchema` Gradle task.

### Testing Stack

JUnit 4 + MockK + Turbine (Flow testing) + Truth (assertions). Test utilities and fakes in `core/testing`. Compose UI tests use `createAndroidComposeRule` with `HiltTestRunner`. Backtick test names: `` `when user clicks save then meme is persisted` ``.

## UX Philosophy

The core user flow is: chatting → need a meme → open app → find via emoji/search → share → back to chat **in under 10 seconds**. Every feature decision should serve this share moment. Emojis are the primary taxonomy. Speed is a feature — gallery loads instantly, search results appear as you type.

## CLI Tool (`tools/riposte-cli/`)

Python CLI for batch AI annotation of meme images using GitHub Copilot SDK.

```bash
cd tools/riposte-cli
scripts/setup.ps1   # or setup.sh — creates venv
meme-cli annotate ./memes                        # English only
meme-cli annotate ./memes --languages en,cs,de   # With translations
meme-cli annotate ./memes --zip --force           # ZIP bundle, overwrite existing
```

- Requires `copilot auth login` first
- Uses `gpt-4.1` model via `github-copilot-sdk`
- Outputs JSON sidecar files per image (schema v1.3)
- No fallback behavior — errors propagate, not placeholder data
- Rate limited: 1s minimum delay between requests, exponential backoff on 429/5xx, up to 8 retries
