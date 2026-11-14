package com.glykos.core.data.demo

import com.glykos.core.data.source.GlucoseSource
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.abs

/**
 * Made-up glucose for the Google Play reviewer, who has no LibreLinkUp account. No network.
 *
 * Plays [curve] one point per minute from the first call, looping, with fresh timestamps so the
 * stale alarm never fires. Every reading is marked [GlucoseReading.simulated] so all outputs
 * announce it as demo.
 */
class DemoGlucoseSource(
    private val clock: Clock = Clock.System,
    private val curve: List<Int> = RUN_CURVE,
) : GlucoseSource {

    init {
        require(curve.size >= 2) { "Demo curve needs at least two points" }
    }

    private var startedAt: Instant? = null

    override suspend fun latest(): GlucoseResult {
        val now = clock.now()
        val start = startedAt ?: now.also { startedAt = it }
        val step = ((now - start).inWholeMinutes % curve.size).toInt()
        val value = curve[step]
        val previous = curve[(step - 1 + curve.size) % curve.size]
        return GlucoseResult.Available(GlucoseReading(value, trend(value - previous), now, simulated = true))
    }

    private fun trend(delta: Int): Trend = when {
        abs(delta) <= 2 -> Trend.STABLE
        delta > 15 -> Trend.RISING_FAST
        delta > 0 -> Trend.RISING
        delta < -15 -> Trend.FALLING_FAST
        else -> Trend.FALLING
    }

    companion object {
        /** A run compressed into 30 minutes: starts high, drops below 70 (alarms), recovers. */
        val RUN_CURVE = listOf(
            176, 184, 192, 196, 190, 178, 164, 150, 138, 128,
            118, 110, 102, 95, 88, 82, 76, 71, 66, 62,
            60, 63, 69, 76, 84, 92, 104, 118, 134, 156,
        )
    }
}
