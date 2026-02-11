package com.adsamcik.riposte.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile generator for Riposte.
 *
 * Run this test to generate baseline profiles that improve startup
 * and runtime performance by pre-compiling critical code paths.
 *
 * Generate the baseline profile with:
 * ./gradlew :baselineprofile:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = "com.adsamcik.riposte",
            includeInStartupProfile = true,
        ) {
            // App startup - critical path
            pressHome()
            startActivityAndWait()

            // Wait for the gallery screen to load
            device.wait(Until.hasObject(By.res("gallery_grid")), 5_000)

            // Scroll through gallery - common user journey
            device.wait(Until.hasObject(By.scrollable(true)), 2_000)
            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.let {
                // Scroll down and up to precompile scrolling paths
                it.scroll(androidx.test.uiautomator.Direction.DOWN, 0.5f)
                device.waitForIdle()
                it.scroll(androidx.test.uiautomator.Direction.UP, 0.5f)
                device.waitForIdle()
            }

            // Navigate to search - common action
            device.findObject(By.res("search_button"))?.click()
            device.wait(Until.hasObject(By.res("search_bar")), 3_000)

            // Go back to gallery
            device.pressBack()
            device.wait(Until.hasObject(By.res("gallery_grid")), 3_000)
        }
    }
}
