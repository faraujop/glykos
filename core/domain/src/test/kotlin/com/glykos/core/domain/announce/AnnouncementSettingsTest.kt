package com.glykos.core.domain.announce

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

class AnnouncementSettingsTest {

    @Test
    fun `defaults match the default announcement config`() {
        assertEquals(AnnouncementConfig(), AnnouncementSettings().toConfig())
    }

    @Test
    fun `maps every field to the config`() {
        val config = AnnouncementSettings(intervalMinutes = 10, minChangeMgDl = 5, veryLow = 50, low = 80, high = 200).toConfig()
        assertEquals(10.minutes, config.interval)
        assertEquals(5, config.minChangeMgDl)
        assertEquals(GlucoseThresholds(veryLow = 50, low = 80, high = 200), config.thresholds)
    }

    @Test
    fun `stale limit and alarm repeat are not user settings`() {
        val config = AnnouncementSettings(intervalMinutes = 30).toConfig()
        assertEquals(AnnouncementConfig().staleAfter, config.staleAfter)
        assertEquals(AnnouncementConfig().alarmRepeat, config.alarmRepeat)
    }

    @Test
    fun `out of range values are clamped`() {
        val s = AnnouncementSettings(intervalMinutes = 0, minChangeMgDl = 500, veryLow = 10, low = 200, high = 1000).sanitized()
        assertEquals(AnnouncementSettings(intervalMinutes = 1, minChangeMgDl = 50, veryLow = 40, low = 100, high = 300), s)
    }

    @Test
    fun `values above range are clamped down and below range up`() {
        val s = AnnouncementSettings(intervalMinutes = 99, veryLow = 99, low = 10, high = 10).sanitized()
        assertEquals(30, s.intervalMinutes)
        assertEquals(55, s.veryLow)
        assertEquals(60, s.low)
        assertEquals(140, s.high)
    }

    @Test
    fun `non positive min change means disabled`() {
        assertNull(AnnouncementSettings(minChangeMgDl = 0).sanitized().minChangeMgDl)
        assertNull(AnnouncementSettings(minChangeMgDl = -3).sanitized().minChangeMgDl)
    }

    @Test
    fun `toConfig never fails, even with corrupted stored values`() {
        val config = AnnouncementSettings(intervalMinutes = -5, minChangeMgDl = 0, veryLow = 999, low = -1, high = 0).toConfig()
        assertEquals(GlucoseThresholds(veryLow = 55, low = 60, high = 140), config.thresholds)
        assertEquals(1.minutes, config.interval)
        assertNull(config.minChangeMgDl)
    }

    @Test
    fun `ranges keep thresholds strictly ordered`() {
        assertEquals(true, AnnouncementSettings.VERY_LOW_RANGE.last < AnnouncementSettings.LOW_RANGE.first)
        assertEquals(true, AnnouncementSettings.LOW_RANGE.last < AnnouncementSettings.HIGH_RANGE.first)
    }
}
