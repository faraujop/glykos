package com.glykos.core.domain.format

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import kotlin.math.abs

/** Spanish TTS text for an [Announcement], e.g. "Ciento doce, bajando, menos ocho". */
object VoiceMessageFormatter {

    fun format(announcement: Announcement): String = when (val result = announcement.result) {
        is GlucoseResult.Unavailable -> noData(result.reason)
        is GlucoseResult.Available ->
            if (announcement.kind == Kind.NO_DATA) stale(result.reading, announcement)
            else prefix(announcement.kind) + reading(result.reading, announcement.deltaMgDl).capitalizeFirst()
    }

    private fun prefix(kind: Kind): String = when (kind) {
        Kind.LOW -> "Glucosa baja. "
        Kind.VERY_LOW -> "Atención, glucosa muy baja. "
        Kind.HIGH -> "Glucosa alta. "
        Kind.ROUTINE, Kind.NO_DATA -> ""
    }

    private fun reading(reading: GlucoseReading, delta: Int?): String =
        listOfNotNull(SpanishNumbers.toWords(reading.valueMgDl), reading.trend.label(), delta?.let(::delta))
            .joinToString(", ")

    private fun delta(delta: Int): String = when {
        delta == 0 -> "sin cambio"
        delta > 0 -> "más ${SpanishNumbers.toWords(delta)}"
        else -> "menos ${SpanishNumbers.toWords(abs(delta))}"
    }

    private fun noData(reason: GlucoseResult.Reason): String =
        reason.label()?.let { "Sin datos de glucosa, $it." } ?: "Sin datos de glucosa."

    private fun stale(reading: GlucoseReading, announcement: Announcement): String {
        val minutes = (announcement.at - reading.timestamp).inWholeMinutes
        val ago = when {
            minutes >= 60 -> "más de una hora"
            minutes == 1L -> "un minuto"
            else -> "${SpanishNumbers.toWords(minutes.toInt())} minutos"
        }
        return "Sin datos recientes. Último dato hace $ago: ${SpanishNumbers.toWords(reading.valueMgDl)}."
    }
}
