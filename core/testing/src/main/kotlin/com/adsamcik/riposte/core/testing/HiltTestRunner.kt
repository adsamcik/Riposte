package com.adsamcik.riposte.core.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom test runner for instrumented tests with Hilt dependency injection.
 *
 * This runner replaces the default application with [HiltTestApplication],
 * enabling Hilt's test dependency injection features in Android instrumented tests.
 *
 * To use this runner, configure your module's build.gradle.kts:
 * ```kotlin
 * android {
 *     defaultConfig {
 *         testInstrumentationRunner = "com.adsamcik.riposte.core.testing.HiltTestRunner"
 *     }
 * }
 * ```
 *
 * And annotate your test class with `@HiltAndroidTest`:
 * ```kotlin
 * @HiltAndroidTest
 * class MyInstrumentedTest {
 *     @get:Rule
 *     val hiltRule = HiltAndroidRule(this)
 *
 *     @Inject
 *     lateinit var myDependency: MyDependency
 *
 *     @Before
 *     fun setup() {
 *         hiltRule.inject()
 *     }
 *
 *     @Test
 *     fun testSomething() {
 *         // Use injected dependencies
 *     }
 * }
 * ```
 *
 * To replace dependencies in tests, use `@BindValue` or create test modules:
 * ```kotlin
 * @HiltAndroidTest
 * class MyInstrumentedTest {
 *     @BindValue
 *     val fakeRepository: MyRepository = FakeMyRepository()
 * }
 * ```
 *
 * Or with a test module:
 * ```kotlin
 * @Module
 * @TestInstallIn(
 *     components = [SingletonComponent::class],
 *     replaces = [MyModule::class]
 * )
 * object FakeMyModule {
 *     @Provides
 *     fun provideMyRepository(): MyRepository = FakeMyRepository()
 * }
 * ```
 */
class HiltTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application {
        return super.newApplication(classLoader, HiltTestApplication::class.java.name, context)
    }
}
