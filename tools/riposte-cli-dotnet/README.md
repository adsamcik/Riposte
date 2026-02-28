# Riposte CLI (.NET)

A command-line tool for annotating meme images using the GitHub Copilot SDK. Generates metadata files compatible with the [Riposte](https://github.com/riposte/riposte) Android app.

This is a .NET 8 rewrite of the original Python CLI, offering single-executable distribution and `dotnet tool` installation.

## Features

- 🤖 **AI-Powered Annotation** — Uses GitHub Copilot's vision capabilities to analyze memes
- 😀 **Emoji Suggestions** — Automatically suggests relevant emoji tags based on content
- 📝 **Text Extraction** — Extracts visible text from memes
- 🔍 **Smart Deduplication** — SHA-256 + perceptual hashing to skip duplicate images
- 🧹 **Duplicate Cleanup** — Find and remove duplicate images
- ⚡ **Parallel Processing** — Concurrent API requests with adaptive backpressure
- 📦 **ZIP Bundling** — Package images + metadata for easy import to the Android app
- 🔐 **Native Auth** — Uses Copilot CLI authentication

## Prerequisites

1. **GitHub Copilot CLI** installed and in PATH
2. **Copilot Authentication**: `copilot auth login`
3. **Active GitHub Copilot Subscription**
4. **.NET 8 SDK** (for building from source)

## Installation

### As a .NET global tool

```bash
dotnet tool install -g RiposteCli
```

### From source

```bash
cd tools/riposte-cli-dotnet
dotnet build
dotnet run --project src/RiposteCli -- annotate ./memes
```

### Single-file executable

```bash
cd tools/riposte-cli-dotnet
dotnet publish src/RiposteCli -c Release -r win-x64 --self-contained -p:PublishSingleFile=true
# Output: src/RiposteCli/bin/Release/net8.0/win-x64/publish/meme-cli.exe
```

## Quick Start

```bash
# Check prerequisites
meme-cli auth status

# Annotate all images in a folder
meme-cli annotate ./my-memes

# Annotate and create a ZIP bundle
meme-cli annotate ./my-memes --zip

# Find and remove duplicates
meme-cli dedupe ./my-memes --dry-run
```

## Commands

### `meme-cli annotate <folder>`

Annotate images with AI-generated metadata.

| Option | Description |
|--------|-------------|
| `--zip [mode]` | Create ZIP bundle: `full` (all images, default) or `patch` (only changed/new) |
| `--output, -o` | Output directory for sidecar files |
| `--model, -m` | Model to use (default: `gpt-5-mini`) |
| `--languages, -l` | Comma-separated BCP 47 language codes (default: `en`) |
| `--concurrency, -j` | Max parallel API requests, 1-50 (default: `4`) |
| `--force, -f` | Regenerate all sidecars |
| `--continue` | Only process images without existing sidecars |
| `--add-new` | Alias for `--continue` |
| `--no-dedup` | Disable duplicate detection |
| `--similarity-threshold` | Max Hamming distance for near-duplicates (default: `10`) |
| `--dry-run` | Show what would be processed |
| `--verbose, -v` | Show detailed progress |

### `meme-cli dedupe <folder>`

Find and remove duplicate images.

| Option | Description |
|--------|-------------|
| `--output, -o` | Directory where sidecar files are stored |
| `--similarity-threshold` | Max Hamming distance (default: `10`) |
| `--no-near` | Only remove exact duplicates |
| `--dry-run` | Preview without deleting |
| `--yes, -y` | Skip confirmation prompt |
| `--verbose, -v` | Detailed output |

### `meme-cli auth status|check`

Verify Copilot CLI installation and connectivity.

## Rate Limiting & Parallelism

The CLI processes multiple images concurrently with intelligent rate limit handling:

- **Shared client** — Single Copilot SDK connection reused across workers
- **Adaptive concurrency** — Reduces on 429 errors, restores on success
- **Global pause on 429** — All workers pause until backoff elapses
- **Per-image retries** — Up to 5 attempts per image independently
- **Exponential backoff with jitter** — Prevents thundering herd

## Development

```bash
cd tools/riposte-cli-dotnet

# Build
dotnet build

# Run tests
dotnet test

# Run
dotnet run --project src/RiposteCli -- annotate ./memes
```

## Migration from Python CLI

This is a feature-complete rewrite of the Python `riposte-cli`. Key differences:

- **Distribution**: `dotnet tool install` or single `.exe` instead of venv/pip
- **Perceptual hashing**: Uses `CoenM.ImageSharp.ImageHash` — hash values differ from Python `imagehash`, so existing `.meme-hashes.json` manifests will be regenerated
- **JSON sidecar format**: Identical schema (v1.3), fully compatible

## License

MIT License — see [LICENSE](../../LICENSE) for details.
