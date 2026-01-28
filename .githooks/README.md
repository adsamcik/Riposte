# Git Hooks

This directory contains Git hooks for automating code quality checks.

## Available Hooks

### pre-commit
Runs before each commit to ensure code quality:

**Checks Performed:**
- ✅ ktlint code formatting
- ✅ detekt static analysis
- ⚠️  Debug statements (println, Log.d, etc.)
- ⚠️  TODO/FIXME comments
- ⚠️  Large files (>500KB)

## Installation

### Automatic Installation

**Windows (PowerShell):**
```powershell
.\.githooks\install-hooks.ps1
```

**Linux/macOS:**
```bash
chmod +x .githooks/install-hooks.sh
./.githooks/install-hooks.sh
```

### Manual Installation

Copy hooks to `.git/hooks/` directory:
```bash
cp .githooks/pre-commit .git/hooks/pre-commit
chmod +x .git/hooks/pre-commit
```

Or configure Git to use this directory:
```bash
git config core.hooksPath .githooks
```

## Usage

Once installed, the pre-commit hook runs automatically before each commit.

### Bypassing Hooks

**Not recommended**, but you can skip hooks with:
```bash
git commit --no-verify
# or
git commit -n
```

### Running Checks Manually

You can run the checks manually without committing:

**Windows:**
```powershell
.\.githooks\pre-commit.ps1
```

**Linux/macOS:**
```bash
./.githooks/pre-commit
```

## Hook Behavior

### ktlint Formatting
- Automatically formats staged Kotlin files
- Re-stages formatted files
- Fails commit if formatting errors occur

### detekt Static Analysis
- Runs detekt on the entire project
- Fails commit if detekt finds issues
- Check `.detekt.yml` for configuration

### Warning Checks
- Debug statements and TODO comments trigger warnings but don't fail the commit
- Large files trigger warnings for review

## Troubleshooting

### Permission Denied (Linux/macOS)
Make the hook executable:
```bash
chmod +x .githooks/pre-commit
chmod +x .githooks/install-hooks.sh
```

### Hook Not Running
Verify installation:
```bash
# Check configured hooks path
git config core.hooksPath

# Or check if hook exists
ls -la .git/hooks/pre-commit
```

### Slow Commits
Pre-commit hooks can slow down commits, especially on large projects. Options:
1. Use `--no-verify` for quick commits (not recommended)
2. Run checks manually before committing: `./gradlew ktlintFormat detekt`
3. Adjust hook script to run only on changed files

### Windows Line Endings
If you see `^M` errors on Linux/macOS, fix line endings:
```bash
git config core.autocrlf input
```

## Customization

Edit `.githooks/pre-commit` or `.githooks/pre-commit.ps1` to:
- Add new checks
- Modify failure conditions
- Change warning thresholds
- Skip specific checks

Example - disable detekt in pre-commit:
```bash
# Comment out the detekt section in .githooks/pre-commit
# print_info "Running detekt static analysis..."
# if ./gradlew detekt --quiet; then
#     ...
# fi
```

## Best Practices

1. **Keep hooks fast**: Slow hooks frustrate developers
2. **Auto-fix when possible**: Format code automatically instead of just checking
3. **Warn, don't fail**: Use warnings for non-critical issues
4. **Make hooks bypassable**: Allow `--no-verify` for emergencies
5. **Document everything**: Clear error messages and documentation

## CI/CD Integration

Git hooks run locally, but similar checks run in CI:
- `.github/workflows/ci.yml` - Build and test
- `.github/workflows/code-quality.yml` - ktlint and detekt

This ensures code quality even if developers bypass hooks.
