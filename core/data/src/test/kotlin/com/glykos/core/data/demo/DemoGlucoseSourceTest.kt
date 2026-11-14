package com.glykos.core.data.demo

import com.glykos.core.data.librelinkup.FakeClock
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DemoGlucoseSourceTest {

    private val start = Instant.parse("2026-11-14T10:00:00Z")
    private val clock = FakeClock(start)
    private val curve = listOf(150, 152, 140, 120, 119)
    private val source = DemoGlucoseSource(clock, curve)

    private suspend fun reading() = (source.latest() as GlucoseResult.Available).reading

    @Test
    fun `readings are fresh and marked as simulated`() = runTest {
        val r = reading()
        assertEquals(150, r.valueMgDl)
        assertEquals(start, r.timestamp)
        assertTrue(r.simulated)
    }

    @Test
    fun `curve advances one point per minute since the first call`() = runTest {
        reading()
        clock.now = start + 1.minutes
        assertEquals(152, reading().valueMgDl)
        clock.now = start + 3.minutes + 30.seconds
        assertEquals(120, reading().valueMgDl)
    }

    @Test
    fun `curve loops when it reaches the end`() = runTest {
        reading()
        clock.now = start + 5.minutes
        assertEquals(150, reading().valueMgDl)
    }

    @Test
    fun `trend follows the change from the previous point`() = runTest {
        reading()
        val trends = (1..4).map { clock.now = start + it.minutes; reading().trend }
        assertEquals(listOf(Trend.STABLE, Trend.FALLING, Trend.FALLING_FAST, Trend.STABLE), trends)
    }

    @Test
    fun `built-in curve passes below 70 so the low alarms can be seen`() {
        assertTrue(DemoGlucoseSource.RUN_CURVE.any { it < 70 })
        assertTrue(DemoGlucoseSource.RUN_CURVE.any { it > 180 })
    }
}
