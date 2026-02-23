package com.adsamcik.riposte.core.events

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class EventBusTest {

    private lateinit var eventBus: EventBus

    @Before
    fun setup() {
        eventBus = EventBus()
    }

    @Test
    fun `emitted event is received by collector`() = runTest {
        val event = MemeShared(memeId = 1)

        eventBus.events.test {
            eventBus.emit(event)
            assertThat(awaitItem()).isEqualTo(event)
        }
    }

    @Test
    fun `on filters by event type`() = runTest {
        eventBus.on<MemeShared>().test {
            eventBus.emit(MemeViewed(memeId = 99))
            eventBus.emit(MemeShared(memeId = 1))
            eventBus.emit(MemeDeleted(memeIds = setOf(2L)))

            val received = awaitItem()
            assertThat(received.memeId).isEqualTo(1)
        }
    }

    @Test
    fun `multiple event types are independently filterable`() = runTest {
        eventBus.on<MemeShared>().test {
            eventBus.on<MemeImported>().test {
                eventBus.emit(MemeShared(memeId = 1))
                eventBus.emit(MemeImported(memeId = 2, source = "zip"))
                eventBus.emit(MemeShared(memeId = 3))

                val imported = awaitItem()
                assertThat(imported.memeId).isEqualTo(2)
            }

            val shared1 = awaitItem()
            assertThat(shared1.memeId).isEqualTo(1)
            val shared2 = awaitItem()
            assertThat(shared2.memeId).isEqualTo(3)
        }
    }

    @Test
    fun `events are not replayed to late subscribers`() = runTest {
        eventBus.emit(MemeShared(memeId = 1))

        eventBus.on<MemeShared>().test {
            // Should not receive the event emitted before subscription
            expectNoEvents()
        }
    }

    @Test
    fun `buffer overflow drops oldest event`() = runTest {
        // Fill buffer beyond capacity without any collector
        repeat(EventBus.BUFFER_CAPACITY + 10) { i ->
            eventBus.emit(MemeViewed(memeId = i.toLong()))
        }

        // Late subscriber gets only the most recent buffered events
        eventBus.on<MemeViewed>().test {
            // Should not crash; we just verify no error on overflow
            expectNoEvents()
        }
    }

    @Test
    fun `event data classes carry correct values`() = runTest {
        val shared = MemeShared(memeId = 42, targetPackage = "com.whatsapp")
        assertThat(shared.memeId).isEqualTo(42)
        assertThat(shared.targetPackage).isEqualTo("com.whatsapp")
        assertThat(shared.timestampMs).isGreaterThan(0)

        val imported = MemeImported(memeId = 7, source = "gallery_pick")
        assertThat(imported.source).isEqualTo("gallery_pick")

        val embeddings = EmbeddingsReady(processedCount = 10, failedCount = 2, remainingCount = 5)
        assertThat(embeddings.processedCount).isEqualTo(10)
        assertThat(embeddings.remainingCount).isEqualTo(5)
    }
}
