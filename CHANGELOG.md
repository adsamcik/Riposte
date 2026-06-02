# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-06-02

### Added

- **Riposte as a system file source.** New `DocumentsProvider` exposes the meme library to the Android Storage Access Framework: tap "+" → Files in any app (Discord, Gmail, WhatsApp, etc.) and pick a meme directly from a Riposte folder. Browse by **All Memes**, **Favorites**, **Recently Used**, or **Emojis** (one subfolder per emoji that tags any meme, ordered by usage). Real downsized thumbnails via `BitmapFactory.inSampleSize`; Room-Flow-driven change notifications keep the picker in sync with imports / deletions / favorite toggles in real time.
- `:testapps:share-receiver` — in-project debug-only Android app that pretends to be every kind of share receiver we want to integration-test against (well-behaved, Discord-style buggy, slow, paranoid, multi-process). Backed by a `ShareTelemetryProvider` ContentProvider so integration tests assert on outcomes via cross-process queries instead of scraping logcat. Auto-installed before `connectedAndroidTest`.

### Fixed

- **Discord (and other React-Native-based share targets) no longer crash on meme shares.** Discord's `ShareActivity` calls `Context.grantUriPermission` to forward URIs to its upload workers, which Android's grant model forbids for non-owner re-grants of transient `FileProvider` URIs (every share threw `SecurityException` after 1-2 attempts). Switched the share path from `FileProvider` to MediaStore (`Pictures/.riposte-share/` with `IS_PENDING` lifecycle and cleanup-on-next-share + on app start) so receivers authorise the URI via their own `READ_MEDIA_IMAGES` permission — the crash class becomes structurally impossible.
- Floating toolbar in the gallery no longer taps through to underlying meme grid items.
- Search clear (×) button now actually clears the text and refocuses the field.
- All WorkManager workers implement `getForegroundInfo()` for Android 14+ foreground-service compliance.

### Improved

- All icon buttons in the gallery meet the 48dp minimum touch-target size required by WCAG.
- Share intents now carry `FLAG_GRANT_READ_URI_PERMISSION | FLAG_GRANT_WRITE_URI_PERMISSION | FLAG_GRANT_PERSISTABLE_URI_PERMISSION` (was READ only) so well-behaved receivers can persist or forward URIs without manual workarounds.
- Multi-share path refactored to go through `ShareRepository.prepareMultipleForSharing` with proper rollback if any meme fails to publish.

### Tests

- 18 new instrumentation tests against the new `:testapps:share-receiver` fixture:
  - 9 covering the MediaStore share path: HappyPath, WriteRequired, Persistable, ArrayListOnly multi-share, late-read survival, plus the headline `mediaStore_share_to_DiscordStyleActivity_does_not_crash` regression marker and two counterfactual tests that prove the original FileProvider behaviour still reproduces the crash (so any future "let's go back to FileProvider" temptation fails red).
  - 9 covering `DocumentsProvider`: root enumeration, folder structure, emoji subfolders, favorites filtering, search, document opening (byte equality), thumbnail downsizing.
- Unit tests added for 4 use cases and 1 repository.

## [0.4.3] - 2026-03-27

### Changed

- Upgraded AGP from 9.1.0-rc01 to 9.1.0

### Fixed

- Embedding pipeline: stop re-processing memes with legitimately fewer embedding types on every foreground resume (attempt tracking with persistent counter)
- Semantic search: invalidate candidate cache when new embeddings are generated, so fresh memes appear in results immediately
- Embedding statistics: use single SQL UNION query for accurate meme count instead of memory-heavy approach

### Improved

- Expanded test suite: 14 regression tests for embedding pipeline attempt tracking, search cache invalidation pipeline tests, ML test alignment

### CLI

- **Incremental manifest saves** — build manifest saved after each image instead of only at end, preventing data loss on interruption
- **Model refusal handling** — detect content policy refusals with clear warning message; retry up to 5 times with linear backoff for spurious false positives
- **Global crash reporting** — error harnesses for TaskScheduler, AppDomain, and ProcessExit ensure crashes always produce output
- **Timestamps** — all per-image processing lines include HH:mm:ss prefix for log correlation
- **Dedupe default change** — exact-only duplicate cleanup is now the default; near-duplicate cleanup opt-in via `--include-near`
- **Legacy schema detection** — sidecars with outdated schema version now trigger rebuild automatically
- Atomic ZIP bundle creation via temp file + rename (preserves previous bundle on failure)
- ConcurrentBag replaces manual locking for processed/error collections
- Async SemaphoreSlim replaces blocking lock for manifest I/O
- CancellationToken threaded through entire pipeline for graceful Ctrl+C
- Per-image error handling in batch optimization (one corrupt image no longer aborts the batch)
- Fixed DisposeAsync masking original exceptions in error paths
- Fixed concurrent temp file collisions with GUID-based filenames
- Pipeline refactored from 550-line method into focused sub-methods

## [0.4.2] - 2026-03-07

### Fixed

- ML inference: detect GPU NaN output and automatically fall back to CPU accelerator
- ML inference: fix `normalize()` not catching NaN norm values (IEEE 754 edge case)
- Embeddings: auto-detect and purge NaN-corrupted embeddings on app startup
- Semantic search: add emoji to scoring slots so unannotated memes participate in search
- Accessibility: add contentDescription to Import FAB (was invisible to TalkBack)
- Navigation: back press in edit mode now exits to view mode instead of leaving MemeDetail
- Build: resolve AAB asset conflict between base and generic_embedding AI Pack
- Logging: add release-build Logcat tree for ML pipeline failure visibility

## [0.4.1] - 2026-02-28

### Added

- Gallery: Sort by newest during import for stable scrolling
- Import: Hardened cloud URI import with cleanup and progress tracking
- Settings: Richer AI search indexing status display

### Changed

- Upgraded Compose BOM to 2026.02.01
- Moved sentencepiece.model to flavor-specific source sets
- Reduced embedding worker CPU pressure for smoother device experience

### Fixed

- FTS4 search: replaced FTS5-only `bm25()` with FTS4-compatible query in search DAO
- Emoji search: use unquoted column filters in FTS4 queries
- Emoji import: normalize variation selectors for consistent FTS search
- Embedding worker: checks model availability before processing, fails fast on model error
- Embedding worker: removed self-cancelling continuation loop, improved notification messaging
- Embedding indexing: continuous indexing with adaptive batching and inter-batch yields
- Embedding model: resolved DISPATCH_OP failure and silent init errors
- Snackbar respects navigation bar insets on all screens
- Progress banner debounced to prevent flickering
- Google Play flavor: bundled generic model for sideload/fallback
- Resolved 22 chaos QA bugs across 6 root cause clusters

### Improved

- Expanded test suite: comprehensive migration tests for schema versions 6→7 and 7→8

### CLI

- **Smart rebuild system** — incremental annotation with build manifest tracking, skips unchanged images
- **Image optimization** — automatic image optimization during annotation pipeline
- **ZIP bundle modes** — full and patch bundle generation
- **Output subdirectory layout** — organized output into `sidecars/`, `optimized/`, `bundle/` with legacy migration
- **Legacy manifest seeding** — bootstrap rebuild manifests from existing sidecar files
- **Rebuild reason diagnostics** — detailed reporting on why images are re-annotated
- **Detect and strip removed schema fields** from existing sidecar files
- **Track optimization config** in build manifest for change detection
- Fixed BasedOn silent data loss in partial merge
- Fixed case-insensitive filename lookups + atomic sidecar writes
- Fixed ZIP entry collision when images share stem but differ in extension
- Fixed Spectre.Console markup injection via dynamic content escaping
- Fixed silent failures during annotation
- Fixed atomic manifest save for concurrency safety
- Added OnPermissionRequest handler for Copilot SDK update
- Removed broken legacy seeding implementation
- 600+ new tests across smart rebuild, schema validation, bundling, and concurrency

## [0.4.0] - 2026-02-27

### Added

- Emotion-based semantic search — memes are now indexed with structured emotion metadata (sentiment, core emotions, usage context) for much better results on abstract queries like "funny", "sad", or "happy"
- Persistent query embedding cache — search query embeddings survive app restarts, making repeated searches instant
- Gallery: Extended FAB with "Import" label for first-time discoverability
- Gallery: Select All / Deselect All toggle in selection mode
- Gallery: End-of-results hint below sparse search results
- Import screen: Supported formats note (JPEG, PNG, WebP, GIF)
- Duplicates screen: Sensitivity level descriptions below slider
- Search results header now shows query text (e.g., "2 results for 'cat'")

### Changed

- Removed legacy MediaPipe model support — only EmbeddingGemma (768-dimensional) is supported going forward
- On model upgrade, outdated embeddings are deleted and regenerated from scratch instead of attempting backwards-compatible migration
- Removed mediapipe-tasks-text dependency (smaller APK)
- Metadata schema bumped to v1.4 with emotion taxonomy fields

### Fixed

- MemeDetail: landscape images now center vertically instead of showing black void
- MemeDetail: back button has shadow for visibility on bright images
- MemeDetail: unified action button styling (Share uses IconButton with primaryContainer background)
- Delete dialog: neutral gray Cancel button for clearer contrast against red Delete
- Emoji filter toggle: re-tapping active chip now properly clears the filter
- MemeEdit: neutral Discard button color for clearer Save/Discard hierarchy
- Settings: search index info uses onSurfaceVariant instead of error-red
- Share sheet: image preview now appears via ClipData
- Gallery selection mode: Share/Delete buttons show selection count
- FTS migration correctly rebuilds virtual table after adding emotionsJson column

### Improved

- Expanded test suite: model upgrade/delete flow tests, embedding version manager tests, adversarial DAO tests

## [0.3.4] - 2026-02-22

### Added

- Undo button after deleting a meme — tap to restore within 5 seconds
- Search emoji by name when editing tags (type "fire" to find 🔥, "sad" to find 😢)
- "Import Memes" button shown when search finds no results
- Embedding progress now shows how many memes are processed out of total

### Changed

- Phone vibrates when you favorite a meme
- Clearing search history now asks for confirmation first
- Updated launcher icons

### Fixed

- Search errors show a friendly message instead of technical gibberish
- Stalled import message now translates properly in all languages
- GIFs are shared as-is instead of being re-encoded
- Favoriting a meme while scrolling no longer occasionally misses the count update
- Adding an emoji to a meme while quickly tapping no longer drops the edit
- Deleting a meme cleans up reliably even if the file is already gone
- Smart search no longer returns inflated relevance scores
- Emoji search works correctly in right-to-left languages
- Several memory leaks fixed during meme import
- Fixed rare crashes from concurrent background operations

### Security

- Meme bundle import now rejects ZIP bombs (2 GB extraction limit)
- Each import extracts into its own isolated folder
- Smart search validates embedding dimensions before comparing

### Accessibility

- TalkBack announces empty state icons, emoji chips, and import thumbnails ("Image 1 of 5")
- Import progress updates are announced automatically by screen readers
- Emoji chip text follows your system font size preference

## [0.3.3] - 2026-02-21

### Changed

- Improved meme detail UX: image error state, larger touch targets, refined button hierarchy, accessible roles
- Gallery content transitions with Crossfade, dark mode emoji card backgrounds, localized strings
- Import flow: error count in failure summary, Material icons for emoji editing, BackHandler for editor sheet
- Cross-cutting polish: consistent spacing tokens, dialog strings, export progress indicator, emoji selection feedback
- Navigate to gallery immediately after import starts instead of waiting
- Replaced foreground service with batched WorkManager workers for background processing
- Adaptive batch sizing based on device performance
- Removed unnecessary READ_MEDIA_IMAGES and READ_EXTERNAL_STORAGE permissions
- Bumped AGP from 9.0.1 to 9.1.0-rc01
- Import worker uses atomic status updates for crash safety
- Embedding progress now shows processed/remaining counts in gallery banner

### Added

- Stale import recovery: detects imports stuck >30 minutes on gallery startup and auto-resolves them
- Embedding progress banner in gallery with real-time processing counts

### Fixed

- Fixed LazyList duplicate key crash with defensive deduplication at DAO and Compose levels
- Fixed silent error swallowing in duplicate detection observation flow
- Eliminated dead code: unused SearchBarWithEmoji stub, deprecated onLongPress parameter, unused DropdownSettingItem
- Deduplicated formatFileSize() into shared core/common utility
- Import worker validates staged files exist before processing

### Improved

- Significantly expanded test suite: ZIP security tests, settings import/export tests, accessibility tests, adversarial data tests, DAO regression tests
- Improved test quality: fixed FTS bug, strengthened test oracles, reduced mock boilerplate

## [0.3.2] - 2026-02-21

### Fixed

- Fixed crash on meme detail screen when similar memes contained duplicate entries from multiple embedding types

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

[Unreleased]: https://github.com/adsamcik/riposte/compare/v0.4.3...HEAD
[0.4.3]: https://github.com/adsamcik/riposte/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/adsamcik/riposte/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/adsamcik/riposte/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/adsamcik/riposte/compare/v0.3.4...v0.4.0
[0.3.4]: https://github.com/adsamcik/riposte/compare/v0.3.3...v0.3.4
[0.3.3]: https://github.com/adsamcik/riposte/compare/v0.3.2...v0.3.3
[0.3.2]: https://github.com/adsamcik/riposte/compare/v0.3.1...v0.3.2
[0.3.1]: https://github.com/adsamcik/riposte/compare/v0.3.0...v0.3.1
[0.3.0]: https://github.com/adsamcik/riposte/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/adsamcik/riposte/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/adsamcik/riposte/releases/tag/v0.1.0
