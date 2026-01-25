# Meme My Mood - GitHub Copilot Instructions

You are an expert Android developer working on **Meme My Mood**, a modern Android application for organizing, searching, and sharing memes with emoji-based categorization and AI-powered search.

## Project Overview

This is a multi-module Android application following Clean Architecture with MVI pattern. The app allows users to import images, tag them with emojis, search using AI-powered semantic search, and share memes with customizable settings.

## Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.3.0 |
| UI Framework | Jetpack Compose | BOM 2025.12.00 |
| Architecture | Clean Architecture + MVI | - |
| Dependency Injection | Hilt | 2.58 |
| Database | Room + FTS4 | 2.8.4 |
| Async | Coroutines & Flow | 1.10.1 |
| AI/ML | ML Kit + LiteRT | Latest |
| Image Loading | Coil 3 | 3.3.0 |
| Navigation | Type-safe Navigation Compose | 2.9.6 |
| Build System | Gradle Version Catalogs | - |
| Serialization | Kotlinx Serialization | 1.8.0 |

## Project Structure

```
meme-my-mood/
├── app/                    # Main application module
├── core/
│   ├── common/            # Shared utilities & extensions
│   ├── database/          # Room database & DAOs
│   ├── datastore/         # DataStore preferences
│   ├── ml/                # ML Kit & LiteRT integration
│   ├── model/             # Domain models
│   ├── testing/           # Test utilities
│   └── ui/                # Design system & components
├── feature/
│   ├── gallery/           # Meme gallery feature
│   ├── import/            # Image import feature
│   ├── search/            # Search feature
│   ├── share/             # Sharing feature
│   └── settings/          # Settings feature
└── baselineprofile/       # Performance profiling
```

## Coding Standards

### Kotlin Best Practices
- Use Kotlin idioms: `let`, `apply`, `also`, `run`, `with` appropriately
- Prefer immutable data structures (`val` over `var`, immutable collections)
- Use data classes for domain models
- Use sealed classes/interfaces for state and events
- Leverage extension functions for cleaner APIs
- Use inline functions for higher-order functions with lambdas
- Prefer expression bodies for simple functions
- Use named arguments for clarity in function calls with multiple parameters

### Compose Guidelines
- Follow unidirectional data flow (UDF)
- Keep composables stateless when possible; hoist state
- Use `remember` and `derivedStateOf` for expensive calculations
- Prefer `LaunchedEffect` for side effects, `DisposableEffect` for cleanup
- Use `collectAsStateWithLifecycle` for Flow collection in Compose
- Create small, focused composables for reusability
- Use Material 3 components and dynamic colors
- Follow the slot-based API pattern for flexible composables
- Annotate composables with `@Composable` and preview functions with `@Preview`

### MVI Architecture
- **State**: Single immutable UI state class per screen
- **Intent**: Sealed class representing user actions
- **ViewModel**: Process intents, update state, emit side effects
- Use `StateFlow` for UI state, `SharedFlow` for one-time events
- Keep business logic in Use Cases, not ViewModels

### Clean Architecture Layers
1. **Presentation** (feature modules): Compose UI, ViewModels
2. **Domain** (core/model): Use Cases, Domain Models, Repository Interfaces
3. **Data** (core/database, core/datastore): Repository Implementations, Data Sources

### Dependency Injection (Hilt)
- Use `@HiltViewModel` for ViewModels
- Use `@Inject constructor` for dependencies
- Define modules in `di` package within each module
- Use `@Singleton` for app-wide singletons
- Use `@ViewModelScoped` when appropriate

### Room Database
- Use suspend functions for database operations
- Use Flow for observable queries
- Define entities in the database module
- Use type converters for complex types
- Follow FTS4 patterns for full-text search

### Coroutines & Flow
- Use `viewModelScope` in ViewModels
- Prefer `Flow` over `suspend` for data streams
- Use appropriate dispatchers: `IO` for database/network, `Default` for CPU
- Handle exceptions with `catch` operator or try-catch
- Use `stateIn` and `shareIn` for sharing flows

### Testing
- Write unit tests for ViewModels, Use Cases, and Repositories
- Use MockK for mocking
- Use Turbine for Flow testing
- Write UI tests with Compose testing library
- Use the test doubles in `core/testing`

### Error Handling
- Use Result or sealed classes for operation outcomes
- Provide user-friendly error messages
- Log errors appropriately for debugging
- Never crash silently; always handle exceptions

### Performance
- Use baseline profiles for startup optimization
- Optimize Compose recomposition with stable types
- Use `@Stable` and `@Immutable` annotations appropriately
- Lazy load images with Coil
- Implement pagination for large lists

## Module Dependencies

- Feature modules depend on `core` modules only
- Core modules should not depend on feature modules
- `app` module wires everything together
- Use API/implementation separation in Gradle

## Code Style

- Follow Kotlin coding conventions
- Use 4-space indentation
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations
- Document public APIs with KDoc
- Use meaningful variable and function names

## Common Patterns

### Screen State
```kotlin
data class ScreenUiState(
    val isLoading: Boolean = false,
    val items: List<Item> = emptyList(),
    val error: String? = null
)
```

### User Intent
```kotlin
sealed interface ScreenIntent {
    data object LoadData : ScreenIntent
    data class ItemClicked(val id: String) : ScreenIntent
}
```

### ViewModel Pattern
```kotlin
@HiltViewModel
class ScreenViewModel @Inject constructor(
    private val useCase: UseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(ScreenUiState())
    val uiState: StateFlow<ScreenUiState> = _uiState.asStateFlow()
    
    fun onIntent(intent: ScreenIntent) { /* ... */ }
}
```

## DO NOT
- Don't use deprecated APIs
- Don't block the main thread
- Don't hardcode strings (use string resources)
- Don't ignore lint warnings without justification
- Don't create God classes/objects
- Don't mix UI logic with business logic
- Don't use platform-specific code in shared modules

## CLI Tool (meme-my-mood-cli)

The project includes a Python CLI tool at `tools/meme-my-mood-cli/` for batch annotating meme images with AI.

### Key Points
- Uses **GitHub Models API** at `https://models.github.ai/inference/chat/completions`
- NOT the Copilot SDK (which requires Copilot CLI backend)
- Authentication: GitHub PAT with `models` scope
- Model format: `openai/gpt-4.1` (provider prefix required)
- Outputs JSON sidecar files matching `MemeMetadata` schema
- **No fallback behavior** - errors should propagate, not return placeholder data
- **venv required** - run `scripts/setup.ps1` or `scripts/setup.sh`

### Metadata Schema
```json
{
  "schemaVersion": "1.0",
  "emojis": ["😂", "🔥"],
  "title": "Plain descriptive title",
  "description": "Brief description",
  "tags": ["tag1", "tag2"],
  "textContent": "Extracted text from image",
  "createdAt": "2026-01-25T12:00:00+00:00",
  "appVersion": "cli-1.0.0"
}
```

### Title Guidelines
- Use plain, descriptive titles (max 50 chars)
- Describe what's in the image, not clever wordplay
- Good: "Person in gamer outfit pointing at camera"
- Bad: "Gamer Vibes On Point"
