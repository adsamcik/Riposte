package com.adsamcik.riposte.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
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
class EmbeddingGemmaGenerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : EmbeddingGenerator {

    /** Lazily initialized image labeler for extracting features from images. */
    private var _imageLabeler: com.google.mlkit.vision.label.ImageLabeler? = null
    private val imageLabeler: com.google.mlkit.vision.label.ImageLabeler
        get() {
            if (_imageLabeler == null) {
                _imageLabeler = ImageLabeling.getClient(
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

    /** Whether GPU acceleration is enabled. */
    private var useGpu = true

    /** Executor for handling ListenableFuture callbacks. */
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    override val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION

    override suspend fun generateFromText(text: String): FloatArray = withContext(Dispatchers.Default) {
        if (text.isBlank()) {
            return@withContext createZeroEmbedding()
        }

        mutex.withLock {
            ensureInitialized()

            val model = embeddingModel
                ?: throw IllegalStateException("EmbeddingGemma model not initialized. Model files may be missing.")

            try {
                // Create embedding request with query task type
                val embedData = EmbedData.create(
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
                Log.e(TAG, "Failed to generate text embedding", e)
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
    suspend fun generateFromDocument(text: String, title: String? = null): FloatArray =
        withContext(Dispatchers.Default) {
            if (text.isBlank()) {
                return@withContext createZeroEmbedding()
            }

            mutex.withLock {
                ensureInitialized()

                val model = embeddingModel
                    ?: throw IllegalStateException("EmbeddingGemma model not initialized. Model files may be missing.")

                try {
                    // Create embedding request with document task type
                    val embedDataBuilder = EmbedData.builder<String>()
                        .setData(text)
                        .setTask(EmbedData.TaskType.RETRIEVAL_DOCUMENT)
                        .setIsQuery(false)

                    // Add title metadata if provided
                    if (!title.isNullOrBlank()) {
                        embedDataBuilder.setMetadata(
                            mapOf(GemmaEmbeddingModel.TITLE_KEY to title)
                        )
                    }

                    val request = EmbeddingRequest.create(listOf(embedDataBuilder.build()))

                    val future = model.getEmbeddings(request)
                    val embedding = awaitListenableFuture(future)

                    embedding.map { it }.toFloatArray()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to generate document embedding", e)
                    throw e
                }
            }
        }

    override suspend fun generateFromImage(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        try {
            val labels = getImageLabels(bitmap)

            if (labels.isEmpty()) {
                Log.d(TAG, "No labels detected in image, returning zero embedding")
                return@withContext createZeroEmbedding()
            }

            // Concatenate labels with spaces for embedding as a document
            val labelText = labels.joinToString(" ")
            generateFromDocument(labelText, title = "Image labels")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate image embedding", e)
            throw e
        }
    }

    override suspend fun generateFromUri(uri: Uri): FloatArray = withContext(Dispatchers.IO) {
        try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }

            if (bitmap == null) {
                Log.w(TAG, "Failed to decode bitmap from URI: $uri")
                return@withContext createZeroEmbedding()
            }

            try {
                generateFromImage(bitmap)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate embedding from URI", e)
            throw e
        }
    }

    override suspend fun isReady(): Boolean = mutex.withLock {
        embeddingModel != null
    }

    override suspend fun initialize() {
        mutex.withLock {
            if (embeddingModel != null) {
                Log.d(TAG, "EmbeddingGemma already initialized")
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
                Log.e(TAG, "EmbeddingGemma model not found at: $modelPath")
                Log.i(TAG, "Please download the model from HuggingFace: litert-community/embeddinggemma-300m")
                Log.i(TAG, "Or run: tools/download-embeddinggemma.ps1 -AllVariants")
                return
            }

            if (!File(tokenizerPath).exists()) {
                Log.e(TAG, "SentencePiece tokenizer not found at: $tokenizerPath")
                return
            }

            Log.d(TAG, "Initializing EmbeddingGemma with GPU=$useGpu")
            Log.d(TAG, "Model path: $modelPath")
            Log.d(TAG, "Tokenizer path: $tokenizerPath")

            embeddingModel = GemmaEmbeddingModel(
                modelPath,
                tokenizerPath,
                useGpu,
            )

            Log.i(TAG, "EmbeddingGemma initialized successfully (dimension: $embeddingDimension)")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native library not available for EmbeddingGemma (unsupported ABI?)", e)
            embeddingModel = null
        } catch (e: ExceptionInInitializerError) {
            Log.e(TAG, "EmbeddingGemma static initialization failed", e)
            embeddingModel = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize EmbeddingGemma", e)

            // Try CPU fallback if GPU fails
            if (useGpu) {
                Log.i(TAG, "Retrying with CPU...")
                useGpu = false
                try {
                    val modelPath = getModelPath()
                    val tokenizerPath = getTokenizerPath()

                    embeddingModel = GemmaEmbeddingModel(
                        modelPath,
                        tokenizerPath,
                        false,
                    )
                    Log.i(TAG, "EmbeddingGemma initialized with CPU fallback")
                } catch (cpuError: UnsatisfiedLinkError) {
                    Log.e(TAG, "CPU fallback failed: native library not available", cpuError)
                    embeddingModel = null
                } catch (cpuError: ExceptionInInitializerError) {
                    Log.e(TAG, "CPU fallback failed: static initialization error", cpuError)
                    embeddingModel = null
                } catch (cpuError: Exception) {
                    Log.e(TAG, "CPU fallback also failed", cpuError)
                    embeddingModel = null
                }
            } else {
                embeddingModel = null
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
            Log.i(TAG, "Copied optimized model from assets: $bestModel")
            return
        }

        // Fall back to generic model
        if (copyAssetIfNeeded(MODEL_FILENAME_GENERIC, modelDir)) {
            Log.i(TAG, "Copied generic model from assets: $MODEL_FILENAME_GENERIC")
        }
    }

    /**
     * Copies a single file from assets to the target directory if it doesn't exist.
     * @return true if file exists (either copied or already present), false if not available.
     */
    private fun copyAssetIfNeeded(assetName: String, targetDir: File): Boolean {
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
            Log.d(TAG, "Copied asset: $assetName (${targetFile.length() / 1024 / 1024} MB)")
            true
        } catch (e: Exception) {
            Log.d(TAG, "Asset not found: $assetName")
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
            Log.d(TAG, "Using optimized model: $bestModelFile")
            return optimizedPath.absolutePath
        }
        
        // Fall back to generic model
        val genericPath = File(modelDir, MODEL_FILENAME_GENERIC)
        if (genericPath.exists()) {
            Log.d(TAG, "Using generic model (optimized not found)")
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
    ): T = suspendCancellableCoroutine { continuation ->
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
                    Log.e(TAG, "ML Kit Image Labeling failed", exception)
                    continuation.resume(emptyList())
                }

            continuation.invokeOnCancellation {
                Log.d(TAG, "Image labeling task cancelled")
            }
        }
    }

    /**
     * Creates a zero-filled embedding array for graceful degradation.
     */
    private fun createZeroEmbedding(): FloatArray = FloatArray(embeddingDimension)

    companion object {
        private const val TAG = "EmbeddingGemma"

        /** Directory where model files are stored. */
        const val MODEL_DIRECTORY = "embedding_models"

        /** Generic EmbeddingGemma model filename (works on all devices). */
        const val MODEL_FILENAME_GENERIC = "embeddinggemma-300M_seq512_mixed-precision.tflite"

        /** Platform-specific model filenames for optimized performance. */
        private val PLATFORM_MODELS = mapOf(
            // Qualcomm Snapdragon
            "sm8550" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite",  // 8 Gen 2
            "sm8475" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8550.tflite",  // 8+ Gen 1 (use 8 Gen 2)
            "sm8650" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8650.tflite",  // 8 Gen 3
            "sm8750" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8750.tflite",  // 8 Gen 4 (Elite)
            "sm8850" to "embeddinggemma-300M_seq512_mixed-precision.qualcomm.sm8850.tflite",  // 8 Gen 5
            // MediaTek Dimensity
            "mt6991" to "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite",  // Dimensity 9300
            "mt6989" to "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6991.tflite",  // Dimensity 9200 (use 9300)
            "mt6993" to "embeddinggemma-300M_seq512_mixed-precision.mediatek.mt6993.tflite",  // Dimensity 9400
        )

        /** SentencePiece tokenizer filename. */
        const val TOKENIZER_FILENAME = "sentencepiece.model"

        /** Default embedding dimension for EmbeddingGemma (768 dimensions). */
        const val DEFAULT_EMBEDDING_DIMENSION = 768

        /** Minimum confidence threshold for image labels. */
        private const val IMAGE_LABEL_CONFIDENCE_THRESHOLD = 0.5f

        /**
         * Detects the device's SoC model and returns the best model filename.
         * Falls back to generic model if no optimized variant is available.
         */
        fun getBestModelFilename(): String {
            val socModel = Build.SOC_MODEL.lowercase()

            Log.d(TAG, "Detected SoC: $socModel")

            // Try to find a matching platform-specific model
            for ((chipset, modelFile) in PLATFORM_MODELS) {
                if (socModel.contains(chipset.lowercase())) {
                    Log.i(TAG, "Using optimized model for $chipset: $modelFile")
                    return modelFile
                }
            }

            // Also check Build.BOARD for some devices
            val board = Build.BOARD.lowercase()
            for ((chipset, modelFile) in PLATFORM_MODELS) {
                if (board.contains(chipset.lowercase())) {
                    Log.i(TAG, "Using optimized model for $chipset (from board): $modelFile")
                    return modelFile
                }
            }

            Log.i(TAG, "Using generic model (no optimized variant for $socModel)")
            return MODEL_FILENAME_GENERIC
        }

        /**
         * Computes cosine similarity between two embeddings.
         *
         * @param embedding1 First embedding vector.
         * @param embedding2 Second embedding vector.
         * @return Cosine similarity score between -1 and 1.
         */
        fun cosineSimilarity(embedding1: FloatArray, embedding2: FloatArray): Float {
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
            return if (magnitude > 0f) dotProduct / magnitude else 0f
        }

        /**
         * Truncates an embedding to a smaller dimension using Matryoshka Representation Learning.
         * The truncated embedding should be re-normalized for optimal performance.
         *
         * @param embedding The original 768-dim embedding.
         * @param targetDimension The target dimension (128, 256, 384, or 512).
         * @return The truncated and normalized embedding.
         */
        fun truncateEmbedding(embedding: FloatArray, targetDimension: Int): FloatArray {
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
