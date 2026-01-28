# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Gallery pagination support for large meme collections
- Database migration testing

### Changed

- Improved FTS query sanitization for security

### Fixed

- ZIP Slip path traversal vulnerability in bundle import
- FTS query injection in search functionality
- Rate limiter double-counting in CLI tool

### Security

- Added network security configuration to block cleartext traffic
- Improved input sanitization for FTS queries

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

### CLI Tool (meme-my-mood-cli)

- Python CLI for batch annotating meme images with AI
- GitHub Copilot SDK integration for image analysis
- Multilingual support with `--languages` option
- ZIP bundle creation for easy import into app
- Adaptive rate limiting with exponential backoff
- Schema v1.1 with localization support

[Unreleased]: https://github.com/yourusername/meme-my-mood/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/yourusername/meme-my-mood/releases/tag/v0.1.0
