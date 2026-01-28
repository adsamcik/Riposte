# Code Quality & Best Practices

This document describes the code quality tools, practices, and workflows established for the Meme My Mood project.

## Overview

We maintain high code quality through:
- **Static Analysis**: detekt and ktlint
- **Code Formatting**: .editorconfig and ktlint
- **Test Coverage**: JaCoCo with minimum thresholds
- **CI/CD**: GitHub Actions automated checks
- **Git Hooks**: Pre-commit validation
- **Convention Plugins**: Shared build configuration
- **Code Review**: PR templates and checklists

## Tools & Configuration

### Static Analysis

#### detekt
Kotlin static code analyzer that detects code smells, complexity, and potential bugs.

**Configuration:** `detekt.yml`

**Run detekt:**
```bash
# Check all modules
./gradlew detekt

# Generate report
./gradlew detekt
# Report: build/reports/detekt/detekt.html
```

**Key Rules Enabled:**
- Complexity analysis (cyclomatic complexity, long methods)
- Potential bugs (null safety, unsafe casts)
- Code style (naming, formatting)
- Performance issues
- Coroutines best practices

#### ktlint
Kotlin linter and formatter enforcing Kotlin coding conventions.

**Configuration:** `.editorconfig`

**Run ktlint:**
```bash
# Check formatting
./gradlew ktlintCheck

# Auto-fix formatting
./gradlew ktlintFormat
```

**Features:**
- Enforces Kotlin official style guide
- Auto-formatting capability
- Compose-specific rules via ktlint-compose-rules
- SARIF output for GitHub Code Scanning

### Code Formatting

#### .editorconfig
Cross-editor configuration for consistent code style.

**Enforces:**
- 4-space indentation for Kotlin/XML
- UTF-8 encoding
- LF line endings
- Trailing comma support
- Max line length: 120 characters

**Supported Editors:** VS Code, IntelliJ IDEA, Android Studio, Vim, Emacs, etc.

### Test Coverage

#### JaCoCo
Code coverage tool for Java/Kotlin.

**Configuration:** `AndroidJacocoConventionPlugin`

**Run coverage:**
```bash
# Generate coverage report
./gradlew testDebugUnitTestCoverage

# Verify coverage thresholds
./gradlew verifyCoverage

# Report: build/reports/jacoco/testDebugUnitTestCoverage/html/index.html
```

**Coverage Thresholds:**
- Minimum overall coverage: **60%**
- Minimum branch coverage: **50%**

**Exclusions:**
- Generated code (Hilt, Room, Data Binding)
- Android framework classes
- Test classes

### Convention Plugins

Reusable Gradle plugins that extract common build configuration.

**Location:** `buildSrc/`

**Available Plugins:**
1. `meme-my-mood.android.library` - Base Android library configuration
2. `meme-my-mood.android.compose` - Compose setup
3. `meme-my-mood.android.hilt` - Hilt dependency injection
4. `meme-my-mood.android.testing` - Test dependencies
5. `meme-my-mood.android.jacoco` - Code coverage

**Usage Example:**
```kotlin
plugins {
    id("meme-my-mood.android.library")
    id("meme-my-mood.android.compose")
    id("meme-my-mood.android.hilt")
}
```

**Documentation:** `buildSrc/README.md`

## CI/CD Workflows

### GitHub Actions

**Workflows:**

1. **CI** (`.github/workflows/ci.yml`)
   - Runs on: Push to main/develop, Pull Requests
   - Actions:
     - ktlint formatting check
     - detekt static analysis
     - Build debug APK
     - Run unit tests
     - Android Lint
   - Uploads: Build reports, APK artifacts

2. **Code Quality** (`.github/workflows/code-quality.yml`)
   - Runs on: Pull Requests, Push to main/develop
   - Actions:
     - detekt with SARIF upload (GitHub Code Scanning)
     - ktlint formatting check
     - Dependency verification
   - Uploads: Analysis reports

3. **PR Checks** (`.github/workflows/pr-checks.yml`)
   - Runs on: Pull Request events
   - Actions:
     - Validate PR title (semantic format)
     - Check for merge conflicts
     - Detect large files
     - Run build & tests (if Android changed)
     - Run CLI tests (if CLI changed)
     - Generate APK size report
   - Features: Path-based job filtering for efficiency

**Status Badges:**
Add to README.md:
```markdown
[![CI](https://github.com/yourusername/meme-my-mood/workflows/CI/badge.svg)](https://github.com/yourusername/meme-my-mood/actions/workflows/ci.yml)
[![Code Quality](https://github.com/yourusername/meme-my-mood/workflows/Code%20Quality/badge.svg)](https://github.com/yourusername/meme-my-mood/actions/workflows/code-quality.yml)
```

## Git Hooks

### Pre-commit Hook

**Location:** `.githooks/`

**Installation:**
```bash
# Windows
.\.githooks\install-hooks.ps1

# Linux/macOS
./.githooks/install-hooks.sh
```

**Checks Performed:**
1. ✅ **ktlint format** (auto-fixes and re-stages)
2. ✅ **detekt** (fails on issues)
3. ⚠️ **Debug statements** (warning)
4. ⚠️ **TODO/FIXME** (warning)
5. ⚠️ **Large files** (warning)

**Bypass (not recommended):**
```bash
git commit --no-verify
```

**Documentation:** `.githooks/README.md`

## Development Workflow

### Before Committing

1. **Format code:**
   ```bash
   ./gradlew ktlintFormat
   ```

2. **Run static analysis:**
   ```bash
   ./gradlew detekt
   ```

3. **Run tests:**
   ```bash
   ./gradlew test
   ```

4. **Check coverage (optional):**
   ```bash
   ./gradlew testDebugUnitTestCoverage
   ```

5. **Build:**
   ```bash
   ./gradlew build
   ```

### Pull Request Checklist

Use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`) which includes:

- [ ] Code follows conventions
- [ ] ktlint and detekt pass
- [ ] Tests added/updated
- [ ] All tests pass
- [ ] Documentation updated
- [ ] No security issues
- [ ] Performance impact considered

## Code Style Guidelines

### Kotlin

**Based on:** [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

**Key Points:**
- Use 4 spaces for indentation
- Max line length: 120 characters
- Use trailing commas in multi-line declarations
- Prefer expression bodies for simple functions
- Use meaningful names (no abbreviations)
- Document public APIs with KDoc

**Example:**
```kotlin
/**
 * Fetches memes from the database with pagination.
 */
fun getMemes(
    query: String,
    limit: Int = 20,
): Flow<PagingData<Meme>> = repository
    .searchMemes(query)
    .cachedIn(viewModelScope)
```

### Jetpack Compose

**See:** `.github/instructions/compose.instructions.md`

**Key Points:**
- Stateless composables with hoisted state
- Use `remember` for computation
- `LaunchedEffect` for side effects
- Proper lifecycle awareness
- Descriptive content descriptions (accessibility)

### Architecture

**Pattern:** Clean Architecture + MVI

**Layers:**
1. **Presentation** (feature modules)
   - Composables
   - ViewModels
   - UI State/Intent

2. **Domain** (core/model)
   - Use Cases
   - Domain Models
   - Repository Interfaces

3. **Data** (core/database, core/datastore)
   - Repository Implementations
   - Data Sources

**Rules:**
- Feature modules depend on core modules only
- No feature-to-feature dependencies
- UI State must be immutable
- Use Cases contain business logic

## IDE Setup

### Android Studio / IntelliJ IDEA

**Recommended Settings:**

1. **Install Plugins:**
   - Detekt Plugin
   - Save Actions Plugin (auto-format on save)

2. **Configure Auto-Format:**
   - Settings → Tools → Actions on Save
   - Enable: "Reformat code", "Optimize imports"

3. **Import Code Style:**
   - The project's `.editorconfig` is automatically detected
   - Settings → Editor → Code Style → Import Scheme → EditorConfig

4. **Enable Git Hooks:**
   - Hooks automatically run if installed via `install-hooks.ps1`

### VS Code

**Required Extensions:**
- Kotlin Language
- EditorConfig for VS Code

**Settings:**
```json
{
  "editor.formatOnSave": true,
  "editor.rulers": [120],
  "files.insertFinalNewline": true,
  "files.trimTrailingWhitespace": true
}
```

## Metrics & Reporting

### Build Reports

After running checks, reports are generated:

**Location:**
```
build/reports/
├── detekt/
│   ├── detekt.html        # Detekt findings
│   └── detekt.sarif       # SARIF for GitHub
├── ktlint/
│   ├── ktlintMainSourceSetCheck.txt
│   └── ktlintMainSourceSetCheck.xml
├── jacoco/
│   └── testDebugUnitTestCoverage/
│       └── html/index.html  # Coverage report
└── tests/
    └── testDebugUnitTest/   # Test results
```

### GitHub Code Scanning

detekt results are uploaded to GitHub Security tab via SARIF files.

**View Results:**
- GitHub → Security tab → Code scanning alerts
- Shows file, line, rule, and description

### PR Comments

The PR Checks workflow automatically comments:
- APK size report
- Test results
- Coverage changes (when configured)

## Troubleshooting

### ktlint Failures

**Issue:** ktlint finds formatting issues

**Solution:**
```bash
./gradlew ktlintFormat
git add .
```

### detekt Failures

**Issue:** detekt finds code smells or issues

**Solution:**
1. Review the report: `build/reports/detekt/detekt.html`
2. Fix the issues manually
3. If a rule doesn't apply, suppress it:
   ```kotlin
   @Suppress("MagicNumber")
   val timeout = 5000
   ```

**Creating Baseline:**
If many existing issues, create a baseline:
```bash
./gradlew detektBaseline
```
This creates `detekt-baseline.xml` that ignores existing issues.

### Coverage Below Threshold

**Issue:** Coverage verification fails

**Solution:**
1. Add missing tests for uncovered code
2. Check exclusions in `AndroidJacocoConventionPlugin.kt`
3. Adjust threshold if needed (carefully)

### Slow Pre-commit Hook

**Issue:** Pre-commit hook takes too long

**Solution:**
1. Run checks manually before committing: `./gradlew ktlintFormat detekt`
2. Bypass hook occasionally: `git commit --no-verify`
3. Modify hook to check only staged files

### CI Failures

**Issue:** CI passes locally but fails in GitHub Actions

**Possible Causes:**
- Different Gradle cache
- Missing dependencies
- Environment differences

**Solution:**
```bash
# Clean build locally
./gradlew clean
./gradlew build --no-daemon

# Check CI logs for specific error
```

## Resources

**Documentation:**
- [detekt Rules](https://detekt.dev/docs/rules/rules)
- [ktlint Rules](https://pinterest.github.io/ktlint/rules/standard/)
- [Kotlin Style Guide](https://kotlinlang.org/docs/coding-conventions.html)
- [JaCoCo Documentation](https://www.jacoco.org/jacoco/trunk/doc/)

**Project Docs:**
- `.editorconfig` - Code formatting rules
- `detekt.yml` - Static analysis configuration
- `buildSrc/README.md` - Convention plugins guide
- `.githooks/README.md` - Git hooks documentation
- `.github/PULL_REQUEST_TEMPLATE.md` - PR checklist

## Continuous Improvement

Code quality is an ongoing effort. We regularly:

1. **Update tools** - Keep detekt, ktlint, and Gradle up to date
2. **Review rules** - Adjust detekt.yml based on team feedback
3. **Monitor metrics** - Track coverage trends over time
4. **Refactor** - Address technical debt identified by tools
5. **Learn** - Share findings and best practices with the team

**Contributing:**
- Suggest rule changes via issues
- Propose new checks via PRs
- Report false positives in detekt/ktlint
- Share useful patterns and practices

---

**Last Updated:** January 28, 2026

For questions or suggestions, please open an issue.
