package com.glykos.core.data.librelinkup

import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class LibreLinkUpMappingTest {

    @ParameterizedTest
    @CsvSource(
        "'11/7/2026 10:05:03 AM', 2026-11-07T10:05:03Z",
        "'1/2/2026 3:04:05 PM', 2026-01-02T15:04:05Z",
        "'12/31/2026 11:59:59 PM', 2026-12-31T23:59:59Z",
        "'11/7/2026 12:00:00 PM', 2026-11-07T12:00:00Z",
        "'11/7/2026 12:30:00 AM', 2026-11-07T00:30:00Z",
        "'03/09/2026 07:08:09 AM', 2026-03-09T07:08:09Z",
    )
    fun `parses factory timestamps as UTC`(raw: String, expected: String) {
        assertEquals(Instant.parse(expected), parseFactoryTimestamp(raw))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "2026-11-07T10:00:00Z", "13/1/2026 1:00:00 AM", "11/7/2026 13:00:00 PM", "11/7/2026 10:00:00"])
    fun `rejects malformed timestamps`(raw: String) {
        assertNull(parseFactoryTimestamp(raw))
    }

    @ParameterizedTest
    @CsvSource(
        "1, FALLING_FAST",
        "2, FALLING",
        "3, STABLE",
        "4, RISING",
        "5, RISING_FAST",
        "0, UNKNOWN",
        "6, UNKNOWN",
    )
    fun `maps trend arrows`(arrow: Int, expected: Trend) {
        assertEquals(expected, trendFromArrow(arrow))
    }

    @Test
    fun `missing trend arrow is unknown`() {
        assertEquals(Trend.UNKNOWN, trendFromArrow(null))
    }

    @Test
    fun `account id is the lowercase hex sha256 of the user id`() {
        // printf 'user-123' | sha256sum
        assertEquals("fcdec6df4d44dbc637c7c5b58efface52a7f8a88535423430255be0bb89bedd8", accountIdHash("user-123"))
    }
}
