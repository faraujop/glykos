package com.glykos.core.domain.session

import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import kotlinx.datetime.Instant

/**
 * Collects the readings seen during one monitoring session, **in memory only**.
 *
 * Privacy promise: Glykos does not keep a glucose history. Nothing here is ever written to disk;
 * the data lives until the session summary is closed or the process dies.
 */
class SessionRecorder(private val start: Instant) {
    // Keyed by sensor timestamp: polling every 60 s often returns the same reading twice.
    private val readings = mutableMapOf<Instant, GlucoseReading>()

    fun record(result: GlucoseResult) {
        if (result is GlucoseResult.Available) readings[result.reading.timestamp] = result.reading
    }

    fun finish(end: Instant) = SessionData(start, end, readings.values.sortedBy { it.timestamp })
}
