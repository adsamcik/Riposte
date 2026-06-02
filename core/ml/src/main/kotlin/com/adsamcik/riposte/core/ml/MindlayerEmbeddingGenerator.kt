package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.net.Uri
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.EmbeddingHandle
import com.adsamcik.mindlayer.sdk.EmbeddingTask
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [EmbeddingGenerator] backed by the Mindlayer SDK.
 *
 * Delegates all embedding inference to the Mindlayer on-device service. The
 * service runs EmbeddingGemma-300M under the hood, so output dimensionality
 * (768) and semantic quality match the previous direct-LiteRT implementation,
 * with the added benefits of shared model state across apps, thermal-aware
 * backend selection, and OOM-resilient client-side history.
 *
 * # Asymmetric prompts
 *
 * Mindlayer's [EmbeddingTask] selects the service-side prompt prefix
 * (Retrieval Document vs Retrieval Query), so the previous string-concatenation
 * `"title: ... | text: ..."` formatting is no longer needed — the service
 * handles it. Titles are folded into the document text upstream by
 * [com.adsamcik.riposte.core.ml.worker.EmbeddingSlotGenerator].
 *
 * # Image embeddings (unsupported)
 *
 * Mindlayer does not expose a direct image-to-embedding API; image-derived
 * content embeddings should be generated from the meme's pre-computed
 * description (produced by the CLI annotation tool) rather than at runtime.
 * The image methods throw [UnsupportedOperationException] as a defensive
 * guard against accidental reintroduction of an image-embedding path.
 *
 * # Initialization error reporting
 *
 * If the Mindlayer service is unavailable (not installed, not approved, bind
 * timed out, embedding capability not advertised), [initializationError]
 * returns a user-facing message and [isReady] returns false. Callers must
 * check [initializationError] before scheduling indexing work.
 */
@Singleton
class MindlayerEmbeddingGenerator
    @Inject
    constructor(
        private val mindlayerClient: MindlayerClient,
    ) : EmbeddingGenerator {
        private val initMutex = Mutex()

        @Volatile
        private var cachedSession: MindlayerSession? = null

        @Volatile
        private var _initializationError: String? = null
        override val initializationError: String? get() = _initializationError

        override val embeddingDimension: Int = EMBEDDING_GEMMA_DIMENSION

        override val modelVersion: String = EmbeddingModelVersionManager.CURRENT_VERSION

        override suspend fun generateFromText(text: String): FloatArray =
            generateFromText(text, title = null)

        override suspend fun generateFromText(
            text: String,
            title: String?,
        ): FloatArray =
            withContext(Dispatchers.Default) {
                val payload = composeDocumentPayload(text, title)
                if (payload.isBlank()) return@withContext createZeroEmbedding()
                runEmbedding(payload, EmbeddingTask.RetrievalDocument)
            }

        override suspend fun generateFromQuery(query: String): FloatArray =
            withContext(Dispatchers.Default) {
                if (query.isBlank()) return@withContext createZeroEmbedding()
                runEmbedding(query, EmbeddingTask.RetrievalQuery)
            }

        override suspend fun generateFromImage(bitmap: Bitmap): FloatArray =
            throw UnsupportedOperationException(
                "Mindlayer does not support direct image-to-embedding. Generate the " +
                    "description with the annotation pipeline and embed the text instead.",
            )

        override suspend fun generateFromUri(uri: Uri): FloatArray =
            throw UnsupportedOperationException(
                "Mindlayer does not support direct image-to-embedding. Generate the " +
                    "description with the annotation pipeline and embed the text instead.",
            )

        override suspend fun isReady(): Boolean = cachedSession?.supportsEmbeddings == true

        override suspend fun initialize() {
            ensureSession()
        }

        override fun close() {
            cachedSession = null
        }

        /**
         * Ensure the Mindlayer session is bound and embeddings are advertised.
         * Caches the typed [MindlayerSession] on success; on failure, populates
         * [_initializationError] so callers can show a user-facing message.
         */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun ensureSession(): MindlayerSession? {
            cachedSession?.let { return it }
            return initMutex.withLock {
                cachedSession?.let { return@withLock it }
                try {
                    val session = mindlayerClient.awaitMindlayer()
                    if (!session.supportsEmbeddings) {
                        _initializationError = "Mindlayer service does not advertise embeddings " +
                            "(${ServiceCapabilities.FEATURE_EMBEDDINGS}). The embedding model " +
                            "pack may still be downloading."
                        Timber.w("Mindlayer connected but FEATURE_EMBEDDINGS not advertised")
                        return@withLock null
                    }
                    _initializationError = null
                    cachedSession = session
                    Timber.i("Mindlayer embeddings ready")
                    session
                } catch (e: MindlayerUnavailableException) {
                    _initializationError = "Mindlayer service unavailable: ${e.message}"
                    Timber.w(e, "Mindlayer embedding init failed")
                    null
                } catch (e: Exception) {
                    _initializationError = "Embedding init failed: ${e.message ?: e.javaClass.simpleName}"
                    Timber.w(e, "Unexpected error initializing Mindlayer embeddings")
                    null
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun runEmbedding(text: String, task: EmbeddingTask): FloatArray {
            val session = ensureSession()
                ?: throw IllegalStateException(
                    initializationError ?: "Mindlayer embeddings not initialized",
                )

            return try {
                val handle = session.mindlayer.embed { text(text, task = task) }
                (handle as EmbeddingHandle.Single).awaitVector()
            } catch (e: MindlayerException) {
                handleEmbeddingException(e)
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Mindlayer embedding call failed")
                throw e
            }
        }

        private fun handleEmbeddingException(e: MindlayerException) {
            when (e.code) {
                MindlayerErrorCode.NOT_SUPPORTED,
                MindlayerErrorCode.FEATURE_NOT_SUPPORTED,
                MindlayerErrorCode.SERVICE_UNAVAILABLE,
                MindlayerErrorCode.EMBEDDING_DISABLED,
                MindlayerErrorCode.EMBEDDING_MODEL_UNAVAILABLE,
                -> {
                    _initializationError = "Mindlayer embedding feature is not available: ${e.message}"
                    cachedSession = null
                }
                MindlayerErrorCode.SERVICE_THROTTLED,
                MindlayerErrorCode.TRANSIENT_RESOURCE_EXHAUSTED,
                -> Timber.w(e, "Mindlayer transiently unavailable; will retry on next call")
                else -> Timber.w(e, "Mindlayer embedding error code=%d", e.code)
            }
        }

        private fun composeDocumentPayload(text: String, title: String?): String {
            val cleanTitle = title?.takeIf { it.isNotBlank() }
            val cleanText = text.takeIf { it.isNotBlank() }
            return when {
                cleanTitle != null && cleanText != null -> "$cleanTitle. $cleanText"
                cleanTitle != null -> cleanTitle
                cleanText != null -> cleanText
                else -> ""
            }
        }

        private fun createZeroEmbedding(): FloatArray = FloatArray(embeddingDimension)

        private companion object {
            const val EMBEDDING_GEMMA_DIMENSION = 768
        }
    }

