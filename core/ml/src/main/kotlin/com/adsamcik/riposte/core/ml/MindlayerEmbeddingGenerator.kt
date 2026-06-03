package com.adsamcik.riposte.core.ml

import android.graphics.Bitmap
import android.net.Uri
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.EmbeddingHandle
import com.adsamcik.mindlayer.sdk.EmbeddingTask
import com.adsamcik.mindlayer.sdk.MindlayerException
import com.adsamcik.mindlayer.shared.MindlayerErrorCode
import kotlinx.coroutines.Dispatchers
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
 * # Caching
 *
 * Connection state and capability negotiation are owned by [MindlayerClient];
 * this class is a thin call-site adapter that delegates per-call. That
 * delegation is intentional: it means that if Mindlayer becomes available
 * mid-session (e.g. the user just approved Riposte in the dashboard, or the
 * embedding asset pack finished extracting), the very next embedding call
 * succeeds without requiring a Riposte restart.
 *
 * # Asymmetric prompts
 *
 * Mindlayer's [EmbeddingTask] selects the service-side prompt prefix
 * (Retrieval Document vs Retrieval Query), so the previous client-side
 * `"title: ... | text: ..."` / `"task: search result | query: ..."` formatting
 * is no longer needed — the service handles it. Titles are folded into the
 * document text upstream by [com.adsamcik.riposte.core.ml.worker.EmbeddingSlotGenerator]
 * before reaching us.
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

        /**
         * Probe whether embeddings are currently usable. Returns the live
         * capability state — does NOT re-attempt to bind, so it's safe to call
         * cheaply from UI gating code.
         */
        override suspend fun isReady(): Boolean =
            when (val state = mindlayerClient.availability.value) {
                is MindlayerAvailability.Ready ->
                    state.capabilities.supports(ServiceCapabilities.FEATURE_EMBEDDINGS)
                else -> false
            }

        override suspend fun initialize() {
            // Best-effort warm-up: try to bind so the first user-visible
            // embedding call doesn't pay the connect latency. We deliberately
            // swallow the failure here — initializationError is the reporting
            // channel, and the real embedding call will retry on its own.
            ensureReady()
        }

        override fun close() {
            // No per-generator resources to release — Mindlayer connection is owned by MindlayerClient.
        }

        /**
         * Try to bind to Mindlayer and confirm embeddings are advertised.
         * Returns the session on success, null on failure (with
         * [_initializationError] populated for UI surfacing).
         */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun ensureReady(): MindlayerSession? {
            return try {
                val session = mindlayerClient.awaitMindlayer()
                if (!session.supportsEmbeddings) {
                    _initializationError = "Mindlayer service does not advertise embeddings " +
                        "(${ServiceCapabilities.FEATURE_EMBEDDINGS}). The embedding model " +
                        "pack may still be downloading."
                    Timber.w("Mindlayer connected but FEATURE_EMBEDDINGS not advertised")
                    null
                } else {
                    _initializationError = null
                    session
                }
            } catch (e: MindlayerUnavailableException) {
                _initializationError = "Mindlayer service unavailable: ${e.message}"
                Timber.d("Mindlayer embedding bind failed: %s", e.message)
                null
            } catch (e: Exception) {
                _initializationError = "Embedding init failed: ${e.message ?: e.javaClass.simpleName}"
                Timber.w(e, "Unexpected error binding to Mindlayer for embeddings")
                null
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun runEmbedding(text: String, task: EmbeddingTask): FloatArray {
            val session = ensureReady()
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
                ->
                    _initializationError = "Mindlayer embedding feature is not available: ${e.message}"
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
