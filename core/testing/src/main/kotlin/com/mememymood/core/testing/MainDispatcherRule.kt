package com.mememymood.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A JUnit [TestWatcher] rule that sets the Main dispatcher to a test dispatcher
 * for the duration of the test.
 *
 * This rule is essential for testing code that uses `Dispatchers.Main`, which would
 * otherwise fail in unit tests since there's no Android main looper.
 *
 * Usage:
 * ```kotlin
 * class MyViewModelTest {
 *     @get:Rule
 *     val mainDispatcherRule = MainDispatcherRule()
 *
 *     @Test
 *     fun testSomething() = runTest {
 *         // Your test code here
 *     }
 * }
 * ```
 *
 * @param testDispatcher The [TestDispatcher] to use as the Main dispatcher.
 *                       Defaults to [UnconfinedTestDispatcher] for immediate execution,
 *                       but can be set to [StandardTestDispatcher] for more controlled timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {

    override fun starting(description: Description?) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description?) {
        Dispatchers.resetMain()
    }
}

/**
 * Creates a [MainDispatcherRule] with a [StandardTestDispatcher].
 *
 * Use this when you need more control over coroutine execution timing,
 * such as testing specific ordering of suspending operations.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun standardDispatcherRule(): MainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

/**
 * Creates a [MainDispatcherRule] with an [UnconfinedTestDispatcher].
 *
 * Use this for most tests where you want coroutines to execute eagerly.
 * This is the default behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun unconfinedDispatcherRule(): MainDispatcherRule = MainDispatcherRule(UnconfinedTestDispatcher())
