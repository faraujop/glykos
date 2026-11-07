package com.glykos.core.domain.monitor

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.announce.AnnouncementConfig
import com.glykos.core.domain.announce.AnnouncementPolicy
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GlucoseMonitorTest {

    private val t0 = Instant.parse("2026-11-07T10:00:00Z")

    private class RecordingSink(override val name: String, var failWith: Exception? = null) : AnnouncementSink {
        val received = mutableListOf<Announcement>()
        override suspend fun deliver(announcement: Announcement) {
            failWith?.let { throw it }
            received += announcement
        }
    }

    private fun TestScope.clock() = object : Clock {
        override fun now(): Instant = t0 + testScheduler.currentTime.milliseconds
    }

    private fun TestScope.fresh(value: Int) = GlucoseResult.Available(GlucoseReading(value, Trend.STABLE, clock().now()))

    private fun TestScope.monitor(
        sinks: List<AnnouncementSink>,
        fetch: suspend () -> GlucoseResult = { fresh(112) },
    ) = GlucoseMonitor(
        policy = AnnouncementPolicy(AnnouncementConfig()),
        fetch = fetch,
        sinks = sinks,
        clock = clock(),
        pollInterval = 60.seconds,
    )

    @Test
    fun `first tick fetches and delivers to every sink`() = runTest {
        val voice = RecordingSink("voz")
        val watch = RecordingSink("reloj")
        monitor(listOf(voice, watch)).tick()
        assertEquals(Kind.ROUTINE, voice.received.single().kind)
        assertEquals(voice.received, watch.received)
    }

    @Test
    fun `later ticks follow the announcement policy`() = runTest {
        val sink = RecordingSink("voz")
        val m = monitor(listOf(sink))
        m.tick()
        advanceTimeBy(1.minutes)
        m.tick()
        assertEquals(1, sink.received.size)
    }

    @Test
    fun `alarm is delivered immediately on the next tick`() = runTest {
        val sink = RecordingSink("voz")
        var value = 80
        val m = monitor(listOf(sink)) { fresh(value) }
        m.tick()
        advanceTimeBy(1.minutes)
        value = 65
        m.tick()
        assertEquals(listOf(Kind.ROUTINE, Kind.LOW), sink.received.map { it.kind })
        assertEquals(-15, sink.received.last().deltaMgDl)
    }

    @Test
    fun `if one sink fails the others still receive it and the error is reported`() = runTest {
        val voice = RecordingSink("voz", failWith = IllegalStateException("sin voz"))
        val watch = RecordingSink("reloj")
        val m = monitor(listOf(voice, watch))
        m.tick()
        assertEquals(1, watch.received.size)
        assertEquals(listOf(OutputError("voz", "sin voz")), m.state.value.outputErrors)
    }

    @Test
    fun `if every sink fails the announcement is retried on the next tick`() = runTest {
        val sink = RecordingSink("voz", failWith = IllegalStateException("sin voz"))
        val m = monitor(listOf(sink))
        m.tick()
        sink.failWith = null
        advanceTimeBy(1.minutes)
        m.tick()
        assertEquals(1, sink.received.size)
    }

    @Test
    fun `if at least one sink delivered, the announcement counts as made`() = runTest {
        val voice = RecordingSink("voz", failWith = IllegalStateException("sin voz"))
        val watch = RecordingSink("reloj")
        val m = monitor(listOf(voice, watch))
        m.tick()
        advanceTimeBy(1.minutes)
        m.tick()
        assertEquals(1, watch.received.size)
    }

    @Test
    fun `errors are cleared after a clean delivery`() = runTest {
        val sink = RecordingSink("voz", failWith = IllegalStateException("sin voz"))
        val m = monitor(listOf(sink))
        m.tick()
        sink.failWith = null
        m.tick()
        assertTrue(m.state.value.outputErrors.isEmpty())
    }

    @Test
    fun `a throwing source is treated as no data`() = runTest {
        val sink = RecordingSink("voz")
        monitor(listOf(sink)) { throw IllegalStateException("boom") }.tick()
        val a = sink.received.single()
        assertEquals(Kind.NO_DATA, a.kind)
        assertEquals(GlucoseResult.Unavailable(GlucoseResult.Reason.UNKNOWN), a.result)
    }

    @Test
    fun `cancellation from the source is propagated`() = runTest {
        val m = monitor(listOf(RecordingSink("voz"))) { throw CancellationException("stop") }
        var cancelled = false
        try {
            m.tick()
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `state exposes the latest result and announcement`() = runTest {
        val m = monitor(listOf(RecordingSink("voz")))
        assertNull(m.state.value.lastResult)
        m.tick()
        assertEquals(fresh(112), m.state.value.lastResult)
        assertEquals(t0, m.state.value.lastCheckAt)
        assertEquals(Kind.ROUTINE, m.state.value.lastAnnouncement?.kind)
    }

    @Test
    fun `run polls immediately and then every poll interval`() = runTest {
        var fetches = 0
        val job = launch { monitor(listOf(RecordingSink("voz"))) { fetches++; fresh(112) }.run() }
        runCurrent()
        assertEquals(1, fetches)
        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(1, fetches)
        advanceTimeBy(1.seconds)
        runCurrent()
        assertEquals(2, fetches)
        advanceTimeBy(2.minutes)
        runCurrent()
        assertEquals(4, fetches)
        job.cancel()
    }

    @Test
    fun `poll interval cannot be shorter than 60 seconds`() = runTest {
        var thrown = false
        try {
            GlucoseMonitor(AnnouncementPolicy(AnnouncementConfig()), { fresh(1) }, emptyList(), clock(), 59.seconds)
        } catch (_: IllegalArgumentException) {
            thrown = true
        }
        assertTrue(thrown)
    }
}
