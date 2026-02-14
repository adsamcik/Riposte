# Riposte CLI

A command-line tool for annotating meme images using the GitHub Copilot SDK. Generates metadata files compatible with the [Riposte](https://github.com/riposte/riposte) Android app.

## Features

- 🤖 **AI-Powered Annotation**: Uses GitHub Copilot's vision capabilities to analyze memes
- 😀 **Emoji Suggestions**: Automatically suggests relevant emoji tags based on content
- 📝 **Text Extraction**: Extracts visible text from memes
- � **Smart Deduplication**: SHA-256 + perceptual hashing to skip duplicate images
- ⚡ **Parallel Processing**: Concurrent API requests with adaptive backpressure
- �📦 **ZIP Bundling**: Package images + metadata for easy import to the Android app
- 🔐 **Native Auth**: Uses Copilot CLI authentication (no separate login required)

## Prerequisites

1. **GitHub Copilot CLI** - Required for SDK communication
   - Install: <https://docs.github.com/en/copilot/how-tos/set-up/install-copilot-cli>
   - Verify: `copilot --version`

2. **Copilot Authentication**

   ```bash
   copilot auth login
   ```

3. **Active GitHub Copilot Subscription**
   - Free tier includes limited usage
   - See: <https://github.com/features/copilot#pricing>

## Installation

### From source (recommended)

```bash
cd tools/riposte-cli

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
cd tools/riposte-cli
.\scripts\setup.ps1
```

### Quick setup (macOS/Linux)

```bash
cd tools/riposte-cli
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
meme-cli annotate ./my-memes --model gpt-5-mini
```

### 3. Import to Android App

Transfer the generated `.meme.zip` file to your Android device and open it with Riposte.

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
  --zip                    Bundle images and sidecars into a .meme.zip file
  --output, -o PATH        Output directory for sidecar files (default: same as input)
  --model, -m TEXT         Model to use for analysis (default: gpt-5-mini)
  --languages, -l TEXT     Comma-separated BCP 47 language codes (default: en)
  --concurrency, -j N      Max parallel API requests (1-10, default: 4)
  --force, -f              Force regeneration of all sidecars, overwriting existing
  --continue               Only process images without existing JSON sidecars
  --add-new                Alias for --continue
  --no-dedup               Disable duplicate detection (skip hash-based deduplication)
  --similarity-threshold N Max Hamming distance for near-duplicates (default: 10)
  --dry-run                Show what would be processed without making changes
  --verbose, -v            Show detailed progress information
```

#### Processing Modes

| Mode        | Flag               | Behavior                                           |
| ----------- | ------------------ | -------------------------------------------------- |
| **Default** | (none)             | Skip images that already have sidecar files        |
| **Force**   | `--force` / `-f`   | Regenerate all sidecars, overwriting existing ones |
| **Continue**| `--continue`       | Explicitly skip images with existing sidecars      |
| **Add New** | `--add-new`        | Alias for `--continue`                             |

#### Deduplication

By default, the CLI uses content-based deduplication to avoid processing duplicate images:

- **SHA-256 hashes** detect exact duplicates (identical files)
- **Perceptual hashes (pHash)** detect near-duplicates (same image with different compression)
- A hash manifest (`.meme-hashes.json`) is saved for faster subsequent runs

| Option                       | Description                                                                           |
| ---------------------------- | ------------------------------------------------------------------------------------- |
| `--no-dedup`                 | Disable all deduplication checks                                                      |
| `--similarity-threshold N`   | Adjust perceptual hash sensitivity (0-256, default: 10). Lower = stricter matching.   |

The `contentHash` field (SHA-256) is included in each sidecar file for Android app deduplication.

**Examples:**

```bash
# Default: only process new images (skip existing sidecars)
meme-cli annotate ./my-memes

# Force regenerate all metadata
meme-cli annotate ./my-memes --force

# Resume processing after interruption
meme-cli annotate ./my-memes --continue

# Preview what would be processed
meme-cli annotate ./my-memes --dry-run

# Disable near-duplicate detection (only exact matches)
meme-cli annotate ./my-memes --similarity-threshold 0

# Disable all deduplication
meme-cli annotate ./my-memes --no-dedup
```

## Supported Image Formats

| Format    | Extensions          | Notes                         |
| --------- | ------------------- | ----------------------------- |
| JPEG      | `.jpg`, `.jpeg`     | Full support                  |
| PNG       | `.png`              | Full support                  |
| WebP      | `.webp`             | Full support                  |
| GIF       | `.gif`              | First frame analyzed          |
| BMP       | `.bmp`              | Full support                  |
| TIFF      | `.tiff`, `.tif`     | Full support                  |
| HEIC/HEIF | `.heic`, `.heif`    | Requires `pillow-heif`        |
| AVIF      | `.avif`             | Requires `pillow-avif-plugin` |
| JPEG XL   | `.jxl`              | Requires `pillow-jxl-plugin`  |

Unsupported formats are skipped with a warning.

## Output Format

For each image, a JSON sidecar file is created:

```text
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
  "basedOn": "Programmer humor",
  "textContent": "When the code works but you don't know why",
  "createdAt": "2026-01-25T12:00:00Z",
  "appVersion": "cli-1.0.0"
}
```

## Rate Limiting & Parallelism

The CLI processes multiple images concurrently (default: 4 workers) with intelligent rate limit handling:

- **Shared client**: A single Copilot SDK connection is reused across all workers, eliminating per-image startup overhead
- **Adaptive concurrency**: Starts at `--concurrency N` workers; automatically reduces on 429 errors and gradually restores on sustained success
- **Global pause on 429**: When any worker hits a rate limit, ALL workers pause until the backoff period elapses — prevents wasting quota
- **Per-image retries**: Each image gets up to 5 retry attempts independently; one image's failures don't affect another's retry budget
- **Exponential backoff**: Automatically increases wait time after repeated errors
- **Retry-After support**: Respects server-specified wait times
- **Jitter**: Random variation to prevent thundering herd problems
- **Graceful Ctrl+C**: Pressing Ctrl+C cancels pending work but lets in-flight requests finish. Completed sidecars are preserved on disk. Run again with `--continue` to process the rest

```bash
# Default: 4 parallel workers
meme-cli annotate ./my-memes

# Sequential processing (like old behavior)
meme-cli annotate ./my-memes -j 1

# Maximum parallelism
meme-cli annotate ./my-memes -j 10

# Resume after interruption
meme-cli annotate ./my-memes --continue
```

You don't need to configure anything beyond `--concurrency` — backpressure is handled automatically.

## How It Works

The CLI uses the GitHub Copilot SDK which communicates with the Copilot CLI via JSON-RPC:

```text
meme-cli → Copilot SDK → Copilot CLI (server mode)
```

Authentication, model management, and API calls are all handled by the Copilot CLI.

## Development

```bash
# Clone the repository
git clone https://github.com/riposte/riposte
cd riposte/tools/riposte-cli

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
