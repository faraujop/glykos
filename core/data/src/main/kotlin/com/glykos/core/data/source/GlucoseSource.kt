package com.glykos.core.data.source

import com.glykos.core.domain.model.GlucoseResult

/**
 * Swappable glucose data source.
 *
 * Planned implementations:
 * - `LibreLinkUpSource`: unofficial LibreLinkUp API, follower account, polling (max every 60 s).
 * - `JugglucoSource` (future): local broadcast, offline.
 *
 * Implementations must never throw for expected failures: return [GlucoseResult.Unavailable].
 */
interface GlucoseSource {
    suspend fun latest(): GlucoseResult
}
