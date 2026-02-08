package com.adsamcik.riposte.core.testing

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Interface for providing coroutine dispatchers.
 *
 * This abstraction allows for easy swapping between production and test dispatchers,
 * enabling proper testing of code that uses coroutines.
 *
 * In production, inject the [DefaultDispatcherProvider].
 * In tests, inject the [TestDispatcherProvider].
 */
interface DispatcherProvider {
    /**
     * Dispatcher for CPU-intensive work.
     * Maps to [kotlinx.coroutines.Dispatchers.Default] in production.
     */
    val default: CoroutineDispatcher

    /**
     * Dispatcher for UI operations.
     * Maps to [kotlinx.coroutines.Dispatchers.Main] in production.
     */
    val main: CoroutineDispatcher

    /**
     * Dispatcher for immediate UI operations.
     * Maps to [kotlinx.coroutines.Dispatchers.Main.immediate] in production.
     */
    val mainImmediate: CoroutineDispatcher

    /**
     * Dispatcher for I/O operations (disk, network).
     * Maps to [kotlinx.coroutines.Dispatchers.IO] in production.
     */
    val io: CoroutineDispatcher

    /**
     * Dispatcher that doesn't confine execution to any specific thread.
     * Maps to [kotlinx.coroutines.Dispatchers.Unconfined] in production.
     */
    val unconfined: CoroutineDispatcher
}

/**
 * Production implementation of [DispatcherProvider] that uses the standard
 * Kotlin coroutine dispatchers.
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
    override val main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main.immediate
    override val io: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    override val unconfined: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
}

/**
 * Test implementation of [DispatcherProvider] that uses test dispatchers.
 *
 * All dispatchers are backed by the same [TestDispatcher] to ensure
 * predictable execution order in tests.
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun testSomething() = runTest {
 *     val testDispatcherProvider = TestDispatcherProvider(testScheduler)
 *     val viewModel = MyViewModel(testDispatcherProvider)
 *     // Test code
 * }
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use for all dispatchers.
 *                       Defaults to [UnconfinedTestDispatcher] for eager execution.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val default: CoroutineDispatcher = testDispatcher
    override val main: CoroutineDispatcher = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
    override val io: CoroutineDispatcher = testDispatcher
    override val unconfined: CoroutineDispatcher = testDispatcher
}

/**
 * Creates a [TestDispatcherProvider] with a [StandardTestDispatcher].
 *
 * Use this when you need controlled execution, allowing you to manually
 * advance the virtual time or run pending coroutines.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun standardTestDispatcherProvider(): TestDispatcherProvider =
    TestDispatcherProvider(StandardTestDispatcher())

/**
 * Creates a [TestDispatcherProvider] with an [UnconfinedTestDispatcher].
 *
 * Use this when you want coroutines to execute eagerly, which is suitable
 * for most unit tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun unconfinedTestDispatcherProvider(): TestDispatcherProvider =
    TestDispatcherProvider(UnconfinedTestDispatcher())
