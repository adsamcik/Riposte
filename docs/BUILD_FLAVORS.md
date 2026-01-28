# Android Build Flavors

Meme My Mood uses product flavors to optimize APK size by targeting specific device architectures (ABIs).

## Available Flavors

The app has the following architecture-specific flavors in the `abi` dimension:

| Flavor | Architecture | Target Devices | APK Size Reduction |
|--------|--------------|----------------|-------------------|
| `universal` | All ABIs | All Android devices | None (default) |
| `arm64` | arm64-v8a | Modern ARM devices (2014+) | ~60-70% smaller |
| `arm` | armeabi-v7a | Older 32-bit ARM devices | ~60-70% smaller |
| `x86_64` | x86_64 | 64-bit x86 emulators/tablets | ~60-70% smaller |
| `x86` | x86 | 32-bit x86 emulators/tablets | ~60-70% smaller |

## Why Use Architecture Flavors?

The app includes native libraries for machine learning (LiteRT, MediaPipe, AI Edge RAG SDK, DJL tokenizers). By default, all architectures are bundled, resulting in large APKs. Architecture-specific builds:

- **Reduce APK size by 60-70%** by including only one architecture's native libraries
- **Faster downloads** for end users
- **Less storage** required on device
- **Optimal for direct APK distribution** (sideloading, F-Droid, etc.)

## Building for Specific Architectures

### Development Builds (Universal)

For development and testing, use the universal flavor to support all architectures:

```bash
# Debug build with all architectures
.\gradlew assembleUniversalDebug

# Release build with all architectures
.\gradlew assembleUniversalRelease
```

### Production Builds (Architecture-Specific)

For production distribution, build architecture-specific APKs:

```bash
# ARM 64-bit (most modern devices)
.\gradlew assembleArm64Release

# ARM 32-bit (older devices)
.\gradlew assembleArmRelease

# x86 64-bit (tablets, some Chromebooks)
.\gradlew assembleX86_64Release

# x86 32-bit (older emulators)
.\gradlew assembleX86Release

# Build all variants
.\gradlew assembleRelease
```

### Google Play Store

For Google Play Store distribution, use **Android App Bundle** with the universal flavor:

```bash
# Create App Bundle (recommended for Play Store)
.\gradlew bundleUniversalRelease
```

Google Play automatically delivers the correct architecture to each device from the App Bundle.

## Build Output Locations

Built APKs are located in:
```
app/build/outputs/apk/{flavor}/{buildType}/app-{flavor}-{buildType}.apk
```

Examples:
- `app/build/outputs/apk/arm64/release/app-arm64-release.apk`
- `app/build/outputs/apk/universal/debug/app-universal-debug.apk`

## Recommended Distribution Strategy

| Distribution Method | Recommended Flavor |
|---------------------|-------------------|
| **Google Play Store** | `universal` (as App Bundle) |
| **GitHub Releases** | Multiple APKs (`arm64`, `arm`, `x86_64`) |
| **F-Droid** | `universal` or multiple APKs |
| **Direct Download** | `arm64` (covers 95%+ devices) |
| **Testing/QA** | `universal` |

## Device Architecture Lookup

To determine which APK a user needs:

1. **Most modern devices (2014+)**: Use `arm64`
2. **Older devices (2012-2014)**: Use `arm`
3. **Tablets/Chromebooks**: Usually `x86_64` or `arm64`
4. **Emulators**: Check emulator settings (usually `x86_64`)

Users can check their device architecture:
- Install **CPU-Z** or **DevCheck** from Play Store
- Look for "ABI" or "Supported ABIs"
- Primary ABI is listed first

## Size Comparison

Example APK sizes (estimated):

- **Universal**: ~50-80 MB
- **arm64**: ~15-25 MB
- **arm**: ~15-25 MB
- **x86_64**: ~15-25 MB
- **x86**: ~15-25 MB

*Actual sizes depend on code, resources, and ML models included.*

## Development Workflow

### Day-to-Day Development
```bash
# Quick debug build (fastest)
.\gradlew assembleUniversalDebug
```

### Pre-Release Testing
```bash
# Build and test primary architecture
.\gradlew assembleArm64Debug
.\gradlew connectedArm64DebugAndroidTest
```

### Release Process
```bash
# Build all release variants
.\gradlew assembleRelease

# Create App Bundle for Play Store
.\gradlew bundleUniversalRelease
```

## Troubleshooting

### Build Errors with Flavors

If you encounter native library errors:
```bash
# Clean and rebuild
.\gradlew clean
.\gradlew assembleArm64Debug
```

### Wrong Architecture Installed

If the app crashes with "UnsatisfiedLinkError":
- The installed APK doesn't match your device's architecture
- Install the correct architecture-specific APK
- Or use the universal build

### Testing Multiple Architectures

To test different architectures:
1. Use different emulators (x86_64, arm64)
2. Or install architecture-specific APKs on physical devices
3. Use `adb install -r app-{flavor}-debug.apk`

## Further Reading

- [Android ABI Management](https://developer.android.com/ndk/guides/abis)
- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [Reducing APK Size](https://developer.android.com/topic/performance/reduce-apk-size)
