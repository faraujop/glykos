package com.glykos.core.domain.format

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.GlucoseResult.Reason
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class VoiceMessageFormatterTest {

    private val now = Instant.parse("2026-11-07T10:00:00Z")

    private fun announcement(
        value: Int,
        trend: Trend = Trend.FALLING,
        delta: Int? = -8,
        kind: Kind = Kind.ROUTINE,
        age: Duration = 30.seconds,
    ) = Announcement(
        kind = kind,
        result = GlucoseResult.Available(GlucoseReading(value, trend, timestamp = now - age)),
        deltaMgDl = delta,
        at = now,
    )

    private fun format(a: Announcement) = VoiceMessageFormatter.format(a)

    @Test
    fun `routine message reads value, trend and delta`() {
        assertEquals("Ciento doce, bajando, menos ocho", format(announcement(112)))
    }

    @ParameterizedTest
    @CsvSource(
        "FALLING_FAST, 'Ciento doce, bajando rápido, menos ocho'",
        "FALLING, 'Ciento doce, bajando, menos ocho'",
        "STABLE, 'Ciento doce, estable, menos ocho'",
        "RISING, 'Ciento doce, subiendo, menos ocho'",
        "RISING_FAST, 'Ciento doce, subiendo rápido, menos ocho'",
        "UNKNOWN, 'Ciento doce, menos ocho'",
    )
    fun `trend is spoken in Spanish and omitted when unknown`(trend: Trend, expected: String) {
        assertEquals(expected, format(announcement(112, trend)))
    }

    @Test
    fun `positive delta is spoken as mas`() {
        assertEquals("Ciento veinte, subiendo, más quince", format(announcement(120, Trend.RISING, delta = 15)))
    }

    @Test
    fun `zero delta is spoken as sin cambio`() {
        assertEquals("Noventa, estable, sin cambio", format(announcement(90, Trend.STABLE, delta = 0)))
    }

    @Test
    fun `missing delta is omitted`() {
        assertEquals("Noventa, estable", format(announcement(90, Trend.STABLE, delta = null)))
    }

    @Test
    fun `low alarm is prefixed`() {
        assertEquals(
            "Glucosa baja. Sesenta y cinco, bajando, menos diez",
            format(announcement(65, delta = -10, kind = Kind.LOW)),
        )
    }

    @Test
    fun `very low alarm is prefixed with an attention call`() {
        assertEquals(
            "Atención, glucosa muy baja. Cincuenta, bajando rápido, menos diez",
            format(announcement(50, Trend.FALLING_FAST, delta = -10, kind = Kind.VERY_LOW)),
        )
    }

    @Test
    fun `high alarm is prefixed`() {
        assertEquals(
            "Glucosa alta. Ciento noventa, subiendo, más quince",
            format(announcement(190, Trend.RISING, delta = 15, kind = Kind.HIGH)),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "NETWORK, 'Sin datos de glucosa, sin conexión.'",
        "AUTH, 'Sin datos de glucosa, revisa la cuenta.'",
        "RATE_LIMITED, 'Sin datos de glucosa, servicio saturado.'",
        "NO_SENSOR_DATA, 'Sin datos de glucosa, el sensor no envía datos.'",
        "UNKNOWN, 'Sin datos de glucosa.'",
    )
    fun `unavailable source says sin datos with the reason`(reason: Reason, expected: String) {
        val a = Announcement(Kind.NO_DATA, GlucoseResult.Unavailable(reason), deltaMgDl = null, at = now)
        assertEquals(expected, format(a))
    }

    @Test
    fun `stale reading says sin datos recientes with its age and last value`() {
        val a = announcement(112, kind = Kind.NO_DATA, delta = null, age = 12.minutes + 40.seconds)
        assertEquals("Sin datos recientes. Último dato hace doce minutos: ciento doce.", format(a))
    }

    @Test
    fun `stale reading of one minute uses singular`() {
        val a = announcement(112, kind = Kind.NO_DATA, delta = null, age = 1.minutes + 10.seconds)
        assertEquals("Sin datos recientes. Último dato hace un minuto: ciento doce.", format(a))
    }

    @Test
    fun `stale reading older than an hour is not spelled in minutes`() {
        val a = announcement(112, kind = Kind.NO_DATA, delta = null, age = 75.minutes)
        assertEquals("Sin datos recientes. Último dato hace más de una hora: ciento doce.", format(a))
    }

    private fun simulated(a: Announcement): Announcement {
        val r = (a.result as GlucoseResult.Available).reading
        return a.copy(result = GlucoseResult.Available(r.copy(simulated = true)))
    }

    @Test
    fun `simulated reading is announced as demo`() {
        assertEquals("Demo. Ciento doce, bajando, menos ocho", format(simulated(announcement(112))))
    }

    @Test
    fun `simulated alarm is announced as demo`() {
        assertEquals(
            "Demo. Glucosa baja. Sesenta y cinco, bajando, menos diez",
            format(simulated(announcement(65, delta = -10, kind = Kind.LOW))),
        )
    }
}
