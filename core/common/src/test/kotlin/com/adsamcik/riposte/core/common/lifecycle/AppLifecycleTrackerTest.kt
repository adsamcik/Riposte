package com.adsamcik.riposte.core.common.lifecycle

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class AppLifecycleTrackerTest {

    private lateinit var tracker: AppLifecycleTracker
    private val owner: LifecycleOwner = mockk()

    @Before
    fun setup() {
        tracker = AppLifecycleTracker()
    }

    @Test
    fun `initial state is foreground`() {
        assertThat(tracker.isInBackground.value).isFalse()
    }

    @Test
    fun `onStop sets background state`() {
        tracker.onStop(owner)

        assertThat(tracker.isInBackground.value).isTrue()
    }

    @Test
    fun `onStart sets foreground state`() {
        tracker.onStop(owner)
        tracker.onStart(owner)

        assertThat(tracker.isInBackground.value).isFalse()
    }

    @Test
    fun `multiple transitions update state correctly`() {
        tracker.onStop(owner)
        assertThat(tracker.isInBackground.value).isTrue()

        tracker.onStart(owner)
        assertThat(tracker.isInBackground.value).isFalse()

        tracker.onStop(owner)
        assertThat(tracker.isInBackground.value).isTrue()
    }

    @Test
    fun `isInBackground flow emits updates`() = runTest {
        tracker.isInBackground.test {
            assertThat(awaitItem()).isFalse()

            tracker.onStop(owner)
            assertThat(awaitItem()).isTrue()

            tracker.onStart(owner)
            assertThat(awaitItem()).isFalse()
        }
    }
}
