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
        private val acceleratorStrategy: AcceleratorStrategy,
    ) : EmbeddingGenerator {
        /** Lazily initialized image labeler for extracting features from images. */
        @Volatile
        private var _imageLabeler: com.google.mlkit.vision.label.ImageLabeler? = null
        private val imageLabelerLock = Any()
        private val imageLabeler: com.google.mlkit.vision.label.ImageLabeler
            get() {
                _imageLabeler?.let { return it }
                synchronized(imageLabelerLock) {
                    _imageLabeler?.let { return it }
                    return ImageLabeling.getClient(
                        ImageLabelerOptions.Builder()
                            .setConfidenceThreshold(IMAGE_LABEL_CONFIDENCE_THRESHOLD)
                            .build(),
                    ).also { _imageLabeler = it }
                }
            }

        /** Mutex to ensure thread-safe access to the embedding model. */
        private val mutex = Mutex()

        /** The LiteRT CompiledModel instance, lazily initialized. */
        private var compiledModel: CompiledModel? = null

        /** The SentencePiece tokenizer, lazily initialized. */
        private var tokenizer: SentencePieceTokenizer? = null

        /** Rust-native tokenizer (preferred over Kotlin for performance). */
        private var rustTokenizer: RustTokenizer? = null

        /** Cached input/output buffers to avoid per-inference native allocation. */
        private var cachedInputBuffers: List<com.google.ai.edge.litert.TensorBuffer>? = null
        private var cachedOutputBuffers: List<com.google.ai.edge.litert.TensorBuffer>? = null

        /** Flag indicating whether initialization has been attempted. */
        private var initializationAttempted = false

        private var _initializationError: String? = null
        override val initializationError: String? get() = _initializationError

        override val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION

        override val modelVersion: String = EmbeddingModelVersionManager.CURRENT_VERSION

        override suspend fun generateFromText(text: String): FloatArray =
            generateFromText(text, title = null)

        /**
         * Generates an embedding for document content using EmbeddingGemma's document prompt format.
         *
         * @param text The document text to embed.
         * @param title Optional title for richer context. If null/blank, "none" is used.
         * @return The embedding vector.
         */
        override suspend fun generateFromText(
            text: String,
            title: String?,
        ): FloatArray =
            withContext(Dispatchers.Default) {
                if (text.isBlank()) {
                    return@withContext createZeroEmbedding(embeddingDimension)
                }

                val titlePart = if (!title.isNullOrBlank()) title else "none"
                val formattedText = "title: $titlePart | text: $text"

                runLockedInference(formattedText, "document")
            }

        /**
         * Generates an embedding for a search query using EmbeddingGemma's query prompt format.
         *
         * @param query The search query text.
         * @return The embedding vector.
         */
        override suspend fun generateFromQuery(query: String): FloatArray =
            withContext(Dispatchers.Default) {
                if (query.isBlank()) {
                    return@withContext createZeroEmbedding(embeddingDimension)
                }

                val formattedText = "task: search result | query: $query"

                runLockedInference(formattedText, "query")
            }

        /**
         * Acquires the model mutex and runs inference with error handling.
         */
        private suspend fun runLockedInference(
            formattedText: String,
            kind: String,
        ): FloatArray =
            mutex.withLock {
                ensureInitialized()

                val model =
                    compiledModel
                        ?: throw IllegalStateException(
                            "EmbeddingGemma model not initialized. " +
                                "Model files may be missing.",
                        )

                try {
                    runInference(model, formattedText)
                } catch (e: GpuInferenceException) {
                    Timber.w(e, "GPU produced NaN output — falling back to CPU")
                    fallbackToCpuAndRetry(formattedText)
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception,
                ) {
                    Timber.e(e, "Failed to generate $kind embedding")
                    throw e
                }
            }

        /**
         * Re-initializes the model with CPU accelerator and retries inference.
         * Called when GPU inference produces NaN/Inf values. Must be called while holding the mutex.
         */
        private fun fallbackToCpuAndRetry(text: String): FloatArray {
            val modelPath = context.getEmbeddingGemmaModelPath()
            compiledModel?.close()
            compiledModel = null
            cachedInputBuffers = null
            cachedOutputBuffers = null

            Timber.i("Re-initializing EmbeddingGemma with CPU after GPU NaN failure")
            initializeWithCpu(modelPath)

            val model = checkNotNull(compiledModel) { "CPU fallback initialization failed" }
            return runInference(model, text)
        }

        override suspend fun generateFromImage(bitmap: Bitmap): FloatArray =
            withContext(Dispatchers.Default) {
                try {
                    val labels = getImageLabels(bitmap)

                    if (labels.isEmpty()) {
                        Timber.d("No labels detected in image, returning zero embedding")
                        return@withContext createZeroEmbedding(embeddingDimension)
                    }

                    // Concatenate labels with spaces for embedding as a document
                    val labelText = labels.joinToString(" ")
                    generateFromText(labelText, title = "Image labels")
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
                        return@withContext createZeroEmbedding(embeddingDimension)
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
                    rustTokenizer?.close()
                    rustTokenizer = null
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

                val modelPath = context.getEmbeddingGemmaModelPath()
                val tokenizerPath = context.getEmbeddingGemmaTokenizerPath()

                if (requiredModelFilesExist(modelPath, tokenizerPath)) {
                    initializeTokenizer(tokenizerPath)
                    if (tokenizer != null || rustTokenizer != null) {
                        initializeModelRuntime(modelPath)
                    }
                }

                // Safety net: if all init paths completed but model is still null, set error
                if (compiledModel == null && _initializationError == null) {
                    Timber.e("EmbeddingGemma initialization completed with no model and no error — setting error")
                    _initializationError = ERROR_INIT_FAILED
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

        private fun requiredModelFilesExist(
            modelPath: String,
            tokenizerPath: String,
        ): Boolean {
            val modelExists = File(modelPath).exists()
            val tokenizerExists = File(tokenizerPath).exists()
            if (!modelExists) {
                Timber.e("EmbeddingGemma model not found at: $modelPath")
                Timber.i("Please download the model from HuggingFace: litert-community/embeddinggemma-300m")
                Timber.i("Or run: tools/download-embeddinggemma.ps1 -AllVariants")
            }
            if (!tokenizerExists) {
                Timber.e("SentencePiece tokenizer not found at: $tokenizerPath")
            }
            if (!modelExists || !tokenizerExists) {
                _initializationError = ERROR_FILES_NOT_FOUND
            }
            return modelExists && tokenizerExists
        }

        private fun initializeModelRuntime(modelPath: String) {
            if (!tryInitializeWithGpu(modelPath)) {
                val genericPath = context.getGenericEmbeddingGemmaModelPath()
                if (genericPath != null && genericPath != modelPath) {
                    Timber.i("Optimized model failed — falling back to generic model on CPU")
                    initializeWithCpu(genericPath)
                } else if (acceleratorStrategy.getBestAccelerator() != Accelerator.CPU) {
                    initializeWithCpu(modelPath)
                }
            }
        }

        private fun initializeTokenizer(tokenizerPath: String) {
            Timber.d("Loading SentencePiece tokenizer from: $tokenizerPath")
            // Prefer Rust tokenizer for lower memory and faster encoding
            try {
                val modelBytes = File(tokenizerPath).readBytes()
                rustTokenizer = RustTokenizer.parse(modelBytes)
                Timber.i("Rust SentencePiece tokenizer loaded (vocab=%d)", rustTokenizer?.vocabSize())
                return
            } catch (e: UnsatisfiedLinkError) {
                Timber.w("Rust tokenizer unavailable — native library not loaded: %s", e.message)
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.w(e, "Rust tokenizer failed — falling back to Kotlin")
            }

            // Kotlin fallback
            try {
                tokenizer = SentencePieceModelParser.parse(File(tokenizerPath))
                Timber.i("Kotlin SentencePiece tokenizer loaded (fallback)")
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
                val accelerator = acceleratorStrategy.getBestAccelerator()
                Timber.d("Initializing EmbeddingGemma with LiteRT (accelerator=$accelerator)")
                Timber.d("Model path: $modelPath")

                val model =
                    CompiledModel.create(
                        modelPath,
                        CompiledModel.Options(accelerator),
                    )

                // Canary check: verify buffers can be created (catches DISPATCH_OP errors
                // where model loads but the runtime lacks required custom ops)
                val testOutputs = model.createOutputBuffers()
                val testInputs = model.createInputBuffers()

                compiledModel = model
                cachedOutputBuffers = testOutputs
                cachedInputBuffers = testInputs
                Timber.i("EmbeddingGemma initialized successfully (dimension: $embeddingDimension)")
                _initializationError = null
                true
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.e(e, "Failed to initialize EmbeddingGemma with preferred accelerator")
                false
            }
        }

        private fun initializeWithCpu(modelPath: String) {
            Timber.i("Retrying with CPU...")
            try {
                val model =
                    CompiledModel.create(
                        modelPath,
                        CompiledModel.Options(Accelerator.CPU),
                    )

                // Canary check: verify buffers can be created
                val testOutputs = model.createOutputBuffers()
                val testInputs = model.createInputBuffers()

                compiledModel = model
                cachedOutputBuffers = testOutputs
                cachedInputBuffers = testInputs
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
            // Tokenize text — prefer Rust, fall back to Kotlin
            val tokenIds: List<Int> = rustTokenizer?.encode(text)
                ?: requireNotNull(tokenizer) { "No tokenizer initialized (Rust or Kotlin)" }.encode(text)

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

            val normalized = EmbeddingUtils.normalize(embedding)

            // Detect NaN/Inf output from GPU — indicates driver or precision issue
            if (normalized.any { !it.isFinite() }) {
                throw GpuInferenceException(
                    "GPU inference produced NaN/Inf values (${normalized.count { !it.isFinite() }}/" +
                        "${normalized.size} non-finite)",
                )
            }

            return normalized
        }

        /**
         * Thrown when GPU inference produces non-finite (NaN/Inf) output values.
         * Triggers automatic fallback to CPU accelerator.
         */
        internal class GpuInferenceException(message: String) : RuntimeException(message)

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
         * Uses atomic write (temp file + rename) to prevent partial files from interrupted copies.
         * @return true if file exists (either copied or already present), false if not available.
         */
        private fun copyAssetIfNeeded(
            assetName: String,
            targetDir: File,
        ): Boolean {
            val targetFile = File(targetDir, assetName)
            if (targetFile.exists() && targetFile.length() > 0) {
                return true
            }
            // Remove any zero-byte leftover from a previously interrupted copy
            if (targetFile.exists()) {
                targetFile.delete()
            }

            return try {
                val tempFile = File(targetDir, "$assetName.tmp")
                context.assets.open("$MODEL_DIRECTORY/$assetName").use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                // Atomic rename — prevents partial files if interrupted before this point
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    tempFile.delete()
                    Timber.e("Failed to rename temp file to: $assetName")
                } else {
                    Timber.d("Copied asset: $assetName (${targetFile.length() / BYTES_PER_KB / BYTES_PER_KB} MB)")
                }
                renamed
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Timber.d(e, "Asset not found in assets: $assetName")
                // Clean up temp file on failure
                File(targetDir, "$assetName.tmp").delete()
                false
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

        companion object {

            /** Directory where model files are stored. */
            const val MODEL_DIRECTORY = "embedding_models"

            /** Generic EmbeddingGemma model filename (works on all devices). */
            const val MODEL_FILENAME_GENERIC = "embeddinggemma-300M_seq512_mixed-precision.tflite"

            /** Model sequence length (must match the TFLite model's input shape). */
            private const val MODEL_SEQ_LENGTH = 512

            /** BOS (beginning-of-sequence) token ID for EmbeddingGemma's SentencePiece vocabulary. */
            private const val BOS_TOKEN_ID = 2

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

            /**
             * Returns the generic model filename for all devices.
             *
             * SoC-specific AOT models are disabled because they contain DISPATCH_OP
             * custom ops requiring vendor NPU delegate libraries (.so) that aren't
             * bundled with the app. Until the NPU delegate integration is complete,
             * all devices use the generic model with GPU/CPU acceleration.
             *
             * TODO: Re-enable SoC-specific model selection when vendor NPU delegates
             *  are bundled and validated. The AOT models need matching delegate .so files
             *  on the device at runtime.
             */
            fun getBestModelFilename(): String {
                val socModel = Build.SOC_MODEL.lowercase()
                Timber.d("Detected SoC: $socModel (using generic model — AOT models disabled)")
                return MODEL_FILENAME_GENERIC
            }
        }
    }
