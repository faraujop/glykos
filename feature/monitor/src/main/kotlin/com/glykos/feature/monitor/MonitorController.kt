package com.glykos.feature.monitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.monitor.MonitorState
import com.glykos.core.domain.session.SessionData
import com.glykos.core.domain.session.SessionRecorder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import kotlinx.datetime.Clock
import javax.inject.Singleton

data class ServiceStatus(
    val running: Boolean = false,
    val monitor: MonitorState = MonitorState(),
    /** Why the service could not start or stopped, shown in the UI. */
    val error: String? = null,
    /**
     * Readings of the session that just ended, for the summary screen. In memory only: cleared when
     * the summary is closed, when a new session starts, or when the process dies.
     */
    val finishedSession: SessionData? = null,
)

/** Starts/stops [MonitorService] and exposes its live status to the UI. */
@Singleton
class MonitorController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    private var recorder: SessionRecorder? = null

    fun start() {
        _status.value = _status.value.copy(error = null, finishedSession = null)
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
    }

    fun stop() {
        context.startService(Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
    }

    /** Closes the summary and forgets its readings. */
    fun dismissSession() {
        _status.value = _status.value.copy(finishedSession = null)
    }

    internal fun beginSession() {
        if (recorder == null) recorder = SessionRecorder(Clock.System.now())
    }

    internal fun record(result: GlucoseResult?) {
        result?.let { recorder?.record(it) }
    }

    /** Idempotent: the service may end the session both on stop and on destroy. */
    internal fun endSession() {
        val data = recorder?.finish(Clock.System.now()) ?: return
        recorder = null
        _status.value = _status.value.copy(finishedSession = data)
    }

    internal fun update(transform: (ServiceStatus) -> ServiceStatus) {
        _status.value = transform(_status.value)
    }
}
