package com.glykos.core.domain.monitor

import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.AnnouncementPolicy
import com.glykos.core.domain.model.GlucoseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Somewhere an announcement can be delivered (voice, watch...). Throws if it could not deliver. */
interface AnnouncementSink {
    val name: String
    suspend fun deliver(announcement: Announcement)
}

data class OutputError(val sink: String, val message: String)

data class MonitorState(
    val lastResult: GlucoseResult? = null,
    val lastCheckAt: Instant? = null,
    val lastAnnouncement: Announcement? = null,
    val outputErrors: List<OutputError> = emptyList(),
)

/**
 * Poll → decide → deliver loop. Platform-free: the Android service only hosts it.
 *
 * - A source that throws is reported as no data (never silence).
 * - Each sink is delivered independently: one failing does not stop the others.
 * - An announcement only counts as made when at least one sink delivered it; otherwise it is
 *   retried on the next tick.
 */
class GlucoseMonitor(
    private val policy: AnnouncementPolicy,
    private val fetch: suspend () -> GlucoseResult,
    private val sinks: List<AnnouncementSink>,
    private val clock: Clock = Clock.System,
    private val pollInterval: Duration = 60.seconds,
) {
    init {
        require(pollInterval >= MIN_POLL_INTERVAL) { "LibreLinkUp must not be polled more than once a minute" }
    }

    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private var previous: Announcement? = null

    /** Runs until cancelled. */
    suspend fun run(): Nothing {
        while (true) {
            tick()
            delay(pollInterval)
        }
    }

    suspend fun tick() {
        val result = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GlucoseResult.Unavailable(GlucoseResult.Reason.UNKNOWN)
        }
        val now = clock.now()
        _state.update { it.copy(lastResult = result, lastCheckAt = now) }

        val announcement = policy.decide(result, now, previous) ?: return
        val errors = sinks.mapNotNull { sink ->
            try {
                sink.deliver(announcement)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OutputError(sink.name, e.message ?: e::class.simpleName.orEmpty())
            }
        }
        val delivered = errors.size < sinks.size
        if (delivered) previous = announcement
        _state.update {
            it.copy(lastAnnouncement = if (delivered) announcement else it.lastAnnouncement, outputErrors = errors)
        }
    }

    private companion object {
        val MIN_POLL_INTERVAL = 60.seconds
    }
}
