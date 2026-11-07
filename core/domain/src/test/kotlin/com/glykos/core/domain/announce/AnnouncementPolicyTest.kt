package com.glykos.core.domain.announce

import com.glykos.core.domain.announce.Announcement.Kind
import com.glykos.core.domain.model.GlucoseReading
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class AnnouncementPolicyTest {

    private val t0 = Instant.parse("2026-11-07T10:00:00Z")
    private val policy = AnnouncementPolicy(AnnouncementConfig())

    private fun available(value: Int, at: Instant = t0, trend: Trend = Trend.STABLE) =
        GlucoseResult.Available(GlucoseReading(valueMgDl = value, trend = trend, timestamp = at))

    /** Announcement made at [at] for a fresh reading of [value]. */
    private fun announced(value: Int, at: Instant, kind: Kind = Kind.ROUTINE) =
        Announcement(kind = kind, result = available(value, at), deltaMgDl = null, at = at)

    /** Fresh reading [value] taken at [now] (the sensor timestamp equals the poll time). */
    private fun decide(value: Int, now: Instant, previous: Announcement?, p: AnnouncementPolicy = policy) =
        p.decide(available(value, now), now, previous)

    @Nested
    inner class Classification {

        @ParameterizedTest
        @CsvSource(
            "40, VERY_LOW",
            "53, VERY_LOW",
            "54, LOW",
            "69, LOW",
            "70, ROUTINE",
            "180, ROUTINE",
            "181, HIGH",
            "400, HIGH",
        )
        fun `classifies fresh readings by default thresholds`(value: Int, expected: Kind) {
            assertEquals(expected, policy.classify(available(value), now = t0))
        }

        @Test
        fun `stale reading is no data even if the value is in range`() {
            val result = available(110, at = t0)
            assertEquals(Kind.NO_DATA, policy.classify(result, now = t0 + 10.minutes + 1.seconds))
        }

        @Test
        fun `reading exactly at the stale limit is still valid`() {
            assertEquals(Kind.ROUTINE, policy.classify(available(110, at = t0), now = t0 + 10.minutes))
        }

        @Test
        fun `stale low reading is no data, since the value can no longer be trusted`() {
            assertEquals(Kind.NO_DATA, policy.classify(available(50, at = t0), now = t0 + 20.minutes))
        }

        @Test
        fun `unavailable source is no data`() {
            val result = GlucoseResult.Unavailable(GlucoseResult.Reason.NETWORK)
            assertEquals(Kind.NO_DATA, policy.classify(result, now = t0))
        }

        @Test
        fun `custom thresholds are respected`() {
            val custom = AnnouncementPolicy(
                AnnouncementConfig(thresholds = GlucoseThresholds(veryLow = 60, low = 80, high = 160)),
            )
            assertEquals(Kind.VERY_LOW, custom.classify(available(59), t0))
            assertEquals(Kind.LOW, custom.classify(available(79), t0))
            assertEquals(Kind.HIGH, custom.classify(available(161), t0))
        }

        @Test
        fun `thresholds must be ordered`() {
            assertThrows<IllegalArgumentException> { GlucoseThresholds(veryLow = 70, low = 70, high = 180) }
            assertThrows<IllegalArgumentException> { GlucoseThresholds(veryLow = 54, low = 180, high = 180) }
        }
    }

    @Nested
    inner class Routine {

        @Test
        fun `first reading is always announced`() {
            val a = decide(112, t0, previous = null)
            assertEquals(Kind.ROUTINE, a?.kind)
            assertNull(a?.deltaMgDl)
        }

        @Test
        fun `in range reading before the interval is not announced`() {
            assertNull(decide(112, t0 + 4.minutes, previous = announced(120, t0)))
        }

        @Test
        fun `in range reading after the interval is announced with delta since last announcement`() {
            val a = decide(112, t0 + 5.minutes, previous = announced(120, t0))
            assertEquals(Kind.ROUTINE, a?.kind)
            assertEquals(-8, a?.deltaMgDl)
            assertEquals(t0 + 5.minutes, a?.at)
        }

        @Test
        fun `custom interval is respected`() {
            val p = AnnouncementPolicy(AnnouncementConfig(interval = 10.minutes))
            assertNull(decide(112, t0 + 9.minutes, announced(120, t0), p))
            assertNotNull(decide(112, t0 + 10.minutes, announced(120, t0), p))
        }

        @Test
        fun `with min change, small changes are not announced`() {
            val p = AnnouncementPolicy(AnnouncementConfig(minChangeMgDl = 10))
            assertNull(decide(111, t0 + 5.minutes, announced(120, t0), p))
        }

        @Test
        fun `with min change, changes at or above it are announced in either direction`() {
            val p = AnnouncementPolicy(AnnouncementConfig(minChangeMgDl = 10))
            assertEquals(-10, decide(110, t0 + 5.minutes, announced(120, t0), p)?.deltaMgDl)
            assertEquals(15, decide(135, t0 + 5.minutes, announced(120, t0), p)?.deltaMgDl)
        }

        @Test
        fun `min change still waits for the interval`() {
            val p = AnnouncementPolicy(AnnouncementConfig(minChangeMgDl = 10))
            assertNull(decide(150, t0 + 2.minutes, announced(120, t0), p))
        }
    }

    @Nested
    inner class Alarms {

        @Test
        fun `entering low is announced immediately, ignoring the interval`() {
            val a = decide(65, t0 + 1.minutes, previous = announced(75, t0))
            assertEquals(Kind.LOW, a?.kind)
            assertEquals(-10, a?.deltaMgDl)
        }

        @Test
        fun `escalating from low to very low is announced immediately`() {
            val a = decide(50, t0 + 1.minutes, previous = announced(60, t0, Kind.LOW))
            assertEquals(Kind.VERY_LOW, a?.kind)
        }

        @Test
        fun `entering high is announced immediately`() {
            assertEquals(Kind.HIGH, decide(190, t0 + 1.minutes, announced(175, t0))?.kind)
        }

        @Test
        fun `returning to range from an alarm is announced immediately`() {
            assertEquals(Kind.ROUTINE, decide(72, t0 + 1.minutes, announced(68, t0, Kind.LOW))?.kind)
        }

        @Test
        fun `ongoing alarm is not repeated before the repeat interval`() {
            assertNull(decide(66, t0 + 4.minutes, announced(65, t0, Kind.LOW)))
        }

        @Test
        fun `ongoing alarm repeats after the repeat interval`() {
            assertEquals(Kind.LOW, decide(66, t0 + 5.minutes, announced(65, t0, Kind.LOW))?.kind)
        }

        @Test
        fun `alarms ignore min change`() {
            val p = AnnouncementPolicy(AnnouncementConfig(minChangeMgDl = 20))
            assertEquals(Kind.LOW, decide(68, t0 + 1.minutes, announced(72, t0), p)?.kind)
            assertEquals(Kind.LOW, decide(68, t0 + 5.minutes, announced(68, t0, Kind.LOW), p)?.kind)
        }

        @Test
        fun `custom alarm repeat is respected`() {
            val p = AnnouncementPolicy(AnnouncementConfig(alarmRepeat = 2.minutes))
            assertEquals(Kind.LOW, decide(66, t0 + 2.minutes, announced(65, t0, Kind.LOW), p)?.kind)
        }
    }

    @Nested
    inner class NoData {

        private val unavailable = GlucoseResult.Unavailable(GlucoseResult.Reason.NETWORK)

        @Test
        fun `losing data is announced immediately`() {
            val a = policy.decide(unavailable, t0 + 1.minutes, previous = announced(112, t0))
            assertEquals(Kind.NO_DATA, a?.kind)
            assertNull(a?.deltaMgDl)
        }

        @Test
        fun `no data repeats after the alarm repeat interval`() {
            val previous = Announcement(Kind.NO_DATA, unavailable, deltaMgDl = null, at = t0)
            assertNull(policy.decide(unavailable, t0 + 4.minutes, previous))
            assertEquals(Kind.NO_DATA, policy.decide(unavailable, t0 + 5.minutes, previous)?.kind)
        }

        @Test
        fun `recovering data is announced immediately with no delta against the no data announcement`() {
            val previous = Announcement(Kind.NO_DATA, unavailable, deltaMgDl = null, at = t0)
            val a = decide(112, t0 + 1.minutes, previous)
            assertEquals(Kind.ROUTINE, a?.kind)
            assertNull(a?.deltaMgDl)
        }

        @Test
        fun `a stale reading behind a no data announcement is not used as delta base`() {
            val previous = Announcement(Kind.NO_DATA, available(90, at = t0 - 20.minutes), deltaMgDl = null, at = t0)
            assertNull(decide(112, t0 + 1.minutes, previous)?.deltaMgDl)
        }

        @Test
        fun `reading going stale is announced as no data`() {
            val now = t0 + 11.minutes
            val a = policy.decide(available(112, at = t0), now, previous = announced(112, t0))
            assertEquals(Kind.NO_DATA, a?.kind)
        }
    }

    @Test
    fun `config rejects non positive durations`() {
        assertThrows<IllegalArgumentException> { AnnouncementConfig(interval = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { AnnouncementConfig(alarmRepeat = Duration.ZERO) }
        assertThrows<IllegalArgumentException> { AnnouncementConfig(staleAfter = Duration.ZERO) }
    }

    @Test
    fun `config rejects non positive min change`() {
        assertThrows<IllegalArgumentException> { AnnouncementConfig(minChangeMgDl = 0) }
    }
}
