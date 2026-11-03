package com.glykos.core.domain.model

import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * A single glucose measurement.
 *
 * @property valueMgDl glucose in mg/dl.
 * @property trend direction reported by the source.
 * @property timestamp moment the sensor took the measurement (not when we fetched it).
 */
data class GlucoseReading(
    val valueMgDl: Int,
    val trend: Trend,
    val timestamp: Instant,
) {
    fun age(now: Instant): Duration = now - timestamp

    /** A reading older than [maxAge] must be treated as "no data" and trigger the stale alarm. */
    fun isStale(now: Instant, maxAge: Duration): Boolean = age(now) > maxAge
}
