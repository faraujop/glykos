package com.glykos.feature.monitor

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.glykos.core.domain.monitor.MonitorState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ServiceStatus(
    val running: Boolean = false,
    val monitor: MonitorState = MonitorState(),
    /** Why the service could not start or stopped, shown in the UI. */
    val error: String? = null,
)

/** Starts/stops [MonitorService] and exposes its live status to the UI. */
@Singleton
class MonitorController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    fun start() {
        _status.value = _status.value.copy(error = null)
        ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
    }

    fun stop() {
        context.startService(Intent(context, MonitorService::class.java).setAction(MonitorService.ACTION_STOP))
    }

    internal fun update(transform: (ServiceStatus) -> ServiceStatus) {
        _status.value = transform(_status.value)
    }
}
