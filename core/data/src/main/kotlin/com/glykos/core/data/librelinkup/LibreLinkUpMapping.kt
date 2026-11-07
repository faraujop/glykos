package com.glykos.core.data.librelinkup

import com.glykos.core.domain.model.Trend
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.security.MessageDigest

private val FACTORY_TIMESTAMP = Regex("""^(\d{1,2})/(\d{1,2})/(\d{4}) (\d{1,2}):(\d{2}):(\d{2}) (AM|PM)$""")

/** Parses LibreLinkUp's `FactoryTimestamp` (UTC, US 12-hour format). Returns null if malformed. */
internal fun parseFactoryTimestamp(raw: String): Instant? {
    val g = FACTORY_TIMESTAMP.matchEntire(raw.trim())?.groupValues ?: return null
    val hour12 = g[4].toInt()
    if (hour12 !in 1..12) return null
    val hour24 = when {
        g[7] == "AM" && hour12 == 12 -> 0
        g[7] == "AM" -> hour12
        hour12 == 12 -> 12
        else -> hour12 + 12
    }
    return try {
        LocalDateTime(g[3].toInt(), g[1].toInt(), g[2].toInt(), hour24, g[5].toInt(), g[6].toInt())
            .toInstant(TimeZone.UTC)
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun trendFromArrow(arrow: Int?): Trend = when (arrow) {
    1 -> Trend.FALLING_FAST
    2 -> Trend.FALLING
    3 -> Trend.STABLE
    4 -> Trend.RISING
    5 -> Trend.RISING_FAST
    else -> Trend.UNKNOWN
}

/**
 * Value of the `account-id` header: lowercase hex SHA-256 of the user id.
 * JVM-only (MessageDigest); swap for a KMP hash when :core:data goes multiplatform.
 */
internal fun accountIdHash(userId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(userId.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
