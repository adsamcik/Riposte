# Meme My Mood CLI

A command-line tool for annotating meme images using the GitHub Copilot SDK. Generates metadata files compatible with the [Meme My Mood](https://github.com/meme-my-mood/meme-my-mood) Android app.

## Features

- 🤖 **AI-Powered Annotation**: Uses GitHub Copilot's vision capabilities to analyze memes
- 😀 **Emoji Suggestions**: Automatically suggests relevant emoji tags based on content
- 📝 **Text Extraction**: Extracts visible text from memes
- 📦 **ZIP Bundling**: Package images + metadata for easy import to the Android app
- 🔐 **Native Auth**: Uses Copilot CLI authentication (no separate login required)

## Prerequisites

1. **GitHub Copilot CLI** - Required for SDK communication
   - Install: https://docs.github.com/en/copilot/how-tos/set-up/install-copilot-cli
   - Verify: `copilot --version`

2. **Copilot Authentication**
   ```bash
   copilot auth login
   ```

3. **Active GitHub Copilot Subscription**
   - Free tier includes limited usage
   - See: https://github.com/features/copilot#pricing

## Installation

### From source (recommended)

```bash
cd tools/meme-my-mood-cli

# Create and activate virtual environment
python -m venv .venv

# Windows
.venv\Scripts\activate

# macOS/Linux
source .venv/bin/activate

# Install dependencies
pip install -e .
```

### Quick setup (Windows)

```bash
cd tools/meme-my-mood-cli
.\scripts\setup.ps1
```

### Quick setup (macOS/Linux)

```bash
cd tools/meme-my-mood-cli
./scripts/setup.sh
```

## Quick Start

### 1. Check Prerequisites

```bash
meme-cli check
```

This verifies Copilot CLI is installed and authenticated.

### 2. Annotate Images

```bash
# Annotate all images in a folder
meme-cli annotate ./my-memes

# Annotate and create a ZIP bundle
meme-cli annotate ./my-memes --zip

# Specify output directory for sidecars
meme-cli annotate ./my-memes --output ./annotated

# Use a specific model
meme-cli annotate ./my-memes --model gpt-4o
```

### 3. Import to Android App

Transfer the generated `.meme.zip` file to your Android device and open it with Meme My Mood.

## Commands

### `meme-cli check`

Verify Copilot CLI is installed and authenticated.

```bash
meme-cli check
```

### `meme-cli annotate`

Annotate images with AI-generated metadata.

```bash
meme-cli annotate <folder> [OPTIONS]

Options:
  --zip              Bundle images and sidecars into a .meme.zip file
  --output, -o PATH  Output directory for sidecar files (default: same as input)
  --model, -m TEXT   Model to use for analysis (default: gpt-4o)
  --dry-run          Show what would be processed without making changes
  --verbose, -v      Show detailed progress information
```

## Supported Image Formats

| Format | Extensions | Notes |
|--------|------------|-------|
| JPEG | `.jpg`, `.jpeg` | Full support |
| PNG | `.png` | Full support |
| WebP | `.webp` | Full support |
| GIF | `.gif` | First frame analyzed |
| BMP | `.bmp` | Full support |
| TIFF | `.tiff`, `.tif` | Full support |
| HEIC/HEIF | `.heic`, `.heif` | Requires `pillow-heif` |
| AVIF | `.avif` | Requires `pillow-avif-plugin` |
| JPEG XL | `.jxl` | Requires `pillow-jxl-plugin` |

Unsupported formats are skipped with a warning.

## Output Format

For each image, a JSON sidecar file is created:

```
my-memes/
├── funny-cat.jpg
├── funny-cat.jpg.json    # ← Generated sidecar
├── programming-meme.png
└── programming-meme.png.json
```

### Sidecar JSON Schema

```json
{
  "schemaVersion": "1.0",
  "emojis": ["😂", "🐱"],
  "title": "Funny cat meme",
  "description": "A cat looking confused at a computer screen",
  "tags": ["cat", "funny", "confused"],
  "textContent": "When the code works but you don't know why",
  "createdAt": "2026-01-25T12:00:00Z",
  "appVersion": "cli-1.0.0"
}
```

## Rate Limiting

The SDK handles rate limiting automatically through the Copilot CLI backend.

## How It Works

The CLI uses the GitHub Copilot SDK which communicates with the Copilot CLI via JSON-RPC:

```
meme-cli → Copilot SDK → Copilot CLI (server mode)
```

Authentication, model management, and API calls are all handled by the Copilot CLI.

## Development

```bash
# Clone the repository
git clone https://github.com/meme-my-mood/meme-my-mood
cd meme-my-mood/tools/meme-my-mood-cli

# Create and activate virtual environment
python -m venv .venv
.venv\Scripts\activate  # Windows
source .venv/bin/activate  # macOS/Linux

# Install in development mode with dev dependencies
pip install -e ".[dev]"

# Run tests
pytest

# Lint code
ruff check src/
```

## License

MIT License - see [LICENSE](../../LICENSE) for details.
