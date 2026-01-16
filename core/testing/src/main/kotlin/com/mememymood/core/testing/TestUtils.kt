package com.mememymood.core.testing

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Collection of test utility functions for common testing patterns.
 */

/**
 * Runs a test with a [MainDispatcherRule] automatically applied.
 *
 * This is a convenience wrapper around [runTest] that sets up the main dispatcher.
 *
 * Usage:
 * ```kotlin
 * @Test
 * fun myTest() = runTestWithMainDispatcher {
 *     val viewModel = MyViewModel()
 *     viewModel.someAction()
 *     advanceUntilIdle()
 *     assertThat(viewModel.state.value).isEqualTo(expected)
 * }
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runTestWithMainDispatcher(
    timeout: Duration = 10.seconds,
    testBody: suspend TestScope.() -> Unit,
) {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    try {
        runTest(timeout = timeout) { testBody() }
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * Extension to collect and assert on flow emissions using Turbine.
 *
 * Usage:
 * ```kotlin
 * viewModel.stateFlow.testCollect { items ->
 *     assertThat(items.first()).isEqualTo(expectedInitialState)
 * }
 * ```
 */
suspend fun <T> Flow<T>.testCollect(
    timeout: Duration = 1.seconds,
    validate: suspend ReceiveTurbine<T>.(List<T>) -> Unit,
) {
    val collected = mutableListOf<T>()
    test(timeout = timeout) {
        while (true) {
            val item = awaitItem()
            collected.add(item)
            if (expectMostRecentItem() == item) break
        }
        validate(collected)
    }
}

/**
 * Asserts that a Flow emits the expected values in order.
 *
 * Usage:
 * ```kotlin
 * myFlow.assertEmits(value1, value2, value3)
 * ```
 */
suspend fun <T> Flow<T>.assertEmits(vararg expected: T) {
    test {
        expected.forEach { expectedValue ->
            assertThat(awaitItem()).isEqualTo(expectedValue)
        }
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Asserts that a Flow emits a single value matching the predicate.
 *
 * Usage:
 * ```kotlin
 * myFlow.assertEmitsThat { it.isNotEmpty() }
 * ```
 */
suspend fun <T> Flow<T>.assertEmitsThat(predicate: (T) -> Boolean) {
    test {
        val item = awaitItem()
        assertThat(predicate(item)).isTrue()
        cancelAndIgnoreRemainingEvents()
    }
}

/**
 * Asserts that a Flow emits an error of the specified type.
 *
 * Usage:
 * ```kotlin
 * myFlow.assertError<IllegalStateException>()
 * ```
 */
suspend inline fun <reified E : Throwable> Flow<*>.assertError() {
    test {
        val error = awaitError()
        assertThat(error).isInstanceOf(E::class.java)
    }
}

/**
 * Asserts that a Flow completes without emitting any values.
 */
suspend fun Flow<*>.assertEmpty() {
    test {
        awaitComplete()
    }
}

/**
 * Extension function to advance until idle and get the current state.
 * Useful for testing ViewModels with StateFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun <T> TestScope.awaitState(stateProvider: () -> T): T {
    advanceUntilIdle()
    return stateProvider()
}
