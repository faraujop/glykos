package com.glykos.feature.output

import com.glykos.core.domain.model.GlucoseResult

/**
 * Swappable channel to deliver glucose info to the user.
 *
 * Planned implementations: `VoiceOutput` (TTS with ducking, es-ES),
 * `NotificationWatchOutput` (new notification per reading, forwarded to Garmin),
 * and in the future `ConnectIqOutput`.
 *
 * [GlucoseResult.Unavailable] must be delivered too ("sin datos"), never ignored.
 */
interface GlucoseOutput {
    suspend fun deliver(result: GlucoseResult)
}
