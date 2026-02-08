# Android Build Flavors

Riposte uses product flavors to optimize APK size by controlling which embedding models are included.

## Overview

The app has **one flavor dimension**: **`embedding`** - Controls which AI/ML models are included (0-1200 MB impact)

All flavors build **universal** (all CPU architectures) for maximum compatibility. This simplifies distribution while the embedding model selection provides the meaningful size optimization.

## Embedding Model Flavors

| Flavor | Models Included | APK Size | Use Case |
|--------|----------------|----------|----------|
| `lite` | None (MediaPipe USE fallback) | ~265 MB | Minimal size, basic search |
| `standard` | Generic model only | ~440 MB | **Recommended** - works on all devices |
| `qualcomm` | Generic + Qualcomm (4 models) | ~1.05 GB | Optimized for Snapdragon 8 Gen 2+ |
| `mediatek` | Generic + MediaTek (2 models) | ~720 MB | Optimized for Dimensity 9300/9400 |
| `full` | All models (7 total) | ~1.45 GB | Development/testing only |

*Debug build sizes. Release builds are ~40-50% smaller with minification and resource shrinking.*

### Model Details

**Generic Model** (`standard` flavor):
- File: `embeddinggemma-300M_seq512_mixed-precision.tflite` (~171 MB)
- Works on all Android devices
- GPU-accelerated with CPU fallback
- Inference time: ~150-200ms on mid-range devices

**Qualcomm Models** (`qualcomm` flavor):
- Snapdragon 8 Gen 2 (sm8550) - ~181 MB
- Snapdragon 8 Gen 3 (sm8650) - ~181 MB  
- Snapdragon 8 Gen 4 Elite (sm8750) - ~176 MB
- Snapdragon 8 Gen 5 (sm8850) - ~176 MB
- Optimized for Qualcomm AI Engine
- Inference time: ~85-120ms on flagship

**MediaTek Models** (`mediatek` flavor):
- Dimensity 9300 (mt6991) - ~179 MB
- Dimensity 9400 (mt6993) - ~175 MB
- Optimized for MediaTek APU
- Inference time: ~100-140ms on flagship

### Runtime Behavior

The app automatically detects the device SOC at runtime and loads the best available model:
1. If SOC-specific model exists → use it (fastest)
2. Else if generic model exists → use it (compatible)
3. Else fall back to MediaPipe USE or simple embeddings

## Why Universal (All Architectures)?

All flavors include native libraries for all CPU architectures (arm64-v8a, armeabi-v7a, x86_64, x86):

- **Maximum compatibility**: Works on all Android devices
- **Simpler distribution**: One APK per embedding flavor
- **Google Play optimization**: Play Store automatically delivers the right architecture from App Bundles
- **Modest size impact**: Native libs add ~80-90 MB compared to arm64-only
- **Real optimization is models**: Embedding models (0-1200 MB) dwarf native lib sizes

If you need absolute minimum size for direct APK distribution, you can manually configure ABI filters in `build.gradle.kts`.

## Build Examples

### Development Builds

```bash
# Fastest build - standard model (recommended)
.\gradlew assembleStandardDebug

# Smallest build - no models
.\gradlew assembleLiteDebug

# Qualcomm-optimized
.\gradlew assembleQualcommDebug
```

### Release Builds

```bash
# Recommended for most users - standard model
.\gradlew assembleStandardRelease

# Qualcomm flagship devices
.\gradlew assembleQualcommRelease

# MediaTek flagship devices
.\gradlew assembleMediatekRelease

# Development/testing with all models
.\gradlew assembleFullRelease
```

### Google Play Store

For Play Store, use App Bundle with **standard** flavor:

```bash
.\gradlew bundleStandardRelease
```

Google Play automatically delivers the correct architecture to each device from the App Bundle.

## APK Size Comparison

| Variant | APK Size (Debug) | Use Case |
|---------|------------------|----------|
| `liteDebug` | ~265 MB | Minimal size |
| `standardDebug` | ~440 MB | **Recommended** |
| `qualcommDebug` | ~1.05 GB | Qualcomm devices |
| `mediatekDebug` | ~720 MB | MediaTek devices |
| `fullDebug` | ~1.45 GB | Testing |

*Release builds are significantly smaller with ProGuard/R8 and resource shrinking.*

## Recommended Distribution Strategy

| Distribution Method | Recommended Variant |
|---------------------|-------------------|
| **Google Play Store** | `standard` (as App Bundle) |
| **GitHub Releases** | `standard` + `lite` |
| **F-Droid** | `standard` |
| **Direct Download** | `standard` |
| **Minimal Size** | `lite` |
| **Flagship Devices** | `qualcomm` or `mediatek` |
| **Testing/QA** | `full` |

## Device Compatibility

### Which Embedding Flavor?

**Standard (recommended for most):**
- Unknown device/chipset
- Maximum compatibility
- Good balance of size and performance (~440 MB)

**Lite:**
- Storage-constrained devices
- Users who don't need semantic search
- Smallest possible APK (~265 MB)

**Qualcomm:**
- Samsung Galaxy S23/S24/S25
- OnePlus 11/12/13
- Xiaomi 13/14/15
- Google Pixel 7/8/9

**MediaTek:**
- Vivo X100/X200
- OPPO Find X7/X8
- Realme GT series
- Devices with Dimensity 9300/9400

## Build Output Locations

```
app/build/outputs/apk/{embedding}/{buildType}/app-{embedding}-{buildType}.apk
```

Examples:
- `app/build/outputs/apk/standard/release/app-standard-release.apk`
- `app/build/outputs/apk/lite/debug/app-lite-debug.apk`
- `app/build/outputs/apk/qualcomm/release/app-qualcomm-release.apk`

## Development Workflow

### Day-to-Day Development
```bash
# Quick debug build (default is standard)
.\gradlew assembleDebug

# Or explicitly
.\gradlew assembleStandardDebug
```

### Testing Semantic Search
```bash
# Test with standard model
.\gradlew assembleStandardDebug
adb install -r app/build/outputs/apk/standard/debug/app-standard-debug.apk

# Test with Qualcomm optimizations
.\gradlew assembleQualcommDebug
```

### Release Process
```bash
# Build recommended variants
.\gradlew assembleStandardRelease
.\gradlew assembleLiteRelease

# Create App Bundle for Play Store
.\gradlew bundleStandardRelease
```

## Troubleshooting

### "Model file not found" error

The app is trying to load a SOC-specific model that isn't included in your flavor:
- **Solution**: Use `standard`, `qualcomm`, `mediatek`, or `full` flavor
- The app will automatically fall back to generic model or MediaPipe

### APK size too large

- Use `lite` flavor (no models) - ~265 MB
- Use `standard` instead of `qualcomm`/`mediatek`/`full`
- Release builds are ~40-50% smaller than debug

### Slow semantic search

Your flavor might not have the optimized model for your device:
- Qualcomm devices: use `qualcomm` flavor
- MediaTek devices: use `mediatek` flavor
- Or the app is using generic model - still fast, just not optimized

### Build errors

```bash
# Clean and rebuild
.\gradlew clean
.\gradlew assembleStandardDebug
```

## View All Build Tasks

```bash
# List all available assembly tasks
.\gradlew tasks --group=build | Select-String "assemble"

# Shows tasks like:
# assembleStandardDebug
# assembleLiteRelease
# assembleQualcommRelease
# etc.
```

## Further Reading

- [Android ABI Management](https://developer.android.com/ndk/guides/abis)
- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [Reducing APK Size](https://developer.android.com/topic/performance/reduce-apk-size)
