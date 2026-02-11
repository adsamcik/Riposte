package com.adsamcik.riposte.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Base interface for fake repositories used in testing.
 *
 * Provides common functionality for simulating repository behavior including
 * error simulation and emission control.
 *
 * @param T The type of data managed by this repository.
 */
interface FakeRepository<T> {
    /**
     * Flow of items in the repository.
     */
    val items: Flow<List<T>>

    /**
     * Emits a list of items to observers.
     */
    suspend fun emit(items: List<T>)

    /**
     * Configures the repository to throw an exception on the next operation.
     */
    fun setError(exception: Throwable?)

    /**
     * Clears all items from the repository.
     */
    suspend fun clear()
}

/**
 * Abstract base class for fake repositories that provides common implementation.
 *
 * This class handles the boilerplate of managing a [MutableStateFlow] of items
 * and simulating errors, allowing subclasses to focus on domain-specific logic.
 *
 * Usage:
 * ```kotlin
 * class FakeMemeRepository : BaseFakeRepository<Meme>() {
 *     suspend fun getMemeById(id: String): Meme? {
 *         throwIfError()
 *         return currentItems.find { it.id == id }
 *     }
 * }
 * ```
 *
 * @param T The type of data managed by this repository.
 */
abstract class BaseFakeRepository<T> : FakeRepository<T> {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    override val items: Flow<List<T>> = _items.asStateFlow()

    /**
     * The current list of items. Use this in subclass methods for synchronous access.
     */
    protected val currentItems: List<T>
        get() = _items.value

    private var simulatedError: Throwable? = null

    override suspend fun emit(items: List<T>) {
        _items.value = items
    }

    override fun setError(exception: Throwable?) {
        simulatedError = exception
    }

    override suspend fun clear() {
        _items.value = emptyList()
    }

    /**
     * Throws the configured error if one has been set.
     * Call this at the start of repository methods to simulate failures.
     */
    protected fun throwIfError() {
        simulatedError?.let { error ->
            simulatedError = null // Reset after throwing
            throw error
        }
    }

    /**
     * Updates the items using a transform function.
     */
    protected fun updateItems(transform: (List<T>) -> List<T>) {
        _items.update(transform)
    }

    /**
     * Adds an item to the repository.
     */
    protected fun addItem(item: T) {
        _items.update { it + item }
    }

    /**
     * Removes an item from the repository.
     */
    protected fun removeItem(predicate: (T) -> Boolean) {
        _items.update { items -> items.filterNot(predicate) }
    }

    /**
     * Replaces an item in the repository.
     */
    protected fun replaceItem(
        predicate: (T) -> Boolean,
        newItem: T,
    ) {
        _items.update { items ->
            items.map { if (predicate(it)) newItem else it }
        }
    }
}

/**
 * A fake repository with event emission capabilities.
 *
 * Useful for testing repositories that emit one-shot events
 * in addition to maintaining a list of items.
 *
 * @param T The type of data managed by this repository.
 * @param E The type of events that can be emitted.
 */
abstract class BaseFakeRepositoryWithEvents<T, E> : BaseFakeRepository<T>() {
    private val _events = MutableSharedFlow<E>()

    /**
     * Flow of events emitted by the repository.
     */
    val events: Flow<E> = _events.asSharedFlow()

    /**
     * Emits an event to observers.
     */
    protected suspend fun emitEvent(event: E) {
        _events.emit(event)
    }
}

/**
 * Extension function to get a flow of a single item by predicate.
 */
fun <T> FakeRepository<T>.itemFlow(predicate: (T) -> Boolean): Flow<T?> = items.map { list -> list.find(predicate) }

/**
 * Extension function to get a flow of items matching a predicate.
 */
fun <T> FakeRepository<T>.filteredItems(predicate: (T) -> Boolean): Flow<List<T>> =
    items.map { list -> list.filter(predicate) }
