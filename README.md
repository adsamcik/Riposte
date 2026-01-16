# Meme My Mood 🎭

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
| Language | Kotlin 2.1 |
| UI | Jetpack Compose |
| Architecture | Clean Architecture + MVI |
| DI | Hilt |
| Database | Room + FTS4 |
| Async | Coroutines & Flow |
| AI | ML Kit + TensorFlow Lite |
| Build | Gradle Version Catalogs |

## Project Structure

```
meme-my-mood/
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
git clone https://github.com/yourusername/meme-my-mood.git

# Open in Android Studio and sync
# Or build from command line:
./gradlew assembleDebug
```

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
| Watermark | On / Off |

## Dependencies

All dependencies are managed via [gradle/libs.versions.toml](gradle/libs.versions.toml).

Key dependencies:
- Jetpack Compose BOM 2025.01.00
- Hilt 2.53
- Room 2.7.0
- Coil 2.7.0
- ML Kit Text Recognition 16.1.0
- TensorFlow Lite 2.16.1

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
