# Riposte 🎭

A modern Android app for organizing, searching, and sharing memes with emoji-based categorization and AI-powered search.

## Features

- 🖼️ **Image Gallery**: Beautiful grid view of your meme collection
- 🏷️ **Emoji Tags**: Categorize memes with emojis for quick identification
- 🔍 **Smart Search**: Full-text and semantic AI-powered search
- 📤 **Easy Sharing**: Share memes with customizable format, size, and quality
- 📥 **Quick Import**: Import images with automatic emoji suggestions
- 🎨 **Material 3**: Modern design with dynamic colors support

## Tech Stack

This project follows the 2026 Android best practices:

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose (BOM 2025.12.00) |
| Architecture | Clean Architecture + MVI |
| DI | Hilt 2.58 |
| Database | Room 2.8.4 + FTS4 |
| Async | Coroutines 1.10.1 & Flow |
| AI | ML Kit + LiteRT (on-device) |
| Image Loading | Coil 3.3.0 |
| Build | Gradle Version Catalogs |

## Project Structure

```
riposte/
├── app/                    # Main application module
├── core/
│   ├── common/            # Shared utilities & extensions
│   ├── database/          # Room database & DAOs
│   ├── datastore/         # Preferences storage
│   ├── ml/                # ML Kit & TensorFlow integration
│   ├── model/             # Domain models
│   └── ui/                # Design system & components
├── feature/
│   ├── gallery/           # Meme gallery feature
│   ├── import/            # Image import feature
│   ├── search/            # Search feature
│   ├── share/             # Sharing feature
│   └── settings/          # Settings feature
└── docs/                  # Documentation
```

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35

### Build

```bash
# Clone the repository
git clone https://github.com/yourusername/riposte.git

# Open in Android Studio and sync
# Or build from command line:
./gradlew assembleDebug
```

#### Build Flavors

The app uses **product flavors** to control which embedding models are included:

| Flavor | Models | APK Size | Use Case |
|--------|--------|----------|----------|
| `lite` | None | ~177 MB | Minimal size, basic search |
| `standard` | Generic only | ~350 MB | **Recommended** - works everywhere |
| `qualcomm` | Generic + Qualcomm | ~880 MB | Optimized for Snapdragon |
| `mediatek` | Generic + MediaTek | ~555 MB | Optimized for Dimensity |
| `full` | All 7 models | ~1.3 GB | Development/testing |

All flavors build universal (all architectures) for maximum compatibility.

```bash
# Quick development build (recommended)
./gradlew assembleStandardDebug

# Smallest build
./gradlew assembleLiteDebug

# Optimized for Qualcomm devices
./gradlew assembleQualcommRelease
```

See [BUILD_FLAVORS.md](docs/BUILD_FLAVORS.md) for complete details.

### Run

1. Open the project in Android Studio
2. Connect an Android device or start an emulator
3. Click Run (▶️)

## Architecture

The app follows **Clean Architecture** with **MVI** (Model-View-Intent) pattern:

```
┌─────────────────────────────────────────────┐
│                Presentation                  │
│  ┌─────────┐  ┌───────────┐  ┌──────────┐  │
│  │ Screen  │←─│ ViewModel │←─│ UiState  │  │
│  │(Compose)│  │   (MVI)   │  │ + Intent │  │
│  └────┬────┘  └─────┬─────┘  └──────────┘  │
│       │             │                       │
├───────┼─────────────┼───────────────────────┤
│       │      Domain │                       │
│       │   ┌─────────┴────────┐              │
│       │   │    Use Cases     │              │
│       │   └─────────┬────────┘              │
│       │             │                       │
├───────┼─────────────┼───────────────────────┤
│       │        Data │                       │
│       │   ┌─────────┴────────┐              │
│       │   │   Repositories   │              │
│       │   └─────────┬────────┘              │
│       │             │                       │
│       │   ┌─────────┴────────┐              │
│       │   │ DAOs / DataStore │              │
│       │   └──────────────────┘              │
└─────────────────────────────────────────────┘
```

## Key Features

### Emoji Metadata Format

Memes are tagged with emojis using embedded XMP metadata. This allows:
- Self-describing images that can be shared
- Automatic emoji extraction from shared images
- Searchable emoji tags

See [METADATA_FORMAT.md](docs/METADATA_FORMAT.md) for the full specification.

### Search

The app supports three types of search:

1. **Full-Text Search (FTS4)**: Fast text matching on titles, descriptions, and extracted text
2. **Emoji Search**: Filter by emoji tags
3. **Semantic Search**: AI-powered similarity search using embeddings

### Share Configuration

Users can customize how memes are shared:

| Option | Values |
|--------|--------|
| Format | JPEG, PNG, WebP, GIF |
| Quality | 0-100% |
| Max Size | 480p - Original |
| Metadata | Keep / Strip |

## Dependencies

All dependencies are managed via [gradle/libs.versions.toml](gradle/libs.versions.toml).

Key dependencies:
- Jetpack Compose BOM 2025.12.00
- Hilt 2.58
- Room 2.8.4
- Coil 3.3.0
- ML Kit Text Recognition 16.0.1
- LiteRT 1.4.1 (on-device AI)

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Unicode Emoji](https://unicode.org/emoji/) for emoji data
- [Google ML Kit](https://developers.google.com/ml-kit) for on-device AI
- [Material Design 3](https://m3.material.io/) for design guidelines
