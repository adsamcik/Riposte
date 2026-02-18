# Semantic Search Implementation

This document describes the semantic search feature in Riposte, which uses Google's EmbeddingGemma model via the AI Edge RAG SDK for high-quality on-device semantic embeddings.

## Overview

The app uses a hybrid search approach combining:
- **Full-Text Search (FTS4)**: Traditional keyword matching (60% weight)
- **Semantic Search**: Vector similarity using embeddings (40% weight)

This allows users to find memes using natural language queries that may not exactly match the stored metadata.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Search Flow                              │
├─────────────────────────────────────────────────────────────┤
│ User Query → HybridSearchUseCase                            │
│     ├── FTS4 Search (MemeSearchDao)         → 60% weight    │
│     └── Semantic Search (SemanticSearchEngine) → 40% weight │
│         ├── Query Embedding (EmbeddingGemmaGenerator)       │
│         └── Cosine Similarity (vector comparison)           │
└─────────────────────────────────────────────────────────────┘
```

## Components

### EmbeddingGemmaGenerator

Location: `core/ml/src/main/kotlin/.../EmbeddingGemmaGenerator.kt`

Generates 768-dimensional embeddings using Google's EmbeddingGemma 300M model via the AI Edge RAG SDK. This is a state-of-the-art embedding model released in September 2025.

**Features:**
- 768-dimensional high-quality embeddings
- GPU-accelerated with CPU fallback
- Platform-specific optimizations for Qualcomm and MediaTek chipsets
- 100+ language support
- Image embedding via ML Kit Image Labeling → text embedding
- Thread-safe with mutex protection
- Lazy initialization of ML resources

### SemanticSearchEngine

Location: `core/ml/src/main/kotlin/.../SemanticSearchEngine.kt`

Manages the semantic search operations:
- Generates embeddings for search queries
- Computes cosine similarity between query and stored embeddings
- Returns ranked results based on similarity scores

### EmbeddingManager

Location: `core/ml/src/main/kotlin/.../EmbeddingManager.kt`

Handles embedding lifecycle:
- Generates and stores embeddings for new memes
- Tracks model versions for compatibility
- Re-generates embeddings when model changes

## Model Requirements

### EmbeddingGemma 300M Model

The app requires the EmbeddingGemma TFLite model and SentencePiece tokenizer in assets:

**Files**:
- `embeddinggemma-300M_seq512_mixed-precision.tflite` (~179 MB)
- `sentencepiece.model` (~4.7 MB)

**Location**: `app/src/main/assets/embedding_models/`
**Dimensions**: 768

### Obtaining the Model

1. **From HuggingFace**:
   - Visit `https://huggingface.co/litert-community/embeddinggemma-300m`
   - Accept the Gemma license terms
   - Download the generic or platform-specific model

2. **Platform-Specific Optimizations**:
   - Qualcomm: sm8550, sm8650, sm8750, sm8850
   - MediaTek: mt6991, mt6993

### Model Compatibility

The app tracks model versions to ensure embedding compatibility:

| Version | Model | Dimensions | Notes |
| ------- | ----- | ---------- | ----- |
| `embeddinggemma:1.0.0` | EmbeddingGemma 300M | 768 | Current version |
| `mediapipe_use:1.0.0` | Universal Sentence Encoder | 512 | Legacy fallback |
| `litert_use:1.0.0` | Legacy | 512 | Deprecated |

When the model version changes, existing embeddings are marked for regeneration.

## Embedding Storage

Embeddings are stored in the `meme_embeddings` table:

```sql
CREATE TABLE meme_embeddings (
    memeId TEXT PRIMARY KEY,
    embedding BLOB NOT NULL,        -- 768 floats as binary
    dimension INTEGER NOT NULL,     -- 768
    modelVersion TEXT NOT NULL,     -- "embeddinggemma:1.0.0"
    generatedAt INTEGER NOT NULL,   -- Epoch millis
    needsRegeneration INTEGER NOT NULL DEFAULT 0
);
```

## Performance Considerations

1. **Lazy Initialization**: Model is loaded on first use, not app startup
2. **Background Processing**: Embeddings generated via WorkManager
3. **Caching**: Query embeddings can be cached for repeated searches
4. **Batch Processing**: Multiple memes processed in batches

## Testing

### Unit Tests

Located in `core/ml/src/test/`:
- `EmbeddingManagerTest` - Tests embedding lifecycle
- `EmbeddingModelVersionManagerTest` - Tests version tracking

### Instrumented Tests

Located in `core/ml/src/androidTest/`:
- `MediaPipeEmbeddingGeneratorTest` - Requires Android runtime
- `MlIntegrationTest` - Full integration tests

## Error Handling — No Silent Fallback

### Design Decision

When the semantic search index is unavailable (model not compatible, files missing, initialization failure), the app **does not silently fall back to text-only search**. Instead, errors are surfaced to the user immediately.

**Rationale:**
- Silent fallback hides real problems from the user. If the embedding model can't load, the user sees degraded search quality with no explanation and no way to act on it.
- Showing "0 of X memes indexed" with a progress bar when indexing will never complete is misleading. The user waits for progress that will never happen.
- Explicit error messages ("Indexing not supported on this device", "Indexing failed — try reinstalling the app") give users actionable information.

### What happens when the model is unavailable

| Layer | Behavior |
|-------|----------|
| **Settings** | Shows error message immediately on first failure (not after confirmed threshold). Displays root-cause-specific text in red: "not supported", "not included", or "failed". No progress bar shown. |
| **Search** | `UnsatisfiedLinkError` → "Semantic search not supported on this device". `ExceptionInInitializerError` → "Search index failed to load". Both surface as `errorMessage` in the search UI. |
| **Background indexing** | `EmbeddingManager` skips scheduling when `initializationError != null`. No wasted work. |

### Error categories

| Raw error string | User-facing message (Settings) | Root cause |
|------------------|-------------------------------|------------|
| `"Model not compatible with this device"` | Indexing not supported on this device | Native library missing or unsupported ABI |
| `"Model files not found"` | Search model not included in this build | Lite flavor or missing assets |
| `"Model failed to load"` | Indexing failed — try reinstalling the app | Static init error |
| `"Model initialization failed"` | Indexing failed — try reinstalling the app | GPU + CPU both failed |

### Previous behavior (removed)

Previously, `SearchDelegate.performSearch()` caught `UnsatisfiedLinkError` and `ExceptionInInitializerError` and silently fell back to FTS-only text search via `fallbackToTextSearch()`. The settings screen also required 2 confirmed failures on the same app version before showing an error, displaying the misleading "0 of X memes indexed" progress bar in the interim.

## Troubleshooting

### Model Not Loading

```
W/MediaPipeEmbedding: Model file not found: universal_sentence_encoder.tflite
```

Ensure the model file is placed in `app/src/main/assets/`.

### Zero Embeddings

If all embeddings are zero:
1. Check model file exists and is valid
2. Verify model version compatibility
3. Check logs for initialization errors

### Slow Search

If semantic search is slow:
1. Verify embeddings are pre-computed (not generated on-demand)
2. Check if model initialization is blocking
3. Consider reducing the number of results to compare
