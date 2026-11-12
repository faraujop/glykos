package com.glykos.core.domain.session

import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class SessionRecorderTest {

    private val t0 = Instant.parse("2026-11-12T10:00:00Z")

    private fun available(minute: Int, value: Int) =
        GlucoseResult.Available(GlucoseReading(value, Trend.STABLE, t0 + minute.minutes))

    @Test
    fun `keeps each sensor reading once, even if polled several times`() {
        val recorder = SessionRecorder(start = t0)

        recorder.record(available(0, 120))
        recorder.record(available(0, 120))
        recorder.record(available(1, 118))

        assertEquals(listOf(120, 118), recorder.finish(t0 + 2.minutes).readings.map { it.valueMgDl })
    }

    @Test
    fun `ignores unavailable results`() {
        val recorder = SessionRecorder(start = t0)

        recorder.record(GlucoseResult.Unavailable(GlucoseResult.Reason.NETWORK))

        assertEquals(0, recorder.finish(t0 + 1.minutes).readings.size)
    }

    @Test
    fun `readings come out in time order`() {
        val recorder = SessionRecorder(start = t0)

        recorder.record(available(2, 100))
        recorder.record(available(1, 110))

        assertEquals(listOf(110, 100), recorder.finish(t0 + 3.minutes).readings.map { it.valueMgDl })
    }

    @Test
    fun `finish keeps start and end`() {
        val data = SessionRecorder(start = t0).finish(t0 + 5.minutes)

        assertEquals(t0, data.start)
        assertEquals(t0 + 5.minutes, data.end)
    }
}
