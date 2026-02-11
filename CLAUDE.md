# CLAUDE.md

Riposte is a multi-module Android application for organizing, searching, and sharing memes with emoji-based categorization and AI-powered semantic search.

## Tech Stack

- **Language**: Kotlin 2.3.0
- **UI**: Jetpack Compose (BOM 2025.12.00) with Material 3
- **Architecture**: Clean Architecture + MVI pattern
- **DI**: Hilt 2.58
- **Database**: Room 2.8.4 with FTS4 for full-text search
- **Async**: Coroutines 1.10.1 & Flow
- **AI/ML**: ML Kit (text recognition/labeling) + MediaPipe (embeddings) + LiteRT
- **Image Loading**: Coil 3.3.0
- **Navigation**: Type-safe Navigation Compose 2.9.6
- **Serialization**: Kotlinx Serialization 1.8.0
- **Build**: Gradle Version Catalogs

## Project Structure

```text
riposte/
├── app/                    # Main application, wires modules together
├── core/
│   ├── common/            # Shared utilities, navigation routes, extensions
│   ├── database/          # Room database, DAOs, entities, migrations
│   ├── datastore/         # DataStore preferences
│   ├── ml/                # ML Kit, MediaPipe, semantic search, embeddings
│   ├── model/             # Domain models (Meme, EmojiTag, ShareConfig, etc.)
│   ├── testing/           # Test utilities, fakes, rules
│   └── ui/                # Design system, theme, reusable components
├── feature/
│   ├── gallery/           # Meme grid view, detail view, favorites
│   ├── import/            # Image import, ZIP bundle import
│   ├── search/            # FTS + semantic search
│   ├── share/             # Sharing with format/quality/size options
│   └── settings/          # App preferences
├── baselineprofile/       # Startup performance optimization
├── docs/                  # Technical documentation
└── tools/riposte-cli/ # Python CLI for batch AI annotation
```

## Development Commands

```bash
# Build debug APK
./gradlew :app:assembleDebug

# Run all unit tests
./gradlew test

# Run specific module tests
./gradlew :feature:gallery:test

# Run Android instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Generate baseline profile
./gradlew :app:generateBaselineProfile

# CLI tool (requires venv)
cd tools/riposte-cli
scripts/setup.ps1  # or setup.sh
meme-cli annotate <directory> --zip
meme-cli annotate <directory> --languages en,cs --force
```

## Key Patterns

### MVI (Model-View-Intent)

Each feature screen has:

- **UiState**: Single immutable data class holding all screen state
- **Intent**: Sealed interface representing user actions
- **Effect**: Sealed interface for one-time side effects (navigation, snackbars)
- **ViewModel**: Processes intents, updates state via `MutableStateFlow`, emits effects via `Channel`

```kotlin
// State
data class GalleryUiState(val memes: List<Meme>, val isLoading: Boolean, ...)

// Intent
sealed interface GalleryIntent {
    data object LoadMemes : GalleryIntent
    data class OpenMeme(val memeId: Long) : GalleryIntent
}

// Effect
sealed interface GalleryEffect {
    data class NavigateToMeme(val memeId: Long) : GalleryEffect
}
```

### Use Cases

Business logic lives in single-purpose Use Case classes:

```kotlin
class GetMemesUseCase @Inject constructor(
    private val repository: MemeRepository
) {
    operator fun invoke(): Flow<List<Meme>> = repository.getMemes()
}
```

### Type-Safe Navigation

Navigation routes are defined as serializable objects in `core/common`:

```kotlin
@Serializable data object GalleryRoute
@Serializable data class MemeDetailRoute(val memeId: Long)
```

### Repository Pattern

- Interfaces in domain/feature modules
- Implementations in data layer with `@Inject constructor`
- Bound via Hilt `@Binds` in modules

### Pagination (Paging3)

Gallery uses Paging3 for large meme collections (1000+):

```kotlin
// DAO returns PagingSource
@Query("SELECT * FROM memes ORDER BY importedAt DESC")
fun getAllMemesPaged(): PagingSource<Int, MemeEntity>

// Repository wraps in Pager
Pager(config = PagingConfig(pageSize = 20)) { ... }.flow

// ViewModel caches with cachedIn(viewModelScope)
// UI collects with collectAsLazyPagingItems()
```

## Important Files

| File | Purpose |
| ---- | ------- |
| `gradle/libs.versions.toml` | All dependency versions |
| `core/database/MemeDatabase.kt` | Room database with migrations |
| `core/ml/SemanticSearchEngine.kt` | Hybrid FTS + vector search |
| `core/model/Meme.kt` | Primary domain model |
| `app/src/main/.../RiposteNavHost.kt` | Navigation graph |
| `docs/METADATA_FORMAT.md` | XMP metadata schema spec |
| `docs/SEMANTIC_SEARCH.md` | Search implementation details |
| `.github/instructions/` | Domain-specific coding guidelines |

## Gotchas

- **Kotlin source dirs**: Use `src/main/kotlin/` not `src/main/java/`
- **FTS sync**: `MemeFtsEntity` must be kept in sync with `MemeEntity` updates
- **FTS query sanitization**: Always sanitize user input before FTS MATCH clauses
- **Embedding regeneration**: When ML model version changes, embeddings are marked `needsRegeneration`
- **WorkManager + Hilt**: App implements `Configuration.Provider` for `HiltWorkerFactory`
- **Type-safe navigation**: Use `@Serializable` route objects, not string routes
- **Modifier parameter**: Always pass `modifier: Modifier = Modifier` as last param in composables
- **ZIP import**: Validates paths to prevent ZIP Slip attacks (canonical path validation)
- **Feature module isolation**: Feature modules must NOT depend on other features (use navigation)
- **Metadata schema**: v1.3 supports `primaryLanguage`, `localizations` for i18n, and `basedOn` for meme origin
- **CLI tool**: Uses GitHub Copilot SDK, requires `copilot auth login` first
- **Paging for All filter**: Use `usePaging=true` for All memes, regular lists for filtered views
- **Database schema versioning**: Only one schema version bump allowed per release cycle. If the schema was already bumped, add your changes to the existing migration instead of creating a new one. See `core/database/released-schema-version.txt`.

## Agent Instructions

When working in this codebase:

1. **Read instruction files** in `.github/instructions/` before editing matching files
2. **Follow MVI pattern** - one UiState, sealed Intent, sealed Effect per screen
3. **Use Use Cases** for business logic, keep ViewModels thin
4. **Prefer Flow** over suspend for data streams, use `collectAsStateWithLifecycle` in Compose
5. **Add tests** - unit tests for ViewModels/UseCases using MockK + Turbine
6. **Use version catalog** - add new dependencies to `libs.versions.toml`
7. **Sanitize user input** - especially for FTS queries and file paths
8. **Keep features isolated** - no feature-to-feature dependencies
9. **Run tests** before completing work: `./gradlew test`
10. **Lint** before PRs: `./gradlew lint`
11. **Never revert work done by other agents or in prior turns.** If a file was already modified, preserve those changes. Do not reset, checkout, or overwrite files to their original state. Only make additive or surgical edits on top of existing work.

## CLI Tool Notes

The Python CLI at `tools/riposte-cli/` annotates images with AI:

- Uses GitHub Copilot SDK (`github-copilot-sdk`)
- Outputs JSON sidecar files per image (schema v1.3)
- Rate limited with exponential backoff
- Supports `--zip` to create importable ZIP bundles
- Processing modes: `--force` (overwrite), `--continue` (skip existing), `--dry-run`
- Multilingual: `--languages en,cs,de` for translations
- No fallback behavior - errors propagate

See `.github/copilot-instructions.md` for detailed CLI documentation.
