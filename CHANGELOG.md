# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.2.0] - 2026-02-11

### Added

- Swipe between memes in detail view with HorizontalPager
- Similar Memes discovery on meme detail screen
- Gallery pagination for large meme collections (1000+ memes)
- Semantic search with multi-vector embeddings (content + intent slots)
- Embedding model info and search index statistics in Settings
- Embedding model error display when AI model is unavailable
- Background import processing with WorkManager
- Quick Share redesign with categorized app grid and clipboard support
- Multi-select mode with bottom action bar in gallery
- Crash diagnostics section in Settings
- Import status banner in gallery
- Loading states for import, search, and share flows
- Schema v1.3 with `basedOn` and `searchPhrases` fields
- Database schema version validation for releases
- Czech, German, Spanish, and Portuguese localizations
- Release workflow and F-Droid CI pipeline

### Changed

- Search merged into gallery with SearchDelegate pattern
- Gallery emoji bar now search-only mode with cleaner grid and tighter spacing
- Gallery reduces columns during search mode for readability
- Replaced all hardcoded colors and shapes with theme tokens
- Simplified technical jargon in Settings labels
- Share export controls moved to Settings
- DCT-based perceptual hash in CLI tool (improved accuracy)
- Renamed package to `com.adsamcik.riposte`
- Renamed CLI tool to `riposte-cli`
- Applied ktlint formatting across all modules

### Fixed

- Metadata preservation through import pipeline
- Missing accessibility content descriptions and touch targets below 48dp
- Share crash when no sharing apps installed
- Black screen after import navigation
- Detail screen stacking when navigating similar memes
- WorkManager stale state on import screen re-entry
- Settings export/import dialog cancel button consistency
- MemeDetail share button emphasis, back button contrast, save loading spinner, and delete dialog context
- Rate limiter double-counting in CLI tool

### Security

- ZIP Slip path traversal vulnerability in bundle import
- FTS query injection in search functionality
- Network security configuration blocks cleartext HTTP traffic

## [0.1.0] - 2026-01-28

### Features

- **Gallery Feature**: Beautiful grid view of meme collection with favorites support
- **Emoji Tagging**: Categorize memes with emojis using XMP metadata embedding
- **Smart Search**: Full-text search (FTS4) with hybrid semantic search using embeddings
- **Semantic Search**: AI-powered similarity search using MediaPipe/EmbeddingGemma
- **Image Import**: Import images with automatic emoji suggestions via ML Kit
- **ZIP Bundle Import**: Import `.meme.zip` bundles created by CLI tool with pre-generated metadata
- **Share Feature**: Share memes with customizable format (JPEG/PNG/WebP), quality, and size
- **Settings**: Dark mode, semantic search toggle, sharing preferences
- **Material 3 Design**: Modern UI with dynamic colors and adaptive theming
- **Baseline Profiles**: Startup and runtime performance optimization
- **Type-safe Navigation**: Kotlin serialization-based navigation routes
- **WorkManager Integration**: Background embedding generation with battery-aware scheduling

### Technical

- Clean Architecture with MVI pattern across all features
- Modular project structure with core and feature modules
- Room database with FTS4 for efficient text search
- Hilt dependency injection with proper scoping
- Coroutines and Flow for async operations
- Coil 3 for efficient image loading
- Comprehensive test suite (~1,050 tests)

### CLI Tool (riposte-cli)

- Python CLI for batch annotating meme images with AI
- GitHub Copilot SDK integration for image analysis
- Multilingual support with `--languages` option
- ZIP bundle creation for easy import into app
- Adaptive rate limiting with exponential backoff
- Schema v1.1 with localization support

[Unreleased]: https://github.com/yourusername/riposte/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/yourusername/riposte/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/yourusername/riposte/releases/tag/v0.1.0
