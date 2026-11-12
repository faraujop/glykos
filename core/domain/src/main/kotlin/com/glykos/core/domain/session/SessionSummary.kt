package com.glykos.core.domain.session

import com.glykos.core.domain.announce.GlucoseThresholds
import com.glykos.core.domain.model.GlucoseReading
import kotlinx.datetime.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Readings of one finished session, sorted by time. In memory only (see [SessionRecorder]). */
data class SessionData(
    val start: Instant,
    val end: Instant,
    val readings: List<GlucoseReading>,
) {
    val duration: Duration get() = (end - start).coerceAtLeast(Duration.ZERO)

    /**
     * Splits the session time by glucose range, using the same limits as the alarms
     * (below = `< low`, above = `> high`).
     *
     * Each reading covers the time until the next one (or the end of the session). If that is
     * longer than [MAX_GAP] it is a gap: the reading only covers [READING_INTERVAL] and the rest is
     * **no data**. Time without data is never counted as in range, so a failure cannot look like a
     * good result.
     */
    fun summarize(thresholds: GlucoseThresholds): SessionSummary {
        var below = Duration.ZERO
        var inRange = Duration.ZERO
        var above = Duration.ZERO

        readings.forEachIndexed { i, reading ->
            val boundary = readings.getOrNull(i + 1)?.timestamp ?: end
            val coverEnd = if (boundary - reading.timestamp <= MAX_GAP) boundary else reading.timestamp + READING_INTERVAL
            val covered = (minOf(coverEnd, end) - maxOf(reading.timestamp, start)).coerceAtLeast(Duration.ZERO)
            when {
                reading.valueMgDl < thresholds.low -> below += covered
                reading.valueMgDl > thresholds.high -> above += covered
                else -> inRange += covered
            }
        }

        val inSession = readings.filter { it.timestamp <= end }
        return SessionSummary(
            data = this,
            timeBelow = below,
            timeInRange = inRange,
            timeAbove = above,
            timeNoData = (duration - below - inRange - above).coerceAtLeast(Duration.ZERO),
            first = inSession.firstOrNull(),
            last = inSession.lastOrNull(),
            min = inSession.minOfOrNull { it.valueMgDl },
            max = inSession.maxOfOrNull { it.valueMgDl },
            segments = segments(inSession),
            thresholds = thresholds,
        )
    }

    /** Runs of readings with no gap longer than [MAX_GAP]; the chart draws a line per run. */
    private fun segments(readings: List<GlucoseReading>): List<List<GlucoseReading>> {
        val result = mutableListOf<MutableList<GlucoseReading>>()
        readings.forEach { reading ->
            val current = result.lastOrNull()
            if (current != null && reading.timestamp - current.last().timestamp <= MAX_GAP) {
                current += reading
            } else {
                result += mutableListOf(reading)
            }
        }
        return result
    }

    companion object {
        /** LibreLinkUp publishes a new reading every minute. */
        val READING_INTERVAL = 1.minutes

        /** Longer than this without a new reading is a gap ("sin datos"). */
        val MAX_GAP = 5.minutes
    }
}

data class SessionSummary(
    val data: SessionData,
    val timeBelow: Duration,
    val timeInRange: Duration,
    val timeAbove: Duration,
    val timeNoData: Duration,
    val first: GlucoseReading?,
    val last: GlucoseReading?,
    val min: Int?,
    val max: Int?,
    val segments: List<List<GlucoseReading>>,
    val thresholds: GlucoseThresholds,
) {
    /** Share of the whole session, 0–100. */
    fun percent(part: Duration): Int {
        val total = data.duration
        if (total == Duration.ZERO) return 0
        return (part / total * 100).roundToInt()
    }
}
