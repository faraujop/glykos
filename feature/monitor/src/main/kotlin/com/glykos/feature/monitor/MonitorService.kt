package com.glykos.feature.monitor

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.glykos.core.data.settings.OutputMode
import com.glykos.core.data.settings.SettingsRepository
import com.glykos.core.data.source.GlucoseSource
import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.announce.AnnouncementConfig
import com.glykos.core.domain.announce.AnnouncementPolicy
import com.glykos.core.domain.model.GlucoseResult
import com.glykos.core.domain.monitor.AnnouncementSink
import com.glykos.core.domain.monitor.GlucoseMonitor
import com.glykos.core.domain.monitor.MonitorState
import com.glykos.feature.output.GlucoseOutput
import com.glykos.feature.output.NotificationWatchOutput
import com.glykos.feature.output.VoiceOutput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours

/**
 * Foreground service (type `health`) that hosts [GlucoseMonitor]: polls every 60 s and announces
 * through the configured outputs, with the screen locked and music playing.
 *
 * START_STICKY: if the system kills it, it is recreated and monitoring resumes.
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {

    @Inject lateinit var source: GlucoseSource
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var voice: VoiceOutput
    @Inject lateinit var watch: NotificationWatchOutput
    @Inject lateinit var controller: MonitorController

    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopMonitoring(error = null)
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_STICKY

        if (!canStartHealthService()) {
            // Starting a health FGS without the permission throws; report instead of crashing.
            stopMonitoring(error = "Falta el permiso de actividad física")
            return START_NOT_STICKY
        }
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                STATUS_NOTIFICATION_ID,
                statusNotification(MonitorState()),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                } else {
                    0
                },
            )
        } catch (e: RuntimeException) {
            stopMonitoring(error = "No se pudo iniciar el servicio: ${e.message}")
            return START_NOT_STICKY
        }
        acquireWakeLock()
        job = lifecycleScope.launch {
            try {
                monitor()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Never stop silently: tell the user monitoring is OFF.
                notifyCrash()
                stopMonitoring(error = "La monitorización se ha detenido: ${e.message}")
            }
        }
        return START_STICKY
    }

    private fun notifyCrash() {
        val n = NotificationCompat.Builder(this, NotificationWatchOutput.CHANNEL_ALARMS)
            .setSmallIcon(com.glykos.feature.output.R.drawable.ic_stat_glucose)
            .setContentTitle("⚠️ Glykos se ha detenido")
            .setContentText("Ya no se vigila la glucosa. Abre la app para reiniciar.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(this).notify(CRASH_NOTIFICATION_ID, n)
        }
    }

    private suspend fun monitor() {
        val sinks = when (settings.outputMode.first()) {
            OutputMode.VOICE -> listOf(sink("Voz", voice))
            OutputMode.WATCH -> listOf(sink("Reloj", watch))
            OutputMode.BOTH -> listOf(sink("Voz", voice), sink("Reloj", watch))
        }
        val monitor = GlucoseMonitor(
            policy = AnnouncementPolicy(AnnouncementConfig()),
            fetch = source::latest,
            sinks = sinks,
        )
        controller.update { it.copy(running = true, error = null) }
        lifecycleScope.launch {
            monitor.state.collect { state ->
                controller.update { it.copy(monitor = state) }
                notifyStatus(state)
            }
        }
        monitor.run()
    }

    private fun sink(name: String, output: GlucoseOutput) = object : AnnouncementSink {
        override val name = name
        override suspend fun deliver(announcement: Announcement) = output.deliver(announcement)
    }

    private fun stopMonitoring(error: String?) {
        job?.cancel()
        job = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        controller.update { it.copy(running = false, error = error) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        controller.update { it.copy(running = false) }
        super.onDestroy()
    }

    private fun canStartHealthService(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun acquireWakeLock() {
        val pm = getSystemService(PowerManager::class.java)
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "glykos:monitor").apply {
            // Safety cap so a forgotten session cannot drain the battery forever.
            acquire(WAKE_LOCK_MAX.inWholeMilliseconds)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_STATUS, "Monitorización activa", NotificationManager.IMPORTANCE_LOW)
            .apply {
                description = "Notificación fija mientras Glykos vigila la glucosa"
                setShowBadge(false)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notifyStatus(state: MonitorState) {
        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(this).notify(STATUS_NOTIFICATION_ID, statusNotification(state))
        }
    }

    private fun statusNotification(state: MonitorState): Notification {
        val time = state.lastCheckAt?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it.toEpochMilliseconds()))
        }
        val reading = when (val r = state.lastResult) {
            null -> "Esperando la primera lectura…"
            is GlucoseResult.Available -> "Última: ${r.reading.valueMgDl} mg/dl · comprobado $time"
            is GlucoseResult.Unavailable -> "Sin datos (${r.reason.name.lowercase()}) · $time"
        }
        val errors = state.outputErrors.joinToString("\n") { "⚠️ ${it.sink}: ${it.message}" }
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(com.glykos.feature.output.R.drawable.ic_stat_glucose)
            .setContentTitle("Glykos vigilando tu glucosa")
            .setContentText(if (errors.isEmpty()) reading else errors)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(reading, errors).filter { it.isNotEmpty() }.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, "Parar", stop)
            .build()
    }

    companion object {
        const val ACTION_STOP = "com.glykos.monitor.STOP"
        private const val CHANNEL_STATUS = "monitor_status"
        private const val STATUS_NOTIFICATION_ID = 1
        private const val CRASH_NOTIFICATION_ID = 2
        private val WAKE_LOCK_MAX = 6.hours
    }
}
