package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedderResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Embedding generator using MediaPipe's TextEmbedder with Universal Sentence Encoder.
 *
 * This implementation provides real semantic embeddings for text, enabling meaningful
 * similarity comparisons. For images, it first extracts labels using ML Kit Image Labeling,
 * then generates embeddings from the concatenated label text.
 *
 * The TextEmbedder is not thread-safe, so all operations are protected by a mutex.
 * Initialization is lazy and happens on first use or via explicit [initialize] call.
 *
 * @property context Application context for accessing assets and content resolver.
 */
@Singleton
class MediaPipeEmbeddingGenerator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : EmbeddingGenerator {
        /**
         * Internal constructor for testing with injected dependencies.
         * @suppress Do not use this constructor directly in production code.
         */
        internal constructor(
            context: Context,
            testTextEmbedder: TextEmbedder?,
            testEmbeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION,
        ) : this(context) {
            this.textEmbedder = testTextEmbedder
            this.cachedEmbeddingDimension = testEmbeddingDimension
            this.initializationAttempted = true
        }

        /** Lazily initialized image labeler to avoid native library loading issues in tests. */
        private var _imageLabeler: com.google.mlkit.vision.label.ImageLabeler? = null
        private val imageLabeler: com.google.mlkit.vision.label.ImageLabeler
            get() {
                if (_imageLabeler == null) {
                    _imageLabeler =
                        ImageLabeling.getClient(
                            ImageLabelerOptions.Builder()
                                .setConfidenceThreshold(IMAGE_LABEL_CONFIDENCE_THRESHOLD)
                                .build(),
                        )
                }
                return _imageLabeler!!
            }

        /** Mutex to ensure thread-safe access to the TextEmbedder. */
        private val mutex = Mutex()

        /** The MediaPipe TextEmbedder instance, lazily initialized. */
        private var textEmbedder: TextEmbedder? = null

        /** Flag indicating whether initialization has been attempted. */
        private var initializationAttempted = false

        /** Cached embedding dimension from the model. */
        private var cachedEmbeddingDimension: Int? = null

        override val embeddingDimension: Int
            get() = cachedEmbeddingDimension ?: DEFAULT_EMBEDDING_DIMENSION

        override suspend fun generateFromText(text: String): FloatArray =
            withContext(Dispatchers.Default) {
                if (text.isBlank()) {
                    return@withContext createZeroEmbedding()
                }

                mutex.withLock {
                    ensureInitialized()

                    val embedder = textEmbedder
                    if (embedder == null) {
                        Timber.w("TextEmbedder not available, returning zero embedding")
                        return@withLock createZeroEmbedding()
                    }

                    try {
                        val result: TextEmbedderResult = embedder.embed(text)
                        extractEmbedding(result)
                    } catch (
                        @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                        e: Exception,
                    ) {
                        Timber.e(e, "Failed to generate text embedding")
                    }
                }
            }

        override suspend fun generateFromImage(bitmap: Bitmap): FloatArray =
            withContext(Dispatchers.Default) {
                try {
                    val labels = getImageLabels(bitmap)

                    if (labels.isEmpty()) {
                        Timber.d("No labels detected in image, returning zero embedding")
                        return@withContext createZeroEmbedding()
                    }

                    // Concatenate labels with spaces for embedding
                    val labelText = labels.joinToString(" ")
                    generateFromText(labelText)
                } catch (
                    @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to generate image embedding")
            }

        override suspend fun generateFromUri(uri: Uri): FloatArray =
            withContext(Dispatchers.IO) {
                try {
                    val bitmap =
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }

                    if (bitmap == null) {
                        Timber.w("Failed to decode bitmap from URI: $uri")
                        return@withContext createZeroEmbedding()
                    }

                    try {
                        generateFromImage(bitmap)
                    } finally {
                        bitmap.recycle()
                    }
                } catch (
                    @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to generate embedding from URI")

        override suspend fun isReady(): Boolean =
            mutex.withLock {
                textEmbedder != null
            }

        override suspend fun initialize() {
            mutex.withLock {
                if (textEmbedder != null) {
                    Timber.d("TextEmbedder already initialized")
                    return
                }

                initializeTextEmbedder()
            }
        }

        override fun close() {
            runBlocking {
                mutex.withLock {
                    textEmbedder?.close()
                    textEmbedder = null
                    initializationAttempted = false
                    _imageLabeler?.close()
                    _imageLabeler = null
                }
            }
        }

        /**
         * Ensures the TextEmbedder is initialized.
         * Must be called while holding the mutex.
         */
        private fun ensureInitialized() {
            if (textEmbedder == null && !initializationAttempted) {
                initializeTextEmbedder()
            }
        }

        /**
         * Initializes the MediaPipe TextEmbedder with Universal Sentence Encoder model.
         * Must be called while holding the mutex.
         */
        private fun initializeTextEmbedder() {
            initializationAttempted = true

            try {
                Timber.d("Initializing MediaPipe TextEmbedder with model: $MODEL_ASSET_PATH")

                val baseOptions =
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET_PATH)
                        .build()

                val options =
                    TextEmbedder.TextEmbedderOptions.builder()
                        .setBaseOptions(baseOptions)
                        .setL2Normalize(true) // Normalize embeddings for cosine similarity
                        .setQuantize(false) // Use full float precision
                        .build()

                textEmbedder = TextEmbedder.createFromOptions(context, options)

                // Cache the embedding dimension from the model
                updateEmbeddingDimension()

                Timber.i("MediaPipe TextEmbedder initialized successfully (dimension: $embeddingDimension)")
            } catch (
                @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                e: Exception,
            ) {
                Timber.e(e, "Failed to initialize MediaPipe TextEmbedder")
                textEmbedder = null
            }
        }

        /**
         * Updates the cached embedding dimension by performing a test embedding.
         * Must be called while holding the mutex and after textEmbedder is initialized.
         */
        private fun updateEmbeddingDimension() {
            try {
                val testResult = textEmbedder?.embed("test")
                val embeddings = testResult?.embeddingResult()?.embeddings()
                if (!embeddings.isNullOrEmpty()) {
                    val floatEmbedding = embeddings.first().floatEmbedding()
                    if (floatEmbedding != null) {
                        cachedEmbeddingDimension = floatEmbedding.size
                        Timber.d("Detected embedding dimension: ${floatEmbedding.size}")
                    }
                }
            } catch (
                @Suppress("TooGenericExceptionCaught") // ML libraries throw unpredictable exceptions
                e: Exception,
            ) {
                Timber.w(e, "Failed to detect embedding dimension, using default")
            }
        }

        /**
         * Extracts the float embedding array from a TextEmbedder result.
         *
         * @param result The result from TextEmbedder.embed()
         * @return The float array embedding, or zero embedding on failure.
         */
        private fun extractEmbedding(result: TextEmbedderResult): FloatArray {
            val embeddings = result.embeddingResult().embeddings()
            if (embeddings.isEmpty()) {
                Timber.w("TextEmbedder returned empty embeddings")
                return createZeroEmbedding()
            }

            val embedding = embeddings.first()
            val floatEmbedding = embedding.floatEmbedding()

            if (floatEmbedding == null) {
                Timber.w("TextEmbedder returned null float embedding")
                return createZeroEmbedding()
            }

            // Convert to FloatArray (MediaPipe returns a float[] which Kotlin sees as FloatArray)
            return floatEmbedding
        }

        /**
         * Gets image labels using ML Kit Image Labeling.
         *
         * @param bitmap The bitmap to analyze.
         * @return List of detected label strings.
         */
        private suspend fun getImageLabels(bitmap: Bitmap): List<String> {
            return suspendCancellableCoroutine { continuation ->
                val image = InputImage.fromBitmap(bitmap, 0)

                imageLabeler.process(image)
                    .addOnSuccessListener { labels ->
                        val labelTexts = labels.map { it.text.lowercase() }
                        continuation.resume(labelTexts)
                    }
                    .addOnFailureListener { exception ->
                        Timber.e(exception, "ML Kit Image Labeling failed")
                        // Return empty list on failure instead of throwing
                        continuation.resume(emptyList())
                    }

                continuation.invokeOnCancellation {
                    // ML Kit tasks cannot be cancelled directly
                    Timber.d("Image labeling task cancelled")
                }
            }
        }

        /**
         * Creates a zero-filled embedding array for graceful degradation.
         */
        private fun createZeroEmbedding(): FloatArray = FloatArray(embeddingDimension)

        companion object {
            /** Asset path for the Universal Sentence Encoder model. */
            const val MODEL_ASSET_PATH = "universal_sentence_encoder.tflite"

            /** Default embedding dimension for USE (512 dimensions). */
            const val DEFAULT_EMBEDDING_DIMENSION = 512

            /** Minimum confidence threshold for image labels. */
            private const val IMAGE_LABEL_CONFIDENCE_THRESHOLD = 0.5f

            /**
             * Computes cosine similarity between two embeddings.
             *
             * @param embedding1 First embedding vector.
             * @param embedding2 Second embedding vector.
             * @return Cosine similarity score between -1 and 1.
             */
            fun cosineSimilarity(
                embedding1: FloatArray,
                embedding2: FloatArray,
            ): Float {
                require(embedding1.size == embedding2.size) {
                    "Embeddings must have the same dimension: ${embedding1.size} vs ${embedding2.size}"
                }

                var dotProduct = 0f
                var norm1 = 0f
                var norm2 = 0f

                for (i in embedding1.indices) {
                    dotProduct += embedding1[i] * embedding2[i]
                    norm1 += embedding1[i] * embedding1[i]
                    norm2 += embedding2[i] * embedding2[i]
                }

                val magnitude = kotlin.math.sqrt(norm1) * kotlin.math.sqrt(norm2)
                return if (magnitude.isFinite() && magnitude > 0f) dotProduct / magnitude else 0f
            }
        }
    }
