package com.glykos.core.domain.announce

import com.glykos.core.domain.model.GlucoseResult
import kotlinx.datetime.Instant

/**
 * Something the user must be told, produced by [AnnouncementPolicy] and rendered by the
 * voice / watch formatters.
 *
 * @property deltaMgDl change against the last announced valid reading (what the user last heard),
 *   or null when there is no comparable previous reading.
 * @property at moment the announcement was decided.
 */
data class Announcement(
    val kind: Kind,
    val result: GlucoseResult,
    val deltaMgDl: Int?,
    val at: Instant,
) {
    enum class Kind {
        ROUTINE,
        LOW,
        VERY_LOW,
        HIGH,

        /** Source failure or stale reading. Must always reach the user ("sin datos"). */
        NO_DATA,
        ;

        val isAlarm: Boolean get() = this != ROUTINE
    }
}
