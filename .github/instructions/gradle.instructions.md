---
description: 'Gradle build configuration guidelines for the Meme My Mood Android project'
applyTo: '**/*.gradle.kts,**/gradle.properties,**/libs.versions.toml,**/settings.gradle.kts'
---

# Gradle Build Configuration Guidelines

## Version Catalogs

This project uses Gradle Version Catalogs (`gradle/libs.versions.toml`) for dependency management.

### Adding Dependencies
1. Add version to `[versions]` section
2. Add library to `[libraries]` section
3. Add plugin to `[plugins]` section if needed
4. Use in build files with `libs.` prefix

```toml
[versions]
coil = "3.3.0"

[libraries]
coil-compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil" }

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
```

### Usage in Build Files
```kotlin
dependencies {
    implementation(libs.coil.compose)
}

plugins {
    alias(libs.plugins.kotlin.android)
}
```

## Module Structure

### App Module (`app/build.gradle.kts`)
- Apply Android application plugin
- Configure application ID, versions, signing
- Include all feature modules

### Feature Modules (`feature/*/build.gradle.kts`)
- Apply Android library plugin
- Depend on required core modules
- Keep dependencies minimal

### Core Modules (`core/*/build.gradle.kts`)
- Apply Android library plugin
- Expose only necessary APIs
- Use `api` for transitive dependencies

## Common Configuration

### Kotlin Options
```kotlin
compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    freeCompilerArgs.addAll(
        "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-opt-in=kotlinx.coroutines.FlowPreview"
    )
}
```

### Compose Configuration
```kotlin
composeCompiler {
    enableStrongSkippingMode = true
    enableIntrinsicRemember = true
    stabilityConfigurationFile = rootProject.file("compose_stability_config.conf")
}
```

### Build Types
- `debug`: Debuggable, applicationIdSuffix ".debug"
- `release`: Minified, obfuscated, signed

## Dependency Configuration

| Configuration | Use For |
|---------------|---------|
| `implementation` | Internal dependencies |
| `api` | Dependencies exposed to consumers |
| `ksp` | Annotation processors (Hilt, Room) |
| `testImplementation` | Unit test dependencies |
| `androidTestImplementation` | Instrumentation test dependencies |

## Performance

### Build Speed
- Enable Gradle configuration cache
- Use Gradle build cache
- Parallelize module builds
- Use KSP instead of KAPT

### gradle.properties
```properties
org.gradle.jvmargs=-Xmx4g -XX:+HeapDumpOnOutOfMemoryError
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
```

## Avoid
- Don't use `+` in version numbers
- Don't apply plugins conditionally
- Don't use `allprojects` for dependencies
- Don't hardcode SDK versions (use version catalogs)
