package com.glykos.core.domain.session

import com.glykos.core.domain.announce.GlucoseThresholds
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SessionSummaryTest {

    private val t0 = Instant.parse("2026-11-12T10:00:00Z")
    private val thresholds = GlucoseThresholds(veryLow = 54, low = 70, high = 180)

    private fun r(minute: Int, value: Int) = GlucoseReading(value, Trend.STABLE, t0 + minute.minutes)

    /** One reading per minute from [from] (inclusive) to [to] (exclusive), all with [value]. */
    private fun everyMinute(from: Int, to: Int, value: Int) = (from until to).map { r(it, value) }

    private fun summary(readings: List<GlucoseReading>, minutes: Int) =
        SessionData(start = t0, end = t0 + minutes.minutes, readings = readings).summarize(thresholds)

    @Test
    fun `splits time into below, in range and above`() {
        val readings = everyMinute(0, 10, 150) + everyMinute(10, 15, 65) + everyMinute(15, 20, 200)

        val s = summary(readings, 20)

        assertEquals(10.minutes, s.timeInRange)
        assertEquals(5.minutes, s.timeBelow)
        assertEquals(5.minutes, s.timeAbove)
        assertEquals(Duration.ZERO, s.timeNoData)
    }

    @Test
    fun `range limits are inclusive like the alarms`() {
        val s = summary(everyMinute(0, 5, 70) + everyMinute(5, 10, 180), 10)

        assertEquals(10.minutes, s.timeInRange)
    }

    @Test
    fun `very low counts as below`() {
        val s = summary(everyMinute(0, 10, 50), 10)

        assertEquals(10.minutes, s.timeBelow)
    }

    @Test
    fun `a gap longer than the max gap is no data, never in range`() {
        // 0..9 in range, nothing from 10 to 20, then 20..29 in range.
        val readings = everyMinute(0, 10, 120) + everyMinute(20, 30, 120)

        val s = summary(readings, 30)

        assertEquals(20.minutes, s.timeInRange)
        assertEquals(10.minutes, s.timeNoData)
        assertEquals(2, s.segments.size)
    }

    @Test
    fun `a reading followed by a gap only covers one reading interval`() {
        val s = summary(listOf(r(0, 120)), 30)

        assertEquals(SessionData.READING_INTERVAL, s.timeInRange)
        assertEquals(30.minutes - SessionData.READING_INTERVAL, s.timeNoData)
    }

    @Test
    fun `last reading covers until the end when the session stops soon after`() {
        val s = summary(everyMinute(0, 10, 120), 13)

        assertEquals(13.minutes, s.timeInRange)
    }

    @Test
    fun `short pauses between readings are not gaps`() {
        val s = summary(listOf(r(0, 120), r(3, 120), r(6, 120)), 9)

        assertEquals(9.minutes, s.timeInRange)
        assertEquals(1, s.segments.size)
    }

    @Test
    fun `readings taken just before the start only count from the start`() {
        val early = GlucoseReading(120, Trend.STABLE, t0 - 40.seconds)

        val s = summary(listOf(early) + everyMinute(1, 10, 120), 10)

        assertEquals(10.minutes, s.timeInRange)
    }

    @Test
    fun `parts always add up to the session duration`() {
        val readings = everyMinute(0, 7, 60) + everyMinute(12, 15, 250) + listOf(r(29, 100))

        val s = summary(readings, 33)

        assertEquals(33.minutes, s.timeBelow + s.timeInRange + s.timeAbove + s.timeNoData)
    }

    @Test
    fun `percent is relative to the whole session, rounded`() {
        val s = summary(everyMinute(0, 10, 150) + everyMinute(10, 13, 65), 13)

        assertEquals(77, s.percent(s.timeInRange))
        assertEquals(23, s.percent(s.timeBelow))
    }

    @Test
    fun `first, last, min and max come from the session readings`() {
        val readings = listOf(r(0, 148), r(1, 120), r(2, 66), r(3, 74))

        val s = summary(readings, 4)

        assertEquals(148, s.first?.valueMgDl)
        assertEquals(74, s.last?.valueMgDl)
        assertEquals(66, s.min)
        assertEquals(148, s.max)
    }

    @Test
    fun `no readings means the whole session is no data`() {
        val s = summary(emptyList(), 20)

        assertEquals(20.minutes, s.timeNoData)
        assertNull(s.first)
        assertNull(s.min)
        assertEquals(0, s.segments.size)
    }

    @Test
    fun `zero length session does not divide by zero`() {
        val s = summary(emptyList(), 0)

        assertEquals(0, s.percent(s.timeNoData))
    }
}
