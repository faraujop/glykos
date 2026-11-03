package com.glykos.core.domain.model

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class GlucoseReadingTest {

    private val taken = Instant.parse("2026-11-03T10:00:00Z")
    private val reading = GlucoseReading(valueMgDl = 112, trend = Trend.FALLING, timestamp = taken)

    @Test
    fun `age is the time elapsed since the sensor measurement`() {
        assertEquals(3.minutes, reading.age(taken + 3.minutes))
    }

    @Test
    fun `reading within max age is not stale`() {
        assertFalse(reading.isStale(now = taken + 10.minutes, maxAge = 10.minutes))
    }

    @Test
    fun `reading older than max age is stale`() {
        assertTrue(reading.isStale(now = taken + 11.minutes, maxAge = 10.minutes))
    }
}
