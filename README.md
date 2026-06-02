# Riposte 🎭

A modern Android app for organizing, searching, and sharing memes with emoji-based categorization and AI-powered semantic search. Find the perfect meme and share it in under 10 seconds.

## Features

- 🖼️ **Image Gallery**: Grid view with pagination, multi-select, and swipe-through detail view
- 🏷️ **Emoji Tags**: Categorize and filter memes with single-tap emoji chips
- 🔍 **Smart Search**: Full-text, emoji filtering, and semantic AI-powered search with inline quick-filters
- 📤 **Native Sharing**: Share memes directly via the Android share sheet
- 📥 **Quick Import**: Import images or `.meme.zip` bundles with automatic emoji suggestions
- 📊 **Fun Statistics**: Milestones, vibe check, and sparkline charts for your collection
- 🎨 **M3 Expressive**: Material 3 Expressive design with dynamic colors, spring animations, and squircle shapes
- 🌍 **Multilingual**: English, Czech, German, Spanish, and Portuguese

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.3.0 |
| UI | Jetpack Compose (BOM 2025.12.00) with Material 3 |
| Architecture | Clean Architecture + MVI |
| DI | Hilt 2.59.1 |
| Database | Room 2.8.4 + FTS4 |
| Async | Coroutines 1.10.2 & Flow |
| AI/ML | [Mindlayer SDK](https://github.com/adsamcik/Mindlayer) (on-device LLM service: embeddings + OCR) |
| Image Loading | Coil 3.3.0 |
| Serialization | Kotlinx Serialization 1.10.0 |
| Build | Gradle 8.13.2, AGP 9.0.1, Version Catalogs |

## Project Structure

```
riposte/
├── app/                    # Main application, wires modules together
├── core/
│   ├── common/            # Shared utilities, navigation routes, extensions
│   ├── database/          # Room database, DAOs, entities, migrations
│   ├── datastore/         # DataStore preferences
│   ├── ml/                # Mindlayer SDK integration, semantic search, embeddings
│   ├── model/             # Domain models
│   ├── search/            # Search logic (FTS + semantic hybrid)
│   ├── testing/           # Test utilities, fakes, rules
│   └── ui/                # Design system, theme, reusable components
├── feature/
│   ├── gallery/           # Meme gallery, detail view, favorites, search
│   ├── import/            # Image & ZIP bundle import
│   ├── share/             # Sharing feature
│   └── settings/          # App preferences, statistics, licenses
├── tools/
│   ├── riposte-cli-dotnet/  # .NET 8 CLI for batch AI annotation
│   └── riposte-cli/         # Legacy Python CLI
└── docs/                  # Documentation
```

## Getting Started

### Prerequisites

- Android Studio Meerkat (2025.1.1) or later
- JDK 17
- Android SDK 36

### Build

```bash
# Clone the repository
git clone https://github.com/adsamcik/riposte.git

# Build the debug APK
./gradlew :app:assembleDebug
```

#### Mindlayer SDK dependency

Riposte's AI features (semantic search embeddings, OCR) delegate to the
[Mindlayer](https://github.com/adsamcik/Mindlayer) on-device LLM service via
the Mindlayer SDK. The SDK is consumed from local Maven; build it once with:

```bash
# In a Mindlayer checkout:
./gradlew :shared:publishToMavenLocal :sdk:publishToMavenLocal
```

The Mindlayer **service app** must also be installed and approved on the
target device for AI features to work. When the service is unavailable
(not installed, not yet approved, or its embedding pack is still
downloading), Riposte degrades gracefully — text/emoji search continues
to work; semantic search and OCR are disabled until the service is
ready.

### Run

1. Open the project in Android Studio
2. Connect an Android device (min SDK 31) or start an emulator
3. Select the `standard` build variant
4. Click Run (▶️)

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
- Self-describing images that carry their tags when shared
- Automatic emoji extraction from received images
- Searchable emoji tags

See [METADATA_FORMAT.md](docs/METADATA_FORMAT.md) for the full specification.

### Search

The app supports three search modes with inline emoji quick-filters:

1. **Full-Text Search (FTS4)**: Fast text matching on titles, descriptions, and extracted text
2. **Emoji Filtering**: Single-tap emoji chips for instant filtering
3. **Semantic Search**: AI-powered similarity search using on-device EmbeddingGemma embeddings delivered via the Mindlayer service

Favorited memes are prioritized in search results. See [SEMANTIC_SEARCH.md](docs/SEMANTIC_SEARCH.md) for implementation details.

### Sharing

Memes are shared directly via the native Android share sheet. Share format, quality, and size preferences are configured in Settings.

## CLI Tool

The .NET 8 CLI at `tools/riposte-cli-dotnet/` batch-annotates meme images with AI using the GitHub Copilot SDK:

```bash
# Install globally
dotnet tool install -g RiposteCli

# Annotate a directory of images
riposte-cli annotate ./memes --zip --languages en,cs

# See all options
riposte-cli annotate --help
```

- Requires `copilot auth login` first
- Outputs JSON sidecar files per image (metadata schema v1.3)
- Creates importable `.meme.zip` bundles with `--zip`
- Parallel processing with adaptive rate limiting and exponential backoff

## Development

```bash
# Run all unit tests
./gradlew test

# Lint
./gradlew lint

# Static analysis
./gradlew detekt

# Format code
./gradlew ktlintFormat
```

All dependencies are managed via [gradle/libs.versions.toml](gradle/libs.versions.toml).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the GNU General Public License v3.0 — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Unicode Emoji](https://unicode.org/emoji/) for emoji data
- [Mindlayer](https://github.com/adsamcik/Mindlayer) for on-device LLM, embeddings, and OCR inference
- [Material Design 3](https://m3.material.io/) for design guidelines
