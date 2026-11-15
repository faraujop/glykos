package com.glykos.core.domain.format

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlin.math.abs
import kotlin.time.Duration

/**
 * Short one-line text for the watch notification, e.g. "112 -8 baja (hace 1 min)".
 * The Garmin FR35 shows only about 12 characters on its first screen, so value and delta come first
 * and the trend uses short words. Plain Latin-1 text only: the FR35 draws emoji, arrows and symbols
 * such as "−" or "±" as "?" boxes.
 */
object NotificationMessageFormatter {

    fun format(announcement: Announcement): String = when (val result = announcement.result) {
        is GlucoseResult.Unavailable ->
            listOfNotNull("SIN DATOS", result.reason.label()).joinToString(", ")
        is GlucoseResult.Available -> {
            val reading = result.reading
            val age = announcement.at - reading.timestamp
            if (announcement.kind == Kind.NO_DATA) {
                "SIN DATOS recientes, último ${reading.valueMgDl} hace ${shortAge(age)}"
            } else {
                listOfNotNull(
                    // Simulated data must never pass for a real glucose value.
                    "DEMO".takeIf { reading.simulated },
                    label(announcement.kind),
                    reading.valueMgDl.toString(),
                    announcement.deltaMgDl?.let(::delta),
                    shortTrend(reading.trend),
                    if (age.inWholeMinutes < 1) "(ahora)" else "(hace ${shortAge(age)})",
                ).joinToString(" ")
            }
        }
    }

    private fun label(kind: Kind): String? = when (kind) {
        Kind.ROUTINE -> null
        Kind.LOW -> "BAJA"
        Kind.VERY_LOW -> "MUY BAJA"
        Kind.HIGH -> "ALTA"
        Kind.NO_DATA -> "SIN DATOS"
    }

    /** Trend in short words, so it fits the watch screen; null when unknown. */
    private fun shortTrend(trend: Trend): String? = when (trend) {
        Trend.FALLING_FAST -> "baja rápido"
        Trend.FALLING -> "baja"
        Trend.STABLE -> "estable"
        Trend.RISING -> "sube"
        Trend.RISING_FAST -> "sube rápido"
        Trend.UNKNOWN -> null
    }

    /** Trend arrow (↓ ↘ → ↗ ↑) for the app UI, or null when unknown. Not for the watch text. */
    fun arrow(trend: Trend): String? = when (trend) {
        Trend.FALLING_FAST -> "↓"
        Trend.FALLING -> "↘"
        Trend.STABLE -> "→"
        Trend.RISING -> "↗"
        Trend.RISING_FAST -> "↑"
        Trend.UNKNOWN -> null
    }

    private fun delta(delta: Int): String = when {
        delta == 0 -> "+0"
        delta > 0 -> "+$delta"
        else -> "-${abs(delta)}"
    }

    private fun shortAge(age: Duration): String =
        if (age.inWholeMinutes >= 60) "${age.inWholeHours} h" else "${age.inWholeMinutes} min"
}
