# Android Build Flavors

Meme My Mood uses product flavors to optimize APK size by targeting specific embedding models and device architectures.

## Overview

The app has **two flavor dimensions**:

1. **`embedding`** - Controls which AI/ML models are included (~0-1200 MB impact)
2. **`abi`** - Controls which native libraries are included (~5-10% impact)

These combine to create variants like `standardArm64Debug` or `liteUniversalRelease`.

## Embedding Model Flavors

The app uses EmbeddingGemma models for semantic search. These models are large (~170-180 MB each), and the app has 7 total models (generic + 6 SOC-specific). This dimension lets you choose which models to include.

| Flavor | Models Included | Size | Use Case |
|--------|----------------|------|----------|
| `lite` | None (MediaPipe USE fallback) | ~0 MB | Minimal size, basic search |
| `standard` | Generic model only | ~175 MB | **Recommended** - works on all devices |
| `qualcomm` | Generic + Qualcomm (4 models) | ~860 MB | Optimized for Snapdragon 8 Gen 2+ |
| `mediatek` | Generic + MediaTek (2 models) | ~535 MB | Optimized for Dimensity 9300/9400 |
| `full` | All models (7 total) | ~1.2 GB | Development/testing only |

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

## ABI (Architecture) Flavors

Controls which CPU architectures' native libraries are included (for ML Kit, LiteRT, MediaPipe, DJL).

| Flavor | Architecture | Devices | Native Lib Reduction |
|--------|--------------|---------|---------------------|
| `universal` | All ABIs | All devices | None (default) |
| `arm64` | arm64-v8a | Modern ARM (2014+) | ~5-10% smaller |
| `arm` | armeabi-v7a | Older 32-bit ARM | ~5-10% smaller |
| `x86_64` | x86_64 | 64-bit emulators/tablets | ~5-10% smaller |
| `x86` | x86 | 32-bit emulators | ~5-10% smaller |

## Build Examples

### Development Builds

```bash
# Fastest build - standard model, all architectures
.\gradlew assembleStandardUniversalDebug

# Smallest build - no models, arm64 only
.\gradlew assembleLiteArm64Debug

# Qualcomm-optimized, arm64 only
.\gradlew assembleQualcommArm64Debug
```

### Release Builds

```bash
# Recommended for most users - standard model, all architectures
.\gradlew assembleStandardUniversalRelease

# Recommended for direct APK distribution - standard, arm64
.\gradlew assembleStandardArm64Release

# Qualcomm flagship devices - optimized models, arm64
.\gradlew assembleQualcommArm64Release

# MediaTek flagship devices - optimized models, arm64
.\gradlew assembleMediatekArm64Release

# Development/testing with all models
.\gradlew assembleFullUniversalRelease
```

### Google Play Store

For Play Store, use App Bundle with **standard** + **universal**:

```bash
.\gradlew bundleStandardUniversalRelease
```

Google Play automatically delivers the correct architecture to each device.

## APK Size Comparison

| Variant | Model Size | Native Libs | Total APK (est.) |
|---------|-----------|-------------|------------------|
| `liteArm64` | ~0 MB | Single ABI | ~15-20 MB |
| `standardArm64` | ~175 MB | Single ABI | ~190-210 MB |
| `standardUniversal` | ~175 MB | All ABIs | ~200-220 MB |
| `qualcommArm64` | ~860 MB | Single ABI | ~880-900 MB |
| `mediatekArm64` | ~535 MB | Single ABI | ~555-575 MB |
| `fullUniversal` | ~1.2 GB | All ABIs | ~1.25-1.3 GB |

*Sizes include app code, resources, and base dependencies (~20-25 MB)*

## Recommended Distribution Strategy

| Distribution Method | Recommended Variant |
|---------------------|-------------------|
| **Google Play Store** | `standardUniversal` (as App Bundle) |
| **GitHub Releases** | `standardArm64` + `liteArm64` |
| **F-Droid** | `standardUniversal` |
| **Direct Download** | `standardArm64` (covers 95%+ devices) |
| **Minimal Size** | `liteArm64` |
| **Flagship Devices** | `qualcommArm64` or `mediatekArm64` |
| **Testing/QA** | `fullUniversal` |

## Device Compatibility

### Which Embedding Flavor?

**Standard (recommended for most):**
- Unknown device/chipset
- Maximum compatibility
- Reasonable size (~200 MB)

**Lite:**
- Storage-constrained devices
- Users who don't need semantic search
- Smallest possible APK

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

### Which ABI Flavor?

- **Most modern devices (2014+)**: `arm64`
- **Older devices (2012-2014)**: `arm`
- **Tablets/Chromebooks**: usually `x86_64` or `arm64`
- **Emulators**: check settings (usually `x86_64`)
- **Universal**: when unsure, or for App Bundle

## Build Output Locations

```
app/build/outputs/apk/{embedding}{abi}/{buildType}/
```

Examples:
- `app/build/outputs/apk/standardArm64/release/app-standard-arm64-release.apk`
- `app/build/outputs/apk/liteUniversal/debug/app-lite-universal-debug.apk`
- `app/build/outputs/apk/qualcommArm64/release/app-qualcomm-arm64-release.apk`

## Development Workflow

### Day-to-Day Development
```bash
# Quick debug build (fastest compile)
.\gradlew assembleStandardUniversalDebug

# Or even faster with lite
.\gradlew assembleLiteUniversalDebug
```

### Testing Semantic Search
```bash
# Test with standard model
.\gradlew assembleStandardArm64Debug
adb install -r app/build/outputs/apk/standardArm64/debug/app-standard-arm64-debug.apk

# Test with Qualcomm optimizations
.\gradlew assembleQualcommArm64Debug
```

### Release Process
```bash
# Build recommended variants
.\gradlew assembleStandardArm64Release
.\gradlew assembleLiteArm64Release

# Create App Bundle for Play Store
.\gradlew bundleStandardUniversalRelease
```

## Troubleshooting

### "Model file not found" error

The app is trying to load a SOC-specific model that isn't included in your flavor:
- **Solution**: Use `standard`, `qualcomm`, `mediatek`, or `full` flavor
- Or the app will automatically fall back to generic model or MediaPipe

### APK size too large

- Use `lite` flavor (no models) - ~20 MB
- Use `standard` instead of `qualcomm`/`mediatek`/`full`
- Use architecture-specific ABI (`arm64`) instead of `universal`

### Slow semantic search

Your flavor might not have the optimized model for your device:
- Qualcomm devices: use `qualcomm` flavor
- MediaTek devices: use `mediatek` flavor
- Or the app is using generic model - still fast, just not optimized

### Build errors with flavors

```bash
# Clean and rebuild
.\gradlew clean
.\gradlew assembleStandardArm64Debug
```

## View All Build Tasks

```bash
# List all available assembly tasks
.\gradlew tasks --group=build | Select-String "assemble"

# Shows tasks like:
# assembleStandardArm64Debug
# assembleLiteUniversalRelease
# assembleQualcommArm64Release
# etc.
```

## Further Reading

- [Android ABI Management](https://developer.android.com/ndk/guides/abis)
- [Android App Bundle](https://developer.android.com/guide/app-bundle)
- [Reducing APK Size](https://developer.android.com/topic/performance/reduce-apk-size)
