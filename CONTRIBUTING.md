# Contributing to Riposte

Thank you for your interest in contributing to Riposte! This document provides guidelines and instructions for contributing to the project.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Architecture Guidelines](#architecture-guidelines)
- [Code Style](#code-style)
- [Testing Requirements](#testing-requirements)
- [Pull Request Process](#pull-request-process)
- [Issue Reporting](#issue-reporting)

## Code of Conduct

We are committed to providing a welcoming and inclusive experience for everyone. Please be respectful in all interactions. A formal Code of Conduct will be added in a future update.

## Getting Started

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Android Studio | Ladybug 2024.2.1+ | Or later versions |
| JDK | 17 | Required for Gradle |
| Android SDK | 35 | Target SDK |
| Kotlin | 2.3.0 | Managed by Gradle |
| Python | 3.11+ | For CLI tool only |

### Repository Structure

```
riposte/
├── app/                    # Main application module
├── core/                   # Shared core modules
│   ├── common/            # Utilities, navigation routes
│   ├── database/          # Room database, DAOs
│   ├── datastore/         # DataStore preferences
│   ├── ml/                # ML Kit, embeddings
│   ├── model/             # Domain models
│   ├── testing/           # Test utilities
│   └── ui/                # Design system
├── feature/               # Feature modules
│   ├── gallery/           # Meme gallery
│   ├── import/            # Image import
│   ├── search/            # Search functionality
│   ├── share/             # Sharing
│   └── settings/          # App settings
└── tools/riposte-cli/ # Python CLI tool
```

## Development Setup

### Building the Project

```bash
# Clone the repository
git clone https://github.com/yourusername/riposte.git
cd riposte

# Build debug APK (standard model - recommended)
./gradlew :app:assembleStandardDebug

# Build smallest APK (no models)
./gradlew :app:assembleLiteDebug

# Run all unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest
```

**Note**: The app uses embedding model flavors to control APK size:
- **lite**: No models (~177 MB)
- **standard**: Generic model (~350 MB) - **default**
- **qualcomm**: Qualcomm-optimized (~880 MB)
- **mediatek**: MediaTek-optimized (~555 MB)
- **full**: All models (~1.3 GB)

All builds target universal (all architectures). See [BUILD_FLAVORS.md](docs/BUILD_FLAVORS.md) for details.

### CLI Tool Setup

```bash
cd tools/riposte-cli

# Windows
scripts/setup.ps1

# macOS/Linux
./scripts/setup.sh

# Run CLI
meme-cli --help
```

## Architecture Guidelines

### Clean Architecture

The project follows Clean Architecture with three layers:

1. **Presentation** (feature modules)
   - Compose UI screens
   - ViewModels with MVI pattern
   - UI state, intents, and effects

2. **Domain** (use cases, interfaces)
   - Business logic in Use Case classes
   - Repository interfaces
   - Domain models

3. **Data** (implementations)
   - Repository implementations
   - DAOs and data sources
   - Data mapping

### MVI Pattern

Each feature should follow the MVI pattern:

```kotlin
// UI State - Single immutable state class
data class FeatureUiState(
    val isLoading: Boolean = false,
    val items: List<Item> = emptyList(),
    val error: String? = null
)

// Intent - User actions
sealed interface FeatureIntent {
    data object LoadData : FeatureIntent
    data class ItemClicked(val id: Long) : FeatureIntent
}

// Effect - One-time side effects
sealed interface FeatureEffect {
    data class ShowError(val message: String) : FeatureEffect
    data class NavigateToDetail(val id: Long) : FeatureEffect
}
```

### Module Dependencies

- Feature modules depend only on `core` modules
- Feature modules should NOT depend on other feature modules
- Core modules should NOT depend on feature modules
- The `app` module wires everything together

## Code Style

### Kotlin Conventions

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4-space indentation
- Maximum line length: 120 characters
- Use trailing commas in multi-line declarations
- Prefer `val` over `var`
- Use meaningful names for variables and functions

### Compose Guidelines

- Keep composables stateless when possible
- Hoist state to the caller
- Always pass `modifier: Modifier = Modifier` as the last parameter
- Use `collectAsStateWithLifecycle` for Flow collection
- Create small, focused composables

### Documentation

- Document public APIs with KDoc
- Explain "why", not "what" in comments
- Update README when adding new features

## Testing Requirements

### Test Coverage Expectations

- **ViewModels**: Unit tests required for all public methods
- **Use Cases**: Unit tests required
- **Repositories**: Unit tests for implementations
- **UI**: At least smoke tests for screens

### Test Patterns

```kotlin
@Test
fun `intent triggers expected state change`() = runTest {
    // Given
    val viewModel = createViewModel()
    
    // When
    viewModel.onIntent(SomeIntent.LoadData)
    
    // Then
    viewModel.uiState.test {
        val state = awaitItem()
        assertThat(state.isLoading).isFalse()
        assertThat(state.items).isNotEmpty()
    }
}
```

### Running Tests

```bash
# All unit tests
./gradlew test

# Specific module
./gradlew :feature:gallery:test

# With coverage
./gradlew testDebugUnitTestCoverage
```

## Pull Request Process

### Branch Naming

Use descriptive branch names with prefixes:

- `feature/` - New features
- `fix/` - Bug fixes
- `refactor/` - Code refactoring
- `docs/` - Documentation changes
- `test/` - Test additions/fixes

Examples:
- `feature/emoji-picker-improvements`
- `fix/search-crash-on-empty-query`

### Commit Messages

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Examples:
- `feat(gallery): add bulk delete functionality`
- `fix(search): handle empty query properly`
- `docs: update architecture documentation`

### Before Submitting

1. Run all tests: `./gradlew test`
2. Run lint: `./gradlew lint`
3. Ensure no new warnings
4. Update documentation if needed
5. Add tests for new functionality

### Review Process

1. Create a pull request against `main`
2. Fill out the PR template
3. Request review from maintainers
4. Address feedback
5. Squash and merge when approved

## Issue Reporting

### Bug Reports

Include:
- Device and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots/logs if applicable

### Feature Requests

Include:
- Clear description of the feature
- Use case and motivation
- Mockups if applicable

---

Thank you for contributing to Riposte! 🎭
