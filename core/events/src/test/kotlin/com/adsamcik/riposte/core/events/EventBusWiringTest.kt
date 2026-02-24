package com.adsamcik.riposte.core.events

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Integration-level tests for [EventBus] wiring patterns used across the app.
 */
class EventBusWiringTest {

    private lateinit var eventBus: EventBus

    @Before
    fun setup() {
        eventBus = EventBus()
    }

    // ==================== Multiple Event Types ====================

    @Test
    fun `sequential mixed events are filtered correctly by type`() = runTest {
        eventBus.on<MemeShared>().test {
            eventBus.emit(MemeShared(memeId = 1))
            eventBus.emit(MemeImported(memeId = 2, source = "zip"))
            eventBus.emit(MemeViewed(memeId = 3))
            eventBus.emit(MemeShared(memeId = 4))
            eventBus.emit(MemeDeleted(memeIds = setOf(5L)))

            val first = awaitItem()
            assertThat(first.memeId).isEqualTo(1)

            val second = awaitItem()
            assertThat(second.memeId).isEqualTo(4)
        }
    }

    // ==================== Concurrent Subscribers ====================

    @Test
    fun `three concurrent subscribers for different types all receive events`() = runTest {
        val sharedEvents = mutableListOf<MemeShared>()
        val importedEvents = mutableListOf<MemeImported>()
        val viewedEvents = mutableListOf<MemeViewed>()

        val job1 = launch {
            eventBus.on<MemeShared>().take(2).toList(sharedEvents)
        }
        val job2 = launch {
            eventBus.on<MemeImported>().take(1).toList(importedEvents)
        }
        val job3 = launch {
            eventBus.on<MemeViewed>().take(1).toList(viewedEvents)
        }

        // Allow collectors to register
        testScheduler.advanceUntilIdle()

        eventBus.emit(MemeShared(memeId = 1))
        eventBus.emit(MemeImported(memeId = 2, source = "gallery"))
        eventBus.emit(MemeViewed(memeId = 3))
        eventBus.emit(MemeShared(memeId = 4))

        job1.join()
        job2.join()
        job3.join()

        assertThat(sharedEvents).hasSize(2)
        assertThat(sharedEvents.map { it.memeId }).containsExactly(1L, 4L).inOrder()
        assertThat(importedEvents).hasSize(1)
        assertThat(importedEvents[0].memeId).isEqualTo(2L)
        assertThat(viewedEvents).hasSize(1)
        assertThat(viewedEvents[0].memeId).isEqualTo(3L)
    }

    // ==================== Rapid-Fire Events ====================

    @Test
    fun `rapid-fire events all received in order`() = runTest {
        val count = EventBus.BUFFER_CAPACITY / 2 // Stay well within buffer to avoid drops
        val received = mutableListOf<MemeViewed>()

        eventBus.on<MemeViewed>().test {
            for (i in 1L..count) {
                eventBus.emit(MemeViewed(memeId = i))
            }

            for (i in 1L..count) {
                val event = awaitItem()
                received.add(event)
            }

            assertThat(received).hasSize(count)
            received.forEachIndexed { index, event ->
                assertThat(event.memeId).isEqualTo(index + 1L)
            }
        }
    }

    // ==================== Subscriber Cancellation ====================

    @Test
    fun `cancelled subscriber receives no more events`() = runTest {
        val received = mutableListOf<MemeShared>()
        val collectJob = launch {
            eventBus.on<MemeShared>().collect { received.add(it) }
        }
        testScheduler.advanceUntilIdle()

        eventBus.emit(MemeShared(memeId = 1))
        testScheduler.advanceUntilIdle()
        assertThat(received).hasSize(1)

        collectJob.cancelAndJoin()

        eventBus.emit(MemeShared(memeId = 2))
        eventBus.emit(MemeShared(memeId = 3))
        testScheduler.advanceUntilIdle()

        // Should still be 1 — no more events after cancellation
        assertThat(received).hasSize(1)
        assertThat(received[0].memeId).isEqualTo(1L)
    }

    // ==================== Event Data Integrity ====================

    @Test
    fun `event data integrity is preserved through bus`() = runTest {
        val ts = 1719000000000L
        val original = MemeShared(memeId = 42L, targetPackage = "com.whatsapp", timestampMs = ts)

        eventBus.on<MemeShared>().test {
            eventBus.emit(original)
            val received = awaitItem()

            assertThat(received.memeId).isEqualTo(42L)
            assertThat(received.targetPackage).isEqualTo("com.whatsapp")
            assertThat(received.timestampMs).isEqualTo(ts)
            assertThat(received).isEqualTo(original)
        }
    }

    // ==================== Cross-Type Filtering ====================

    @Test
    fun `subscribing to MemeShared ignores MemeDeleted events`() = runTest {
        eventBus.on<MemeShared>().test {
            eventBus.emit(MemeDeleted(memeIds = setOf(1L, 2L)))
            eventBus.emit(MemeViewed(memeId = 3))
            eventBus.emit(MemeImported(memeId = 4, source = "zip"))

            expectNoEvents()
        }
    }

    // ==================== Late Subscriber Misses Past Events ====================

    @Test
    fun `late subscriber does not receive events emitted before subscription`() = runTest {
        eventBus.emit(MemeShared(memeId = 1))
        eventBus.emit(MemeShared(memeId = 2))
        eventBus.emit(MemeShared(memeId = 3))

        eventBus.on<MemeShared>().test {
            expectNoEvents()
        }
    }

    // ==================== Multiple Emissions of Same Type ====================

    @Test
    fun `five MemeImported events with different data all received`() = runTest {
        val sources = listOf("zip", "gallery", "clipboard", "share_target", "download")
        val received = mutableListOf<MemeImported>()

        val collectJob = launch {
            eventBus.on<MemeImported>().take(5).toList(received)
        }
        testScheduler.advanceUntilIdle()

        sources.forEachIndexed { index, source ->
            eventBus.emit(MemeImported(memeId = index.toLong() + 1, source = source))
        }

        collectJob.join()

        assertThat(received).hasSize(5)
        received.forEachIndexed { index, event ->
            assertThat(event.memeId).isEqualTo(index + 1L)
            assertThat(event.source).isEqualTo(sources[index])
        }
    }

    // ==================== EventBus Isolation ====================

    @Test
    fun `two EventBus instances do not cross-talk`() = runTest {
        val bus1 = EventBus()
        val bus2 = EventBus()

        val bus1Received = mutableListOf<MemeShared>()
        val bus2Received = mutableListOf<MemeShared>()

        val job1 = launch {
            bus1.on<MemeShared>().take(1).toList(bus1Received)
        }
        val job2 = launch {
            bus2.on<MemeShared>().take(1).toList(bus2Received)
        }
        testScheduler.advanceUntilIdle()

        bus1.emit(MemeShared(memeId = 1))
        bus2.emit(MemeShared(memeId = 2))

        job1.join()
        job2.join()

        assertThat(bus1Received).hasSize(1)
        assertThat(bus1Received[0].memeId).isEqualTo(1L)
        assertThat(bus2Received).hasSize(1)
        assertThat(bus2Received[0].memeId).isEqualTo(2L)
    }

    // ==================== Flow Never Completes ====================

    @Test
    fun `EventBus on flow does not complete on its own`() = runTest {
        eventBus.on<MemeShared>().test {
            eventBus.emit(MemeShared(memeId = 1))
            awaitItem()

            // Flow should not have completed
            expectNoEvents()
        }
    }

    // ==================== EmbeddingsReady Event ====================

    @Test
    fun `EmbeddingsReady event carries batch counts correctly`() = runTest {
        eventBus.on<EmbeddingsReady>().test {
            eventBus.emit(EmbeddingsReady(processedCount = 15, failedCount = 3, remainingCount = 82))

            val event = awaitItem()
            assertThat(event.processedCount).isEqualTo(15)
            assertThat(event.failedCount).isEqualTo(3)
            assertThat(event.remainingCount).isEqualTo(82)
        }
    }
}
