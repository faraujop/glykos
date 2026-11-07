package com.glykos.core.domain.announce

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Glucose limits in mg/dl. Defaults follow the international Time-in-Range consensus:
 * very low < 54, low < 70, high > 180.
 */
data class GlucoseThresholds(
    val veryLow: Int = 54,
    val low: Int = 70,
    val high: Int = 180,
) {
    init {
        require(veryLow < low && low < high) { "Thresholds must satisfy veryLow < low < high" }
    }
}

/**
 * @property interval time between routine announcements.
 * @property minChangeMgDl if set, routine announcements are skipped when the change since the last
 *   one is smaller. Never applies to alarms.
 * @property staleAfter readings older than this are treated as no data.
 * @property alarmRepeat how often an ongoing alarm (including no data) is repeated.
 */
data class AnnouncementConfig(
    val interval: Duration = 5.minutes,
    val minChangeMgDl: Int? = null,
    val thresholds: GlucoseThresholds = GlucoseThresholds(),
    val staleAfter: Duration = 10.minutes,
    val alarmRepeat: Duration = 5.minutes,
) {
    init {
        require(interval.isPositive()) { "interval must be positive" }
        require(staleAfter.isPositive()) { "staleAfter must be positive" }
        require(alarmRepeat.isPositive()) { "alarmRepeat must be positive" }
        require(minChangeMgDl == null || minChangeMgDl > 0) { "minChangeMgDl must be positive" }
    }
}
