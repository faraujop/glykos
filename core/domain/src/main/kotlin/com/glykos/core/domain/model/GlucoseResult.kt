package com.glykos.core.domain.model

/**
 * Outcome of asking a source for the latest reading.
 *
 * Failures are explicit values, never nulls or swallowed exceptions:
 * every [Unavailable] must reach the user as "sin datos" (noisy failure).
 */
sealed interface GlucoseResult {
    data class Available(val reading: GlucoseReading) : GlucoseResult

    data class Unavailable(val reason: Reason) : GlucoseResult

    enum class Reason {
        NETWORK,
        AUTH,
        RATE_LIMITED,
        NO_SENSOR_DATA,
        UNKNOWN,
    }
}
