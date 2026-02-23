package com.adsamcik.riposte.core.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight in-process event bus for cross-feature coordination.
 *
 * Built on [MutableSharedFlow] with no replay and a bounded buffer.
 * Events are ephemeral — they are NOT persisted and NOT guaranteed
 * to be delivered if no collector is active. Room Flows remain the
 * source of truth for data consistency; this bus only signals that
 * something happened so features can react promptly.
 *
 * Thread-safe: [emit] is a suspending function that respects
 * structured concurrency; [on] returns a cold Flow that can be
 * collected from any dispatcher.
 *
 * Usage:
 * ```kotlin
 * // Emit an event (from any coroutine scope)
 * eventBus.emit(MemeShared(memeId = 42))
 *
 * // Subscribe to a specific event type
 * eventBus.on<MemeShared>().collect { event ->
 *     refreshSuggestions()
 * }
 * ```
 */
@Singleton
class EventBus @Inject constructor() {

    private val _events = MutableSharedFlow<AppEvent>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Raw stream of all events. Prefer [on] for type-safe filtering. */
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    /**
     * Emit an event to all active collectors.
     *
     * Because [BufferOverflow.DROP_OLDEST] is configured, this call
     * never suspends — the oldest buffered event is dropped if the
     * buffer is full and no collector has consumed it yet.
     */
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    /**
     * Returns a [Flow] filtered to events of type [T].
     *
     * ```kotlin
     * eventBus.on<MemeShared>().collect { shared -> ... }
     * ```
     */
    inline fun <reified T : AppEvent> on(): Flow<T> =
        events.filterIsInstance<T>()

    companion object {
        /** Buffer size for events awaiting collection. */
        const val BUFFER_CAPACITY = 64
    }
}
