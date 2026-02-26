# Assets Directory

This directory is for storing application assets like ML models.

## ML Models

Embedding models are delivered via AI Packs (`aipacks/generic_embedding` and `aipacks/soc_optimized`)
and extracted to the app's internal storage at runtime. See `EmbeddingGemmaGenerator` for details.

### Fallback Behavior

If the embedding model is not available:

- Semantic search will be unavailable
- FTS4 (keyword) search will still work
- An error will be surfaced in embedding statistics
