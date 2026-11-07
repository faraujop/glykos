package com.glykos.core.domain.announce

import kotlin.time.Duration.Companion.minutes

/**
 * User-editable announcement settings, with safe bounded ranges.
 *
 * The ranges never overlap, so veryLow < low < high always holds and no value can disable the
 * low alarms. Stale limit and alarm repeat are deliberately NOT editable (safety).
 */
data class AnnouncementSettings(
    val intervalMinutes: Int = 5,
    /** null = speak on every interval regardless of the change. */
    val minChangeMgDl: Int? = null,
    val veryLow: Int = 54,
    val low: Int = 70,
    val high: Int = 180,
) {
    /** Values forced into their ranges; a non-positive min change means disabled. */
    fun sanitized() = AnnouncementSettings(
        intervalMinutes = intervalMinutes.coerceIn(INTERVAL_RANGE),
        minChangeMgDl = minChangeMgDl?.takeIf { it > 0 }?.coerceIn(MIN_CHANGE_RANGE),
        veryLow = veryLow.coerceIn(VERY_LOW_RANGE),
        low = low.coerceIn(LOW_RANGE),
        high = high.coerceIn(HIGH_RANGE),
    )

    /** Always succeeds: values are sanitized first, so corrupted storage cannot break monitoring. */
    fun toConfig(): AnnouncementConfig {
        val s = sanitized()
        return AnnouncementConfig(
            interval = s.intervalMinutes.minutes,
            minChangeMgDl = s.minChangeMgDl,
            thresholds = GlucoseThresholds(veryLow = s.veryLow, low = s.low, high = s.high),
        )
    }

    companion object {
        val INTERVAL_RANGE = 1..30
        val MIN_CHANGE_RANGE = 1..50
        val VERY_LOW_RANGE = 40..55
        val LOW_RANGE = 60..100
        val HIGH_RANGE = 140..300
    }
}
