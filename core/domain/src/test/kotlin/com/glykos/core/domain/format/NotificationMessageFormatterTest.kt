package com.glykos.core.domain.format

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.GlucoseResult.Reason
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class NotificationMessageFormatterTest {

    private val now = Instant.parse("2026-11-07T10:00:00Z")

    private fun announcement(
        value: Int,
        trend: Trend = Trend.FALLING,
        delta: Int? = -8,
        kind: Kind = Kind.ROUTINE,
        age: Duration = 1.minutes + 20.seconds,
    ) = Announcement(
        kind = kind,
        result = GlucoseResult.Available(GlucoseReading(value, trend, timestamp = now - age)),
        deltaMgDl = delta,
        at = now,
    )

    private fun format(a: Announcement) = NotificationMessageFormatter.format(a)

    @Test
    fun `routine notification shows value, trend, delta and age`() {
        assertEquals("112 bajando -8 (hace 1 min)", format(announcement(112)))
    }

    @ParameterizedTest
    @CsvSource(
        "FALLING_FAST, '112 bajando rápido -8 (hace 1 min)'",
        "FALLING, '112 bajando -8 (hace 1 min)'",
        "STABLE, '112 estable -8 (hace 1 min)'",
        "RISING, '112 subiendo -8 (hace 1 min)'",
        "RISING_FAST, '112 subiendo rápido -8 (hace 1 min)'",
        "UNKNOWN, '112 -8 (hace 1 min)'",
    )
    fun `trend is shown as a word and omitted when unknown`(trend: Trend, expected: String) {
        assertEquals(expected, format(announcement(112, trend)))
    }

    @Test
    fun `positive delta has a plus sign`() {
        assertEquals("120 subiendo +15 (hace 1 min)", format(announcement(120, Trend.RISING, delta = 15)))
    }

    @Test
    fun `zero delta is shown as plus zero`() {
        assertEquals("90 estable +0 (hace 1 min)", format(announcement(90, Trend.STABLE, delta = 0)))
    }

    @Test
    fun `missing delta is omitted`() {
        assertEquals("90 estable (hace 1 min)", format(announcement(90, Trend.STABLE, delta = null)))
    }

    @Test
    fun `reading younger than a minute is shown as ahora`() {
        assertEquals("112 bajando -8 (ahora)", format(announcement(112, age = 40.seconds)))
    }

    @Test
    fun `low alarm is labelled`() {
        assertEquals("BAJA 65 bajando -10 (ahora)", format(announcement(65, delta = -10, kind = Kind.LOW, age = 0.seconds)))
    }

    @Test
    fun `very low alarm is labelled`() {
        assertEquals(
            "MUY BAJA 50 bajando rápido -10 (ahora)",
            format(announcement(50, Trend.FALLING_FAST, delta = -10, kind = Kind.VERY_LOW, age = 0.seconds)),
        )
    }

    @Test
    fun `high alarm is labelled`() {
        assertEquals(
            "ALTA 190 subiendo +15 (ahora)",
            format(announcement(190, Trend.RISING, delta = 15, kind = Kind.HIGH, age = 0.seconds)),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "NETWORK, 'SIN DATOS, sin conexión'",
        "AUTH, 'SIN DATOS, revisa la cuenta'",
        "UNKNOWN, 'SIN DATOS'",
    )
    fun `unavailable source shows sin datos with the reason`(reason: Reason, expected: String) {
        val a = Announcement(Kind.NO_DATA, GlucoseResult.Unavailable(reason), deltaMgDl = null, at = now)
        assertEquals(expected, format(a))
    }

    @Test
    fun `stale reading shows last value and its age`() {
        val a = announcement(112, kind = Kind.NO_DATA, delta = null, age = 12.minutes + 40.seconds)
        assertEquals("SIN DATOS recientes, último 112 hace 12 min", format(a))
    }

    @Test
    fun `stale reading older than an hour shows hours`() {
        val a = announcement(112, kind = Kind.NO_DATA, delta = null, age = 2.hours + 5.minutes)
        assertEquals("SIN DATOS recientes, último 112 hace 2 h", format(a))
    }

    /** The Garmin FR35 shows emoji, arrows and other symbols as "?" boxes: keep to Latin-1. */
    @ParameterizedTest
    @CsvSource("ROUTINE", "LOW", "VERY_LOW", "HIGH", "NO_DATA")
    fun `watch text only uses characters the watch can draw`(kind: Kind) {
        Trend.entries.forEach { trend ->
            listOf(-12, 0, 7, null).forEach { delta ->
                val text = format(announcement(70, trend, delta = delta, kind = kind))
                assertTrue(text.all { it.code <= 0xFF }) { "unsupported character in: $text" }
            }
        }
        Reason.entries.forEach { reason ->
            val text = format(Announcement(kind, GlucoseResult.Unavailable(reason), deltaMgDl = null, at = now))
            assertTrue(text.all { it.code <= 0xFF }) { "unsupported character in: $text" }
        }
    }
}
