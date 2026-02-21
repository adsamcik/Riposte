# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.3.1] - 2026-02-20

### Changed

- Simplified build flavors to lite, standard, and googleplay (removed qualcomm/mediatek/full)
- Google Play flavor uses AI Packs with device-targeted SoC-optimized model delivery
- Standard flavor bundles generic model only for F-Droid and sideload distribution
- Lite flavor is now the default for development (fastest builds, no models)
- CI and PR workflows use lite flavor for faster checks

## [0.3.0] - 2026-02-20

### Added

- Find Flow Fusion — inline emoji quick-filters in search bar with animated chip selection morph
- Fun statistics screen with milestones, vibe check, sparklines, and M3 Expressive design language
- Open source licenses screen in settings
- M3 Expressive design system: Digital Joy color palette, Inter font family, spring physics motion, expressive shapes, and hybrid dynamic color theme
- MaterialExpressiveTheme with squircle-to-circle emoji chip selection morph and emoji bounce animation
- Transparent top bar with content scrolling behind it and auto-hiding emoji rail on scroll
- Emoji usage sorting in filter rail (by share count) with settings toggle
- Tap emoji in meme detail to search gallery by that emoji
- Favorited memes prioritized in search results
- Search UX improvements: autocomplete suggestions, search duration display, actionable no-results state, history icons for recent searches
- Native Android share sheet replaces intermediate share screen
- Meme count display in settings
- Auto-resume incomplete embedding indexing on startup with background ML model warm-up
- Notification banner system in gallery (replaces welcome message)
- Text-only indicator when semantic search is unavailable
- Timber logging for all silent catch blocks and ZIP bundle extraction
- Accessibility improvements: gallery loading indicator labels, meme card emoji overlay labels, settings screen enhancements, selection state announcements
- .NET 8 rewrite of riposte-cli with parallel processing, adaptive 429 handling, dedupe command, and 1000+ image batch optimization
- Expanded test suite significantly (566+ CLI tests, comprehensive UI regression tests, duplicate detection tests, fun statistics tests, and more)

### Changed

- Emoji filter changed from multi-select to single-select
- Emoji taps routed through search bar for unified search experience
- Share flow uses native share sheet directly (removed ShareScreen and QuickShareBottomSheet)
- Default share format changed from WebP to JPEG
- Search relevance scoring switched from BM25 to field-based scoring for FTS4
- Emoji filtering moved from composition to SQL PagingSource for performance
- Gallery overflow menu simplified to Select and Settings
- Baseline profile expanded with search and share flows
- Upgraded to AGP 9.0.0, Gradle 9.3.1, Material 3 1.5.0-alpha13, MediaPipe 0.10.32, LiteRT 2.1.1
- Updated launcher icon and splash screen
- CLI default model updated to gpt-5-mini
- Replaced tween animations with M3 Expressive spring physics throughout gallery
- M3 Expressive button hierarchy: Share (filled) > Edit/Favorite (tonal) > Delete (error tonal)
- Dialog buttons follow M3 hierarchy (filled primary + outlined dismiss)
- Cards use tonal elevation instead of shadow elevation
- Spacing tokens applied consistently across shared components
- Emoji chip ripple effect properly clipped to morphed shape
- Unified gallery and search into single grid composable for seamless transitions
- Reduced detekt baseline from 533 to 109 issues (80% reduction)
- Removed 102 dead string resources across all locales

### Fixed

- ANR caused by infinite EmbeddingGenerationWorker scheduling loop
- Startup ANR resolved with lazy Hilt injection and deferred OpenCL availability check
- Import/indexing notifications reappearing on every startup
- Auto-dismiss import and embedding indexing completion notifications after 5 seconds
- Duplicate key crash in gallery LazyVerticalGrid
- Search results blinking when changing filters
- Selection mode crash caused by spring animation overshooting padding to negative values
- Gallery–search transition blink and emoji rail re-animation on mode switch
- Back button navigation: two-step exit from search mode, keyboard dismissal, edit mode exit from all entry points
- Gallery bottom row truncation and grid spacing issues
- Search placeholder cutoff and emoji rail overlap
- Duplicate detection reliability improved with original source byte hashing
- Worker batch continuation and startup race condition
- Import screen empty state layout and error display on image cards
- Detail view image scaling and description expand/collapse
- Progress bar visual artifacts and sparkline dot clipping
- Hardcoded colors replaced with Material 3 theme tokens in MemeDetailScreen
- Smart search settings now coordinate with model availability
- ZIP extract directory recreation after cleanup
- Search index double-counting memes
- OpenCL availability proactively detected before enabling GPU
- CLI ZIP bundling fix for Windows (writestr for OSError)
- Test compilation warnings treated as errors resolved

### Security

- Removed silent fallback from semantic search engine — errors surface immediately instead of degrading silently
- Embedding dimension validation added
- Foreign key constraint added to ImportRequestItemEntity

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

[Unreleased]: https://github.com/adsamcik/riposte/compare/v0.3.1...HEAD
[0.3.1]: https://github.com/adsamcik/riposte/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/adsamcik/riposte/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/adsamcik/riposte/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/adsamcik/riposte/releases/tag/v0.1.0
