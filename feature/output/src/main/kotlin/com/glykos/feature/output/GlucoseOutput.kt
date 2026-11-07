package com.glykos.feature.output

import com.glykos.core.domain.announce.Announcement

/**
 * Swappable channel to deliver glucose info to the user.
 *
 * Implementations: [VoiceOutput] (TTS with ducking, es-ES), [NotificationWatchOutput]
 * (new notification per reading, forwarded to Garmin) and in the future `ConnectIqOutput`.
 *
 * Every [Announcement] must be delivered, including [Announcement.Kind.NO_DATA] ("sin datos").
 *
 * @throws OutputUnavailableException when the channel cannot deliver (no TTS voice, notifications
 *   disabled...). Callers must surface it, never swallow it: a silent output is a safety failure.
 */
interface GlucoseOutput {
    suspend fun deliver(announcement: Announcement)
}

class OutputUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)
