package com.adsamcik.riposte.core.ml

import android.content.Context
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.Capabilities
import com.adsamcik.mindlayer.sdk.HistoryPolicy
import com.adsamcik.mindlayer.sdk.Mindlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Singleton owner of the Mindlayer SDK connection.
 *
 * Mindlayer runs as a separate on-device service app that exposes LLM, embedding,
 * and OCR capabilities over AIDL. This client wraps the SDK lifecycle and exposes
 * coarse availability state so feature code can degrade gracefully when the service
 * is not installed, not yet approved, or missing a required capability.
 *
 * # Lifecycle
 *
 * Construction triggers an asynchronous bind. Callers can:
 * - check [availability] for the current state (suitable for UI gating), or
 * - call [awaitMindlayer] to suspend until the binder is ready and receive a typed
 *   client plus the negotiated [Capabilities].
 *
 * Bind failures are non-fatal: [availability] transitions to [MindlayerAvailability.Unavailable]
 * and the rest of the app continues to work without Mindlayer-backed features.
 */
@Singleton
class MindlayerClient
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val mindlayer: Mindlayer =
            Mindlayer.connect(
                context = context,
                historyPolicy = HistoryPolicy.METADATA_ONLY,
            )

        private val initMutex = Mutex()

        @Volatile
        private var cachedCapabilities: Capabilities? = null

        private val _availability =
            MutableStateFlow<MindlayerAvailability>(MindlayerAvailability.Connecting)

        /** Observable availability state. Use to gate Mindlayer-backed UI affordances. */
        val availability: StateFlow<MindlayerAvailability> = _availability.asStateFlow()

        /**
         * Suspend until the Mindlayer service binder is ready, then return the live
         * client plus its negotiated capabilities.
         *
         * @throws MindlayerUnavailableException when the bind times out or fails. Callers
         *   should catch this and fall back to non-Mindlayer code paths.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun awaitMindlayer(timeout: Duration = DEFAULT_CONNECT_TIMEOUT): MindlayerSession {
            cachedCapabilities?.let { return MindlayerSession(mindlayer, it) }
            return initMutex.withLock {
                cachedCapabilities?.let { return@withLock MindlayerSession(mindlayer, it) }
                try {
                    val caps = mindlayer.awaitConnected(timeout)
                    cachedCapabilities = caps
                    _availability.value = MindlayerAvailability.Ready(caps)
                    Timber.i(
                        "Mindlayer connected: features=%s",
                        caps.supportedFeatures.joinToString(),
                    )
                    MindlayerSession(mindlayer, caps)
                } catch (e: Exception) {
                    _availability.value = MindlayerAvailability.Unavailable(reason = e.message ?: e.javaClass.simpleName)
                    Timber.w(e, "Mindlayer bind failed — falling back to degraded mode")
                    throw MindlayerUnavailableException(
                        "Mindlayer service is not available: ${e.message}",
                        cause = e,
                    )
                }
            }
        }

        /** Disconnect from the Mindlayer service. Idempotent. */
        fun disconnect() {
            mindlayer.disconnect()
            cachedCapabilities = null
            _availability.value = MindlayerAvailability.Connecting
        }

        companion object {
            private val DEFAULT_CONNECT_TIMEOUT = 15.seconds
        }
    }

/**
 * Successful Mindlayer bind result: a live client plus negotiated capabilities.
 */
data class MindlayerSession(
    val mindlayer: Mindlayer,
    val capabilities: Capabilities,
) {
    val supportsEmbeddings: Boolean
        get() = capabilities.supports(ServiceCapabilities.FEATURE_EMBEDDINGS)

    val supportsOcr: Boolean
        get() = capabilities.supports(ServiceCapabilities.FEATURE_OCR_SESSION)
}

/** Coarse Mindlayer availability for UI gating. */
sealed interface MindlayerAvailability {
    /** Bind in progress or not yet attempted. */
    data object Connecting : MindlayerAvailability

    /** Connected and capabilities negotiated. */
    data class Ready(val capabilities: Capabilities) : MindlayerAvailability

    /** Service is not installed, not approved, or bind failed. */
    data class Unavailable(val reason: String) : MindlayerAvailability
}

/**
 * Thrown when Mindlayer is required but the service is unavailable (not installed,
 * not approved, bind timed out, etc.). Callers must catch and degrade gracefully.
 */
class MindlayerUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
