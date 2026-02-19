package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GemmaEmbeddingModel
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.sqrt

/**
 * Embedding generator using Google's EmbeddingGemma model via the AI Edge RAG SDK.
 *
 * EmbeddingGemma is a 308M parameter embedding model that produces high-quality
 * 768-dimensional embeddings. It supports Matryoshka Representation Learning (MRL),
 * allowing truncation to 512, 384, 256, or 128 dimensions with re-normalization.
 *
 * This implementation provides:
 * - GPU-accelerated inference (with CPU fallback)
 * - 768-dimension embeddings (best quality)
 * - 100+ language support
 * - ~119ms inference time on GPU (Samsung S25 Ultra benchmark)
 *
 * Model files required:
 * - embeddinggemma_512_mixed.tflite (~179MB)
 * - sentencepiece.model (~4MB)
 *
 * @property context Application context for accessing model files and content resolver.
 */
@Singleton
class EmbeddingGemmaGenerator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : EmbeddingGenerator {
        /** Lazily initialized image labeler for extracting features from images. */
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

        /** Mutex to ensure thread-safe access to the embedding model. */
        private val mutex = Mutex()

        /** The GemmaEmbeddingModel instance from AI Edge RAG SDK, lazily initialized. */
        private var embeddingModel: GemmaEmbeddingModel? = null

        /** Flag indicating whether initialization has been attempted. */
        private var initializationAttempted = false

        private var _initializationError: String? = null
        override val initializationError: String? get() = _initializationError

        /** Whether GPU acceleration is enabled (auto-detected on first use). */
        private var useGpu: Boolean? = null

        private fun shouldUseGpu(): Boolean {
            val current = useGpu
            if (current != null) return current
            val available = isOpenClAvailable()
            useGpu = available
            return available
        }

        /** Executor for handling ListenableFuture callbacks. */
        private var callbackExecutor = Executors.newSingleThreadExecutor()

        override val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION

        override suspend fun generateFromText(text: String): FloatArray =
            withContext(Dispatchers.Default) {
                if (text.isBlank()) {
                    return@withContext createZeroEmbedding()
                }

                mutex.withLock {
                    ensureInitialized()

                    val model =
                        embeddingModel
                            ?: throw IllegalStateException(
                                "EmbeddingGemma model not initialized. " +
                                    "Model files may be missing.",
                            )

                    try {
                        // Create embedding request with query task type
                        val embedData =
                            EmbedData.create(
                                text,
                                EmbedData.TaskType.RETRIEVAL_QUERY,
                            )
                        val request = EmbeddingRequest.create(listOf(embedData))

                        // Get embeddings using ListenableFuture
                        val future = model.getEmbeddings(request)
                        val embedding = awaitListenableFuture(future)

                        // Convert ImmutableList<Float> to FloatArray
                        embedding.map { it }.toFloatArray()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to generate text embedding")
                        throw e
                    }
                }
            }

        /**
         * Generates an embedding for a document (meme description, tags, etc.).
         * Uses RETRIEVAL_DOCUMENT task type for optimal retrieval quality.
         *
         * @param text The document text to embed.
         * @param title Optional title for the document (improves embedding quality).
         * @return The embedding vector.
         */
        suspend fun generateFromDocument(
            text: String,
            title: String? = null,
        ): FloatArray =
            withContext(Dispatchers.Default) {
                if (text.isBlank()) {
                    return@withContext createZeroEmbedding()
                }

                mutex.withLock {
                    ensureInitialized()

                    val model =
                        embeddingModel
                            ?: throw IllegalStateException(
                                "EmbeddingGemma model not initialized. " +
                                    "Model files may be missing.",
                            )

                    try {
                        // Create embedding request with document task type
                        val embedDataBuilder =
                            EmbedData.builder<String>()
                                .setData(text)
                                .setTask(EmbedData.TaskType.RETRIEVAL_DOCUMENT)
                                .setIsQuery(false)

                        // Add title metadata if provided
                        if (!title.isNullOrBlank()) {
                            embedDataBuilder.setMetadata(
                                mapOf(GemmaEmbeddingModel.TITLE_KEY to title),
                            )
                        }

                        val request = EmbeddingRequest.create(listOf(embedDataBuilder.build()))

                        val future = model.getEmbeddings(request)
                        val embedding = awaitListenableFuture(future)

                        embedding.map { it }.toFloatArray()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to generate document embedding")
                        throw e
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

                    // Concatenate labels with spaces for embedding as a document
                    val labelText = labels.joinToString(" ")
                    generateFromDocument(labelText, title = "Image labels")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to generate image embedding")
                    throw e
                }
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
                } catch (e: Exception) {
                    Timber.e(e, "Failed to generate embedding from URI")
                    throw e
                }
            }

        override suspend fun isReady(): Boolean =
            mutex.withLock {
                embeddingModel != null
            }

        override suspend fun initialize() {
            mutex.withLock {
                if (embeddingModel != null) {
                    Timber.d("EmbeddingGemma already initialized")
                    return
                }

                initializeEmbeddingModel()
            }
        }

        override fun close() {
            embeddingModel = null
            initializationAttempted = false
            _imageLabeler?.close()
            _imageLabeler = null
            callbackExecutor.shutdown()
            callbackExecutor = Executors.newSingleThreadExecutor()
        }

        /**
         * Ensures the embedding model is initialized.
         * Must be called while holding the mutex.
         */
        private fun ensureInitialized() {
            if (embeddingModel == null && !initializationAttempted) {
                initializeEmbeddingModel()
            }
        }

        /**
         * Initializes the GemmaEmbeddingModel with the model files.
         * Must be called while holding the mutex.
         */
        private fun initializeEmbeddingModel() {
            initializationAttempted = true

            try {
                // Ensure models are copied from assets to internal storage
                copyModelsFromAssetsIfNeeded()

                val modelPath = getModelPath()
                val tokenizerPath = getTokenizerPath()

                if (!File(modelPath).exists()) {
                    Timber.e("EmbeddingGemma model not found at: $modelPath")
                    Timber.i("Please download the model from HuggingFace: litert-community/embeddinggemma-300m")
                    Timber.i("Or run: tools/download-embeddinggemma.ps1 -AllVariants")
                    _initializationError = ERROR_FILES_NOT_FOUND
                    return
                }

                if (!File(tokenizerPath).exists()) {
                    Timber.e("SentencePiece tokenizer not found at: $tokenizerPath")
                    _initializationError = ERROR_FILES_NOT_FOUND
                    return
                }

                Timber.d("Initializing EmbeddingGemma with GPU=${shouldUseGpu()}")
                Timber.d("Model path: $modelPath")
                Timber.d("Tokenizer path: $tokenizerPath")

                embeddingModel =
                    GemmaEmbeddingModel(
                        modelPath,
                        tokenizerPath,
                        shouldUseGpu(),
                    )

                Timber.i("EmbeddingGemma initialized successfully (dimension: $embeddingDimension)")
                _initializationError = null
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Native library not available for EmbeddingGemma (unsupported ABI?)")
                embeddingModel = null
                _initializationError = ERROR_NOT_COMPATIBLE
            } catch (e: ExceptionInInitializerError) {
                Timber.e(e, "EmbeddingGemma static initialization failed")
                embeddingModel = null
                _initializationError = ERROR_FAILED_TO_LOAD
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize EmbeddingGemma")

                // Try CPU fallback if GPU fails
                if (shouldUseGpu()) {
                    Timber.i("Retrying with CPU...")
                    useGpu = false
                    try {
                        val modelPath = getModelPath()
                        val tokenizerPath = getTokenizerPath()

                        embeddingModel =
                            GemmaEmbeddingModel(
                                modelPath,
                                tokenizerPath,
                                false,
                            )
                        Timber.i("EmbeddingGemma initialized with CPU fallback")
                        _initializationError = null
                    } catch (cpuError: UnsatisfiedLinkError) {
                        Timber.e(cpuError, "CPU fallback failed: native library not available")
                        embeddingModel = null
                        _initializationError = ERROR_NOT_COMPATIBLE
                    } catch (cpuError: ExceptionInInitializerError) {
                        Timber.e(cpuError, "CPU fallback failed: static initialization error")
                        embeddingModel = null
                        _initializationError = ERROR_FAILED_TO_LOAD
                    } catch (cpuError: Exception) {
                        Timber.e(cpuError, "CPU fallback also failed")
                        embeddingModel = null
                        _initializationError = ERROR_INIT_FAILED
                    }
                } else {
                    embeddingModel = null
                    _initializationError = ERROR_INIT_FAILED
                }
            }
        }

        /**
         * Copies model files from assets to internal storage if they don't exist.
         * This allows bundling models with the APK while using them from a file path.
         */
        private fun copyModelsFromAssetsIfNeeded() {
            val modelDir = File(context.filesDir, MODEL_DIRECTORY)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }

            // Always copy tokenizer if missing
            copyAssetIfNeeded(TOKENIZER_FILENAME, modelDir)

            // Try to copy the best model for this device
            val bestModel = getBestModelFilename()
            if (copyAssetIfNeeded(bestModel, modelDir)) {
                Timber.i("Copied optimized model from assets: $bestModel")
                return
            }

            // Fall back to generic model
            if (copyAssetIfNeeded(MODEL_FILENAME_GENERIC, modelDir)) {
                Timber.i("Copied generic model from assets: $MODEL_FILENAME_GENERIC")
            }
        }

        /**
         * Copies a single file from assets to the target directory if it doesn't exist.
         * @return true if file exists (either copied or already present), false if not available.
         */
        private fun copyAssetIfNeeded(
            assetName: String,
            targetDir: File,
        ): Boolean {
            val targetFile = File(targetDir, assetName)
            if (targetFile.exists()) {
                return true
            }

            return try {
                context.assets.open("$MODEL_DIRECTORY/$assetName").use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Timber.d("Copied asset: $assetName (${targetFile.length() / 1024 / 1024} MB)")
                true
            } catch (e: Exception) {
                Timber.d("Asset not found: $assetName")
                false
            }
        }

        /**
         * Gets the path to the EmbeddingGemma model file.
         * Tries platform-specific optimized model first, falls back to generic.
         */
        private fun getModelPath(): String {
            val modelDir = File(context.filesDir, MODEL_DIRECTORY)

            // First, try the best model for this device's chipset
            val bestModelFile = getBestModelFilename()
            val optimizedPath = File(modelDir, bestModelFile)
            if (optimizedPath.exists()) {
                Timber.d("Using optimized model: $bestModelFile")
                return optimizedPath.absolutePath
            }

            // Fall back to generic model
            val genericPath = File(modelDir, MODEL_FILENAME_GENERIC)
            if (genericPath.exists()) {
                Timber.d("Using generic model (optimized not found)")
                return genericPath.absolutePath
            }

            // Return expected path for error messaging
            return optimizedPath.absolutePath
        }

        /**
         * Gets the path to the SentencePiece tokenizer file.
         */
        private fun getTokenizerPath(): String {
            val modelDir = File(context.filesDir, MODEL_DIRECTORY)
            return File(modelDir, TOKENIZER_FILENAME).absolutePath
        }

        /**
         * Awaits a ListenableFuture and returns its result.
         */
        private suspend fun <T> awaitListenableFuture(
            future: com.google.common.util.concurrent.ListenableFuture<T>,
        ): T =
            suspendCancellableCoroutine { continuation ->
                Futures.addCallback(
                    future,
                    object : FutureCallback<T> {
                        override fun onSuccess(result: T) {
                            continuation.resume(result)
                        }

                        override fun onFailure(t: Throwable) {
                            continuation.resumeWithException(t)
                        }
                    },
                    callbackExecutor,
                )

                continuation.invokeOnCancellation {
                    future.cancel(true)
                }
            }

        /**
         * Gets image labels using ML Kit Image Labeling.
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
                        continuation.resume(emptyList())
                    }

                continuation.invokeOnCancellation {
                    Timber.d("Image labeling task cancelled")
                }
            }
        }

        /**
         * Creates a zero-filled embedding array for graceful degradation.
         */
        private fun createZeroEmbedding(): FloatArray = FloatArray(embeddingDimension)

        companion object {

            /**
             * Checks whether OpenCL is available on this device.
             * The AI Edge RAG SDK's native code fatally aborts (CHECK failure) if the GPU
             * delegate cannot be created. Since this is a native abort, it cannot be caught
             * by Java/Kotlin exception handlers. We proactively check for OpenCL availability
             * to avoid passing useGpu=true on devices where it would crash.
             */
            private fun isOpenClAvailable(): Boolean =
                try {
                    System.loadLibrary("OpenCL")
                    true
                } catch (_: UnsatisfiedLinkError) {
                    Timber.w("OpenCL not available, disabling GPU acceleration")
                    false
                }

            /** Directory where model files are stored. */
            const val MODEL_DIRECTORY = "embedding_models"

            /** Generic EmbeddingGemma model filename (works on all devices). */
            const val MODEL_FILENAME_GENERIC = "embeddinggemma-300M_seq512_mixed-precision.tflite"

            /** Platform-specific model filenames for optimized performance. */
            private val PLATFORM_MODELS =
                mapOf(
                    // Qualcomm Snapdragon
                    // 8 Gen 2
                    "sm8550" to
                        "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite",
                    // 8+ Gen 1 (use 8 Gen 2)
                    "sm8475" to
                        "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite",
                    // 8 Gen 3
                    "sm8650" to
                        "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8650.tflite",
                    // 8 Gen 4 (Elite)
                    "sm8750" to
                        "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite",
                    // 8 Gen 5
                    "sm8850" to
                        "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8850.tflite",
                    // MediaTek Dimensity
                    // Dimensity 9300
                    "mt6991" to
                        "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite",
                    // Dimensity 9200 (use 9300)
                    "mt6989" to
                        "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite",
                    // Dimensity 9400
                    "mt6993" to
                        "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6993.tflite",
                )

            /** SentencePiece tokenizer filename. */
            const val TOKENIZER_FILENAME = "sentencepiece.model"

            /** Default embedding dimension for EmbeddingGemma (768 dimensions). */
            const val DEFAULT_EMBEDDING_DIMENSION = 768

            // region Initialization Error Constants
            /** Device does not support the required native libraries (e.g. unsupported ABI). */
            const val ERROR_NOT_COMPATIBLE = "Model not compatible with this device"

            /** Model asset files are missing (e.g. lite build flavor). */
            const val ERROR_FILES_NOT_FOUND = "Model files not found"

            /** Model static initializer failed (ExceptionInInitializerError). */
            const val ERROR_FAILED_TO_LOAD = "Model failed to load"

            /** Both GPU and CPU initialization attempts failed. */
            const val ERROR_INIT_FAILED = "Model initialization failed"
            // endregion

            /** Minimum confidence threshold for image labels. */
            private const val IMAGE_LABEL_CONFIDENCE_THRESHOLD = 0.5f

            /**
             * Detects the device's SoC model and returns the best model filename.
             * Falls back to generic model if no optimized variant is available.
             */
            fun getBestModelFilename(): String {
                val socModel = Build.SOC_MODEL.lowercase()

                Timber.d("Detected SoC: $socModel")

                // Try to find a matching platform-specific model
                for ((chipset, modelFile) in PLATFORM_MODELS) {
                    if (socModel.contains(chipset.lowercase())) {
                        Timber.i("Using optimized model for $chipset: $modelFile")
                        return modelFile
                    }
                }

                // Also check Build.BOARD for some devices
                val board = Build.BOARD.lowercase()
                for ((chipset, modelFile) in PLATFORM_MODELS) {
                    if (board.contains(chipset.lowercase())) {
                        Timber.i("Using optimized model for $chipset (from board): $modelFile")
                        return modelFile
                    }
                }

                Timber.i("Using generic model (no optimized variant for $socModel)")
                return MODEL_FILENAME_GENERIC
            }

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

                val magnitude = sqrt(norm1) * sqrt(norm2)
                return if (magnitude.isFinite() && magnitude > 0f) dotProduct / magnitude else 0f
            }

            /**
             * Truncates an embedding to a smaller dimension using Matryoshka Representation Learning.
             * The truncated embedding should be re-normalized for optimal performance.
             *
             * @param embedding The original 768-dim embedding.
             * @param targetDimension The target dimension (128, 256, 384, or 512).
             * @return The truncated and normalized embedding.
             */
            fun truncateEmbedding(
                embedding: FloatArray,
                targetDimension: Int,
            ): FloatArray {
                require(targetDimension <= embedding.size) {
                    "Target dimension ($targetDimension) must be <= embedding size (${embedding.size})"
                }
                require(targetDimension in listOf(128, 256, 384, 512, 768)) {
                    "Target dimension must be one of: 128, 256, 384, 512, 768"
                }

                val truncated = embedding.copyOfRange(0, targetDimension)
                return normalize(truncated)
            }

            /**
             * L2-normalizes an embedding vector.
             */
            fun normalize(embedding: FloatArray): FloatArray {
                var sumSquares = 0f
                for (value in embedding) {
                    sumSquares += value * value
                }
                val norm = sqrt(sumSquares)

                if (norm > 0f) {
                    for (i in embedding.indices) {
                        embedding[i] /= norm
                    }
                }
                return embedding
            }
        }
    }
