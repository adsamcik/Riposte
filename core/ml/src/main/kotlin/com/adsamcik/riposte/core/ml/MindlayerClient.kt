package com.adsamcik.riposte.core.ml

import android.content.Context
import com.adsamcik.mindlayer.ServiceCapabilities
import com.adsamcik.mindlayer.sdk.Capabilities
import com.adsamcik.mindlayer.sdk.HistoryPolicy
import com.adsamcik.mindlayer.sdk.Mindlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
 * Mindlayer runs as a separate on-device service app that exposes LLM,
 * embedding, and OCR capabilities over AIDL. This client wraps the SDK
 * lifecycle and exposes a coarse [availability] state so feature code can
 * degrade gracefully when the service is not installed, not yet approved,
 * or missing a required capability.
 *
 * # Lifecycle
 *
 * Construction triggers an asynchronous bind. Callers can:
 * - check [availability] for the current state (suitable for UI gating), or
 * - call [awaitMindlayer] to suspend until the binder is ready and receive a
 *   typed client plus the negotiated [Capabilities].
 *
 * Bind failures are non-fatal: [availability] transitions to
 * [MindlayerAvailability.Unavailable] and the rest of the app continues to
 * work without Mindlayer-backed features.
 *
 * # Failure cooldown
 *
 * After a bind failure, [awaitMindlayer] short-circuits subsequent calls
 * with [MindlayerUnavailableException] for [FAILURE_COOLDOWN] without
 * re-entering the SDK. Without this, every search / OCR / indexing call
 * would block on a 15-second timeout while the service is missing, which
 * users perceive as the app freezing. The cooldown is short enough that
 * fresh installs / approvals are picked up promptly.
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

        /** Wall-clock ms at which the most recent failure's cooldown expires. 0 = no cooldown. */
        @Volatile
        private var failureCooldownEndsAt: Long = 0L

        /** Cached failure message, surfaced via the short-circuit fast path. */
        @Volatile
        private var lastFailureMessage: String? = null

        private val _availability =
            MutableStateFlow<MindlayerAvailability>(MindlayerAvailability.Connecting)

        /** Observable availability state. Use to gate Mindlayer-backed UI affordances. */
        val availability: StateFlow<MindlayerAvailability> = _availability.asStateFlow()

        /**
         * Suspend until the Mindlayer service binder is ready, then return the
         * live client plus its negotiated capabilities.
         *
         * @throws MindlayerUnavailableException when the bind times out or
         *   fails, or when a recent failure is still inside the cooldown
         *   window. Callers should catch this and fall back to non-Mindlayer
         *   code paths.
         */
        @Suppress("TooGenericExceptionCaught")
        suspend fun awaitMindlayer(timeout: Duration = DEFAULT_CONNECT_TIMEOUT): MindlayerSession {
            // Fast path: already connected.
            cachedCapabilities?.let { return MindlayerSession(mindlayer, it) }

            // Fast path: in cooldown after a recent failure — short-circuit to
            // avoid pinning the caller on the SDK's connect timeout. Callers
            // can retry after the cooldown expires.
            val now = System.currentTimeMillis()
            if (now < failureCooldownEndsAt) {
                throw MindlayerUnavailableException(
                    "Mindlayer service is not available: ${lastFailureMessage ?: "previous bind failed"}",
                )
            }

            return initMutex.withLock {
                // Re-check inside the lock — another waiter may have raced.
                cachedCapabilities?.let { return@withLock MindlayerSession(mindlayer, it) }
                val now2 = System.currentTimeMillis()
                if (now2 < failureCooldownEndsAt) {
                    throw MindlayerUnavailableException(
                        "Mindlayer service is not available: ${lastFailureMessage ?: "previous bind failed"}",
                    )
                }

                try {
                    val caps = mindlayer.awaitConnected(timeout)
                    cachedCapabilities = caps
                    failureCooldownEndsAt = 0L
                    lastFailureMessage = null
                    _availability.value = MindlayerAvailability.Ready(caps)
                    Timber.i(
                        "Mindlayer connected: features=%s",
                        caps.supportedFeatures.joinToString(),
                    )
                    MindlayerSession(mindlayer, caps)
                } catch (e: CancellationException) {
                    // Cooperative cancellation (e.g. caller scope was cancelled
                    // while we were waiting on awaitConnected) must not trip the
                    // failure cooldown — it's a control-flow signal, not a
                    // service-availability problem.
                    throw e
                } catch (e: Exception) {
                    val reason = e.message ?: e.javaClass.simpleName
                    lastFailureMessage = reason
                    failureCooldownEndsAt = System.currentTimeMillis() + FAILURE_COOLDOWN.inWholeMilliseconds
                    _availability.value = MindlayerAvailability.Unavailable(reason = reason)
                    Timber.w(e, "Mindlayer bind failed — degraded mode for next %s", FAILURE_COOLDOWN)
                    throw MindlayerUnavailableException(
                        "Mindlayer service is not available: $reason",
                        cause = e,
                    )
                }
            }
        }

        /** Disconnect from the Mindlayer service. Idempotent. */
        fun disconnect() {
            mindlayer.disconnect()
            cachedCapabilities = null
            failureCooldownEndsAt = 0L
            lastFailureMessage = null
            _availability.value = MindlayerAvailability.Connecting
        }

        companion object {
            /**
             * Initial connect deadline. The SDK does its own internal backoff
             * on transient failures (e.g. `SERVICE_THROTTLED` with a 5s
             * `cooldownEndsAt` hint), so this needs to be generous enough to
             * absorb at least one such retry cycle. 60s gives the SDK 5-10
             * retries before we surface a timeout.
             */
            private val DEFAULT_CONNECT_TIMEOUT = 60.seconds

            /**
             * How long to short-circuit subsequent [awaitMindlayer] calls
             * after a bind failure. Tuned to be:
             *  - long enough that a single user gesture that fans out into
             *    many embedding/OCR calls doesn't pay the SDK timeout per
             *    call (avoiding a perceived freeze);
             *  - short enough that a user who just installed / approved the
             *    Mindlayer service sees AI features start working without
             *    having to restart Riposte.
             */
            private val FAILURE_COOLDOWN = 30.seconds
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
 * Thrown when Mindlayer is required but the service is unavailable (not
 * installed, not approved, bind timed out, etc.). Callers must catch and
 * degrade gracefully.
 */
class MindlayerUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
