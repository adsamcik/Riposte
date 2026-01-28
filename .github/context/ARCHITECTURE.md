# Architecture

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                                    App                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         Presentation Layer                           │    │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐   │    │
│  │  │ Gallery │  │ Import  │  │ Search  │  │  Share  │  │Settings │   │    │
│  │  │ Screen  │  │ Screen  │  │ Screen  │  │ Screen  │  │ Screen  │   │    │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘   │    │
│  │       │            │            │            │            │         │    │
│  │  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐  ┌────┴────┐   │    │
│  │  │Gallery  │  │Import   │  │Search   │  │ Share   │  │Settings │   │    │
│  │  │ViewModel│  │ViewModel│  │ViewModel│  │ViewModel│  │ViewModel│   │    │
│  │  └────┬────┘  └────┴────┘  └────┬────┘  └────┬────┘  └────┬────┘   │    │
│  └───────┼────────────────────────┼────────────┼────────────┼─────────┘    │
│          │        Domain Layer    │            │            │              │
│  ┌───────┴────────────────────────┴────────────┴────────────┴─────────┐    │
│  │                           Use Cases                                 │    │
│  │  GetMemesUseCase, DeleteMemesUseCase, SearchMemesUseCase, etc.     │    │
│  └────────────────────────────────┬────────────────────────────────────┘    │
│                                   │                                          │
│  ┌────────────────────────────────┴────────────────────────────────────┐    │
│  │                         Data Layer (Repositories)                    │    │
│  │  GalleryRepository, SearchRepository, SettingsRepository, etc.      │    │
│  └─────────────────┬─────────────────────────────┬─────────────────────┘    │
│                    │                             │                          │
└────────────────────┼─────────────────────────────┼──────────────────────────┘
                     │                             │
┌────────────────────┴─────────────────────────────┴──────────────────────────┐
│                               Core Modules                                   │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │  database  │  │  datastore │  │     ml     │  │   model    │            │
│  │   (Room)   │  │(DataStore) │  │ (ML Kit,   │  │  (Domain   │            │
│  │            │  │            │  │  MediaPipe,│  │   Models)  │            │
│  │ MemeDao    │  │Preferences │  │  LiteRT)   │  │            │            │
│  │ SearchDao  │  │ DataStore  │  │            │  │ Meme       │            │
│  │ EmbeddingDao│ │            │  │ Embeddings │  │ EmojiTag   │            │
│  └────────────┘  └────────────┘  └────────────┘  └────────────┘            │
│  ┌────────────┐  ┌────────────┐                                             │
│  │   common   │  │     ui     │                                             │
│  │ (Routes,   │  │  (Theme,   │                                             │
│  │  Utils)    │  │ Components)│                                             │
│  └────────────┘  └────────────┘                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Module Dependencies

```text
app
 ├── feature:gallery
 ├── feature:import
 ├── feature:search
 ├── feature:share
 ├── feature:settings
 └── core:* (all)

feature:*
 ├── core:model
 ├── core:common
 ├── core:ui
 ├── core:database (via repository binding)
 ├── core:datastore
 └── core:ml

core:database
 └── core:model

core:ml
 └── core:model

core:ui
 ├── core:model
 └── core:common

core:testing
 └── core:model
```

## Component Map

### core:database

**Purpose**: SQLite persistence via Room with FTS4 full-text search

**Location**: `core/database/src/main/kotlin/com/mememymood/core/database/`

**Key Files**:

- `MemeDatabase.kt` - Room database definition with migrations
- `dao/MemeDao.kt` - CRUD operations for memes
- `dao/MemeSearchDao.kt` - FTS4 search queries
- `dao/MemeEmbeddingDao.kt` - Vector embedding storage
- `entity/MemeEntity.kt` - Meme table schema
- `entity/MemeFtsEntity.kt` - FTS virtual table
- `entity/MemeEmbeddingEntity.kt` - Embedding storage table
- `di/DatabaseModule.kt` - Hilt bindings

**Schema Version**: 3 (with migrations 1→2, 2→3)

---

### core:ml

**Purpose**: On-device AI for text recognition, image labeling, and semantic embeddings

**Location**: `core/ml/src/main/kotlin/com/mememymood/core/ml/`

**Key Files**:

- `SemanticSearchEngine.kt` - Hybrid FTS + vector search interface
- `DefaultSemanticSearchEngine.kt` - Implementation with cosine similarity
- `MediaPipeEmbeddingGenerator.kt` - 512-dim embeddings via Universal Sentence Encoder
- `MlKitTextRecognizer.kt` - OCR text extraction
- `EmbeddingManager.kt` - Embedding lifecycle management
- `EmbeddingModelVersionManager.kt` - Model version tracking for re-generation
- `XmpMetadataHandler.kt` - Read/write XMP metadata in images
- `worker/EmbeddingGenerationWorker.kt` - Background embedding generation

**Dependencies**: ML Kit, MediaPipe Tasks, LiteRT

---

### core:model

**Purpose**: Domain models shared across all modules

**Location**: `core/model/src/main/kotlin/com/mememymood/core/model/`

**Key Files**:

- `Meme.kt` - Primary meme model with emojis, metadata, search content
- `EmojiTag.kt` - Emoji representation with codepoint
- `MemeMetadata.kt` - XMP metadata structure
- `ShareConfig.kt` - Sharing format/quality/size options
- `AppPreferences.kt` - User preferences model
- `SearchResult.kt` - Search result with relevance scoring

---

### core:ui

**Purpose**: Design system with Material 3 theming and reusable components

**Location**: `core/ui/src/main/kotlin/com/mememymood/core/ui/`

**Key Files**:

- `theme/Theme.kt` - Dynamic color theme setup
- `theme/Color.kt` - Color palette
- `theme/Typography.kt` - Text styles
- `component/MemeCard.kt` - Gallery item component
- `component/EmojiPicker.kt` - Emoji selection UI
- `component/SearchBar.kt` - Search input component

---

### core:common

**Purpose**: Shared utilities, navigation routes, extension functions

**Location**: `core/common/src/main/kotlin/com/mememymood/core/common/`

**Key Files**:

- `navigation/Routes.kt` - Type-safe navigation route definitions
- `result/Result.kt` - Result wrapper for operations
- `extension/` - Kotlin extension functions
- `util/` - Utility classes

---

### feature:gallery

**Purpose**: Main gallery grid, meme detail view, favorites management

**Location**: `feature/gallery/src/main/kotlin/com/mememymood/feature/gallery/`

**Key Files**:

- `presentation/GalleryScreen.kt` - Grid composable
- `presentation/GalleryViewModel.kt` - MVI state management
- `presentation/GalleryUiState.kt` - UI state data class
- `presentation/GalleryIntent.kt` - User action sealed interface
- `presentation/GalleryEffect.kt` - Side effects sealed interface
- `presentation/MemeDetailScreen.kt` - Single meme view
- `presentation/MemeDetailViewModel.kt` - Detail view logic
- `domain/usecase/GalleryUseCases.kt` - Business logic
- `domain/repository/GalleryRepository.kt` - Repository interface
- `data/GalleryRepositoryImpl.kt` - Repository implementation
- `di/GalleryModule.kt` - Hilt bindings
- `navigation/GalleryNavigation.kt` - NavGraph extensions

---

### feature:search

**Purpose**: Hybrid full-text and semantic search

**Location**: `feature/search/src/main/kotlin/com/mememymood/feature/search/`

**Key Files**:

- `presentation/SearchScreen.kt` - Search UI with results
- `presentation/SearchViewModel.kt` - Search state management
- `domain/usecase/SearchUseCases.kt` - Hybrid search logic
- `data/SearchRepositoryImpl.kt` - Combines FTS and vector search

---

### feature:import

**Purpose**: Import images from gallery, files, or ZIP bundles

**Location**: `feature/import/src/main/kotlin/com/mememymood/feature/import_feature/`

**Key Files**:

- `presentation/ImportScreen.kt` - Import UI
- `presentation/ImportViewModel.kt` - Import state management
- `domain/usecase/ImportZipBundleUseCase.kt` - ZIP import with sidecar JSON
- `data/ZipImporter.kt` - ZIP extraction and processing

---

### feature:share

**Purpose**: Share memes with configurable format, quality, and size

**Location**: `feature/share/src/main/kotlin/com/mememymood/feature/share/`

**Key Files**:

- `presentation/ShareScreen.kt` - Share options UI
- `presentation/ShareViewModel.kt` - Sharing state
- `domain/usecase/ShareUseCases.kt` - Image processing and sharing
- `data/ImageProcessor.kt` - Format conversion, resizing

---

### feature:settings

**Purpose**: App preferences and configuration

**Location**: `feature/settings/src/main/kotlin/com/mememymood/feature/settings/`

**Key Files**:

- `presentation/SettingsScreen.kt` - Settings UI
- `presentation/SettingsViewModel.kt` - Preferences management
- `presentation/component/` - Reusable setting item components

---

## Data Flows

### Meme Import Flow

```text
User selects images → ImportViewModel.onIntent(SelectImages)
    │
    ├── Process each image:
    │   ├── Copy to app storage
    │   ├── Extract EXIF/XMP metadata (XmpMetadataHandler)
    │   ├── Run OCR (MlKitTextRecognizer)
    │   ├── Generate embedding (EmbeddingManager)
    │   └── Create MemeEntity
    │
    └── Insert into database (MemeDao.insertMemes)
        └── FTS index updated automatically (trigger)
```

### Search Flow

```text
User types query → SearchViewModel.onIntent(UpdateQuery)
    │
    └── HybridSearchUseCase.invoke(query)
        ├── FTS Search (MemeSearchDao.searchFts) → 60% weight
        │   └── Returns matching memes with FTS score
        │
        └── Semantic Search (SemanticSearchEngine.search) → 40% weight
            ├── Generate query embedding (MediaPipeEmbeddingGenerator)
            ├── Load meme embeddings (MemeEmbeddingDao)
            ├── Compute cosine similarity
            └── Rank by similarity score
        │
        └── Merge and rank results → SearchResult list
```

### Share Flow

```text
User taps share → ShareViewModel.onIntent(Share)
    │
    ├── Load ShareConfig from preferences
    │
    └── ShareUseCases.prepareForSharing(meme, config)
        ├── Load original image
        ├── Resize if needed (ImageProcessor)
        ├── Convert format if needed (ImageProcessor)
        ├── Embed XMP metadata (XmpMetadataHandler)
        ├── Adjust quality (ImageProcessor)
        └── Create share Intent
            │
            └── GalleryEffect.LaunchShareIntent(intent)
```

## External Integrations

| Service | Purpose | Module |
| ------- | ------- | ------ |
| ML Kit Text Recognition | OCR on meme images | core:ml |
| ML Kit Image Labeling | Generate image labels for search | core:ml |
| MediaPipe Text Embedder | Semantic embeddings (USE model) | core:ml |
| Android Share Sheet | Native sharing | feature:share |
| WorkManager | Background embedding generation | core:ml |

## Database Schema

```sql
-- Main memes table
CREATE TABLE memes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    filePath TEXT NOT NULL UNIQUE,
    fileName TEXT NOT NULL,
    mimeType TEXT NOT NULL,
    width INTEGER NOT NULL,
    height INTEGER NOT NULL,
    fileSizeBytes INTEGER NOT NULL,
    importedAt INTEGER NOT NULL,
    emojiTagsJson TEXT NOT NULL,  -- JSON array
    title TEXT,
    description TEXT,
    textContent TEXT,             -- OCR extracted text
    embedding BLOB,               -- Legacy, migrated to meme_embeddings
    isFavorite INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    useCount INTEGER NOT NULL DEFAULT 0,
    primaryLanguage TEXT,         -- BCP 47 language code (v1.1)
    localizationsJson TEXT        -- JSON object with translations (v1.1)
);

-- FTS virtual table for full-text search
CREATE VIRTUAL TABLE memes_fts USING fts4(
    title, description, textContent, emojiTagsJson,
    content="memes"
);

-- Embeddings table (added in migration 1→2)
CREATE TABLE meme_embeddings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    memeId INTEGER NOT NULL UNIQUE,
    embedding BLOB NOT NULL,      -- Float array as bytes
    dimension INTEGER NOT NULL,   -- 512 for USE model
    modelVersion TEXT NOT NULL,
    generatedAt INTEGER NOT NULL,
    sourceTextHash TEXT,          -- For change detection
    needsRegeneration INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (memeId) REFERENCES memes(id) ON DELETE CASCADE
);

-- Emoji tags table
CREATE TABLE emoji_tags (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    memeId INTEGER NOT NULL,
    emoji TEXT NOT NULL,
    FOREIGN KEY (memeId) REFERENCES memes(id) ON DELETE CASCADE
);
```
