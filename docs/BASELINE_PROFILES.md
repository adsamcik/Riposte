# Baseline Profiles Guide

This document explains how to generate and use baseline profiles for optimizing app startup and runtime performance.

## What are Baseline Profiles?

Baseline Profiles are a list of classes and methods that are pre-compiled during app installation, improving:
- **Startup time**: Up to 40% faster cold start
- **Runtime performance**: Reduced jank and improved frame rates
- **Battery life**: Less work for the JIT compiler

## Current Setup

The project already has baseline profile infrastructure in place:

| Component | Location |
|-----------|----------|
| Generator Test | `baselineprofile/src/main/kotlin/.../BaselineProfileGenerator.kt` |
| Profile Output | `app/src/main/baseline-prof.txt` |
| Build Plugin | `libs.plugins.baselineprofile` |
| Profile Installer | `libs.profile.installer` |

## Generating Baseline Profiles

### Prerequisites

1. **Physical device or emulator**: API 30+ with `root` or `userdebug` build
2. **Connected via ADB**: Run `adb devices` to verify
3. **Release variant**: Profiles are generated against release builds

### Generate Profile

Run from project root:

```powershell
# Windows PowerShell
.\gradlew :app:generateBaselineProfile

# Or generate with connected device benchmarks
.\gradlew :baselineprofile:connectedBenchmarkAndroidTest `
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

```bash
# macOS/Linux
./gradlew :app:generateBaselineProfile

# Or generate with connected device benchmarks
./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
```

### Output Location

The generated profile is automatically placed at:
```
app/src/main/baseline-prof.txt
```

## Build Configuration

The `app/build.gradle.kts` is configured with:

```kotlin
baselineProfile {
    // Don't auto-generate during every build (generate manually)
    automaticGenerationDuringBuild = false
    
    // Enable DEX layout optimization
    dexLayoutOptimization = true
}
```

## Customizing the Profile Generator

Edit `BaselineProfileGenerator.kt` to add critical user journeys:

```kotlin
rule.collect(
    packageName = "com.mememymood",
    includeInStartupProfile = true
) {
    // App startup
    startActivityAndWait()
    
    // Add your critical paths:
    // - Navigate to frequently used screens
    // - Perform common actions
    // - Scroll through lists
    // - Open dialogs
}
```

### Current Coverage

The generator already covers:
- ✅ App startup
- ✅ Gallery grid loading
- ✅ Scrolling through memes
- ✅ Navigation to search

Consider adding:
- Import flow
- Share flow
- Settings navigation
- Meme detail view

## Verification

### Check Profile Size

```powershell
Get-Content app\src\main\baseline-prof.txt | Measure-Object -Line
```

A good profile typically has 1,000-10,000 lines.

### Benchmark Comparison

Run benchmarks to compare with/without profiles:

```powershell
.\gradlew :baselineprofile:connectedBenchmarkAndroidTest
```

## CI/CD Integration

For automated generation in CI:

```yaml
# GitHub Actions example
- name: Generate Baseline Profile
  run: ./gradlew :app:generateBaselineProfile
  
- name: Commit Profile
  uses: stefanzweifel/git-auto-commit-action@v5
  with:
    commit_message: "chore: update baseline profile"
    file_pattern: "app/src/main/baseline-prof.txt"
```

## Troubleshooting

### "No connected devices"

```powershell
# Check ADB connection
adb devices

# Restart ADB if needed
adb kill-server
adb start-server
```

### "Rooted device required"

Use an emulator with a `userdebug` system image, not a production build.

### Profile not improving performance

1. Ensure `profile-installer` dependency is included
2. Verify profile is in `src/main/baseline-prof.txt`
3. Build a release APK and install fresh
4. Wait for profile to be compiled (can take a few minutes after install)

## Further Reading

- [Android Baseline Profiles Documentation](https://developer.android.com/topic/performance/baselineprofiles)
- [Macrobenchmark Library](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)
