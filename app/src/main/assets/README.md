# Assets Directory

This directory is for storing application assets like ML models.

## Required Files

### universal_sentence_encoder.tflite

**Purpose**: Text embedding model for semantic search
**Size**: ~25-40 MB
**Required**: Yes (for semantic search functionality)

### How to Obtain

1. Download from TensorFlow Hub:
   - <https://tfhub.dev/google/lite-model/universal-sentence-encoder-qa-ondevice/1>

2. Or use the MediaPipe sample app's model

3. Place the file in this directory as `universal_sentence_encoder.tflite`

### Fallback Behavior

If the model is not present:

- Semantic search will return zero embeddings
- FTS4 (keyword) search will still work
- A warning will be logged: "Model file not found"
