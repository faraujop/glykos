package com.glykos.core.domain.announce

import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import kotlinx.datetime.Instant
import kotlin.math.abs

/**
 * Decides whether a new result must be announced.
 *
 * Safety rules (do not weaken without explicit request):
 * - Any change of [Kind] is announced immediately (entering/escalating/leaving an alarm, losing data).
 * - Ongoing alarms, including [Kind.NO_DATA], repeat every [AnnouncementConfig.alarmRepeat].
 * - [AnnouncementConfig.minChangeMgDl] only silences routine announcements.
 */
class AnnouncementPolicy(private val config: AnnouncementConfig) {

    fun classify(result: GlucoseResult, now: Instant): Kind {
        val reading = (result as? GlucoseResult.Available)?.reading ?: return Kind.NO_DATA
        if (reading.isStale(now, config.staleAfter)) return Kind.NO_DATA
        val t = config.thresholds
        return when {
            reading.valueMgDl < t.veryLow -> Kind.VERY_LOW
            reading.valueMgDl < t.low -> Kind.LOW
            reading.valueMgDl > t.high -> Kind.HIGH
            else -> Kind.ROUTINE
        }
    }

    /** @return the announcement to make, or null to stay silent. */
    fun decide(result: GlucoseResult, now: Instant, previous: Announcement?): Announcement? {
        val kind = classify(result, now)
        val current = (result as? GlucoseResult.Available)?.reading?.takeIf { kind != Kind.NO_DATA }
        val delta = current?.let { c -> previous?.validReading()?.let { c.valueMgDl - it.valueMgDl } }
        val announcement = Announcement(kind, result, delta, now)

        if (previous == null || previous.kind != kind) return announcement

        val elapsed = now - previous.at
        if (kind.isAlarm) return announcement.takeIf { elapsed >= config.alarmRepeat }

        if (elapsed < config.interval) return null
        val minChange = config.minChangeMgDl
        if (minChange != null && delta != null && abs(delta) < minChange) return null
        return announcement
    }

    private fun Announcement.validReading(): GlucoseReading? =
        if (kind == Kind.NO_DATA) null else (result as? GlucoseResult.Available)?.reading
}
