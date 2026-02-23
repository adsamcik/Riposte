package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
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
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Embedding generator using Google's EmbeddingGemma model via LiteRT (CompiledModel API).
 *
 * EmbeddingGemma is a 308M parameter embedding model that produces high-quality
 * 768-dimensional embeddings. It supports Matryoshka Representation Learning (MRL),
 * allowing truncation to 512, 384, 256, or 128 dimensions with re-normalization.
 *
 * This implementation provides:
 * - GPU-accelerated inference (with CPU fallback)
 * - 768-dimension embeddings (best quality)
 * - 100+ language support
 * - Custom SentencePiece BPE tokenization (Viterbi, HashMap-based, ~40MB)
 *
 * Model files required:
 * - embeddinggemma-300M_seq512_mixed-precision.tflite (~179MB)
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

        /** The LiteRT CompiledModel instance, lazily initialized. */
        private var compiledModel: CompiledModel? = null

        /** The SentencePiece tokenizer, lazily initialized. */
        private var tokenizer: SentencePieceTokenizer? = null

        /** Cached input/output buffers to avoid per-inference native allocation. */
        private var cachedInputBuffers: List<com.google.ai.edge.litert.TensorBuffer>? = null
        private var cachedOutputBuffers: List<com.google.ai.edge.litert.TensorBuffer>? = null

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

        override val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION

        override suspend fun generateFromText(text: String): FloatArray =
            withContext(Dispatchers.Default) {
                if (text.isBlank()) {
                    return@withContext createZeroEmbedding()
                }

                mutex.withLock {
                    ensureInitialized()

                    val model =
                        compiledModel
                            ?: throw IllegalStateException(
                                "EmbeddingGemma model not initialized. " +
                                    "Model files may be missing.",
                            )

                    try {
                        runInference(model, text)
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
                        Timber.e(e, "Failed to generate text embedding")
                        throw e
                    }
                }
            }

        /**
         * Generates an embedding for a document (meme description, tags, etc.).
         *
         * @param text The document text to embed.
         * @param title Optional title prepended to text for richer context.
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

                val inputText =
                    if (!title.isNullOrBlank()) "$title: $text" else text

                mutex.withLock {
                    ensureInitialized()

                    val model =
                        compiledModel
                            ?: throw IllegalStateException(
                                "EmbeddingGemma model not initialized. " +
                                    "Model files may be missing.",
                            )

                    try {
                        runInference(model, inputText)
                    } catch (
                        @Suppress("TooGenericExceptionCaught")
                        e: Exception,
                    ) {
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
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
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
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to generate embedding from URI")
                    throw e
                }
            }

        override suspend fun isReady(): Boolean =
            mutex.withLock {
                compiledModel != null
            }

        override suspend fun initialize() {
            mutex.withLock {
                if (compiledModel != null) {
                    Timber.d("EmbeddingGemma already initialized")
                    return
                }

                initializeEmbeddingModel()
            }
        }

        override fun close() {
            // Acquire mutex to wait for any in-flight inference to complete,
            // preventing use-after-free of the native CompiledModel handle.
            runBlocking {
                mutex.withLock {
                    cachedInputBuffers = null
                    cachedOutputBuffers = null
                    compiledModel?.close()
                    compiledModel = null
                    tokenizer = null
                    initializationAttempted = false
                }
            }
            _imageLabeler?.close()
            _imageLabeler = null
        }

        /**
         * Ensures the embedding model is initialized.
         * Must be called while holding the mutex.
         */
        private fun ensureInitialized() {
            if (compiledModel == null && !initializationAttempted) {
                initializeEmbeddingModel()
            }
        }

        /**
         * Initializes the LiteRT CompiledModel and SentencePiece tokenizer.
         * Must be called while holding the mutex.
         */
        private fun initializeEmbeddingModel() {
            initializationAttempted = true

            try {
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

                initializeTokenizer(tokenizerPath)

                if (tokenizer == null) {
                    // Tokenizer failed to load — skip model initialization
                    return
                }

                if (!tryInitializeWithGpu(modelPath) && shouldUseGpu()) {
                    initializeWithCpu(modelPath)
                }
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Native library not available for EmbeddingGemma (unsupported ABI?)")
                compiledModel = null
                _initializationError = ERROR_NOT_COMPATIBLE
            } catch (e: ExceptionInInitializerError) {
                Timber.e(e, "EmbeddingGemma static initialization failed")
                compiledModel = null
                _initializationError = ERROR_FAILED_TO_LOAD
            }
        }

        private fun initializeTokenizer(tokenizerPath: String) {
            Timber.d("Loading SentencePiece tokenizer from: $tokenizerPath")
            try {
                tokenizer = SentencePieceModelParser.parse(File(tokenizerPath))
                Timber.i("SentencePiece tokenizer loaded")
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.e(e, "Failed to parse SentencePiece tokenizer")
                tokenizer = null
                _initializationError = ERROR_FAILED_TO_LOAD
            }
        }

        private fun tryInitializeWithGpu(modelPath: String): Boolean {
            return try {
                val accelerator = if (shouldUseGpu()) Accelerator.GPU else Accelerator.CPU
                Timber.d("Initializing EmbeddingGemma with LiteRT (accelerator=$accelerator)")
                Timber.d("Model path: $modelPath")

                compiledModel =
                    CompiledModel.create(
                        modelPath,
                        CompiledModel.Options(accelerator),
                    )

                Timber.i("EmbeddingGemma initialized successfully (dimension: $embeddingDimension)")
                _initializationError = null
                true
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.e(e, "Failed to initialize EmbeddingGemma")
                if (!shouldUseGpu()) {
                    compiledModel = null
                    _initializationError = ERROR_INIT_FAILED
                }
                false
            }
        }

        private fun initializeWithCpu(modelPath: String) {
            Timber.i("Retrying with CPU...")
            useGpu = false
            try {
                compiledModel =
                    CompiledModel.create(
                        modelPath,
                        CompiledModel.Options(Accelerator.CPU),
                    )
                Timber.i("EmbeddingGemma initialized with CPU fallback")
                _initializationError = null
            } catch (cpuError: UnsatisfiedLinkError) {
                Timber.e(cpuError, "CPU fallback failed: native library not available")
                compiledModel = null
                _initializationError = ERROR_NOT_COMPATIBLE
            } catch (cpuError: ExceptionInInitializerError) {
                Timber.e(cpuError, "CPU fallback failed: static initialization error")
                compiledModel = null
                _initializationError = ERROR_FAILED_TO_LOAD
            } catch (
                @Suppress("TooGenericExceptionCaught")
                cpuError: Exception,
            ) {
                Timber.e(cpuError, "CPU fallback also failed")
                compiledModel = null
                _initializationError = ERROR_INIT_FAILED
            }
        }

        /**
         * Tokenizes text and runs inference through the LiteRT model.
         * Caches input/output buffers across calls to avoid per-inference native allocation.
         *
         * @param model The initialized CompiledModel.
         * @param text The input text to embed.
         * @return The embedding vector as FloatArray.
         */
        private fun runInference(
            model: CompiledModel,
            text: String,
        ): FloatArray {
            val spTokenizer = requireNotNull(tokenizer) { "Tokenizer not initialized" }

            // Tokenize text using SentencePiece BPE
            val tokenIds = spTokenizer.encode(text)

            // Build token sequence: BOS + tokens, padded/truncated to MODEL_SEQ_LENGTH
            val paddedIds = IntArray(MODEL_SEQ_LENGTH) // filled with 0 (PAD)
            paddedIds[0] = BOS_TOKEN_ID
            val copyLen = minOf(tokenIds.size, MODEL_SEQ_LENGTH - 1) // -1 for BOS
            for (i in 0 until copyLen) {
                paddedIds[i + 1] = tokenIds[i]
            }
            val realTokenCount = 1 + copyLen // BOS + actual tokens

            // Reuse or create input/output buffers
            val inputBuffers = cachedInputBuffers ?: model.createInputBuffers().also {
                cachedInputBuffers = it
            }
            val outputBuffers = cachedOutputBuffers ?: model.createOutputBuffers().also {
                cachedOutputBuffers = it
            }

            require(inputBuffers.isNotEmpty()) { "Model has no input tensors" }
            require(outputBuffers.isNotEmpty()) { "Model has no output tensors" }

            when (inputBuffers.size) {
                1 -> {
                    inputBuffers[0].writeInt(paddedIds)
                }
                2 -> {
                    // Second input is attention_mask: 1 for real tokens, 0 for padding
                    val attentionMask = IntArray(MODEL_SEQ_LENGTH)
                    for (i in 0 until realTokenCount) {
                        attentionMask[i] = 1
                    }
                    inputBuffers[0].writeInt(paddedIds)
                    inputBuffers[1].writeInt(attentionMask)
                }
                else -> {
                    error("Unsupported input tensor count: ${inputBuffers.size}")
                }
            }

            model.run(inputBuffers, outputBuffers)

            val embedding = outputBuffers[0].readFloat()
            check(embedding.size == embeddingDimension) {
                "Model output dimension mismatch: expected $embeddingDimension, got ${embedding.size}"
            }
            return embedding
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
                Timber.d("Copied asset: $assetName (${targetFile.length() / BYTES_PER_KB / BYTES_PER_KB} MB)")
                true
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.d(e, "Asset not found in assets: $assetName")
                false
            }
        }

        /**
         * Gets the path to the EmbeddingGemma model file.
         * Tries platform-specific optimized model first, falls back to generic.
         */
        private fun getModelPath(): String {
            val modelDir = File(context.filesDir, MODEL_DIRECTORY)
            val bestModelFile = getBestModelFilename()
            val optimizedPath = File(modelDir, bestModelFile)
            val genericPath = File(modelDir, MODEL_FILENAME_GENERIC)

            return when {
                optimizedPath.exists() -> {
                    Timber.d("Using optimized model: $bestModelFile")
                    optimizedPath.absolutePath
                }
                genericPath.exists() -> {
                    Timber.d("Using generic model (optimized not found)")
                    genericPath.absolutePath
                }
                // Return expected path for error messaging
                else -> optimizedPath.absolutePath
            }
        }

        /**
         * Gets the path to the SentencePiece tokenizer file.
         */
        private fun getTokenizerPath(): String {
            val modelDir = File(context.filesDir, MODEL_DIRECTORY)
            return File(modelDir, TOKENIZER_FILENAME).absolutePath
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
             * LiteRT's GPU delegate may fatally abort if OpenCL is not available.
             * We proactively check to avoid passing GPU accelerator on unsupported devices.
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

            /** Model sequence length (must match the TFLite model's input shape). */
            private const val MODEL_SEQ_LENGTH = 512

            /** BOS (beginning-of-sequence) token ID for EmbeddingGemma's SentencePiece vocabulary. */
            private const val BOS_TOKEN_ID = 2

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

            /** Bytes per kilobyte for file size logging. */
            private const val BYTES_PER_KB = 1024

            /** Valid dimensions for Matryoshka Representation Learning truncation. */
            private val VALID_TRUNCATION_DIMENSIONS = listOf(128, 256, 384, 512, DEFAULT_EMBEDDING_DIMENSION)

            /**
             * Detects the device's SoC model and returns the best model filename.
             * Falls back to generic model if no optimized variant is available.
             */
            fun getBestModelFilename(): String {
                val socModel = Build.SOC_MODEL.lowercase()
                Timber.d("Detected SoC: $socModel")

                val socMatch = PLATFORM_MODELS.entries.firstOrNull { (chipset, _) ->
                    socModel.contains(chipset.lowercase())
                }
                val match = socMatch ?: PLATFORM_MODELS.entries.firstOrNull { (chipset, _) ->
                    Build.BOARD.lowercase().contains(chipset.lowercase())
                }

                return when {
                    socMatch != null -> {
                        Timber.i("Using optimized model for ${socMatch.key}: ${socMatch.value}")
                        socMatch.value
                    }
                    match != null -> {
                        Timber.i("Using optimized model for ${match.key} (from board): ${match.value}")
                        match.value
                    }
                    else -> {
                        Timber.i("Using generic model (no optimized variant for $socModel)")
                        MODEL_FILENAME_GENERIC
                    }
                }
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
                require(targetDimension in VALID_TRUNCATION_DIMENSIONS) {
                    "Target dimension must be one of: $VALID_TRUNCATION_DIMENSIONS"
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
