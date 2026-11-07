package com.glykos.feature.output

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glykos.core.domain.announce.Announcement
import com.glykos.core.domain.format.NotificationMessageFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts each announcement as a NEW notification so Garmin Connect forwards it to the watch
 * (silent updates of an existing notification may not be forwarded). The previous one is
 * cancelled first, so only the latest stays in the shade.
 *
 * Two channels: routine readings (default importance, no sound: the voice already talks) and
 * alarms (high importance, sound and vibration).
 * Verified 2026-11-07 (Redmi Note 12 5G, Android 14 + Garmin Connect + FR35): both channels,
 * including the silent readings one, reach the watch.
 */
@Singleton
class NotificationWatchOutput @Inject constructor(
    @ApplicationContext private val context: Context,
) : GlucoseOutput {

    private val manager = NotificationManagerCompat.from(context)
    private val counter = AtomicInteger(0)
    @Volatile private var lastId: Int? = null

    init {
        createChannels()
    }

    @SuppressLint("MissingPermission") // checked via areNotificationsEnabled()
    override suspend fun deliver(announcement: Announcement) {
        if (!manager.areNotificationsEnabled()) {
            throw OutputUnavailableException("Notifications are disabled for Glykos")
        }
        val alarm = announcement.kind.isAlarm
        val channel = if (alarm) CHANNEL_ALARMS else CHANNEL_READINGS
        if (!manager.areChannelEnabled(channel)) {
            throw OutputUnavailableException("Notification channel $channel is disabled")
        }

        val text = NotificationMessageFormatter.format(announcement)
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_glucose)
            .setContentTitle(text)
            .setWhen(announcement.at.toEpochMilliseconds())
            .setShowWhen(true)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_STATUS)
            .setPriority(if (alarm) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

        lastId?.let(manager::cancel)
        val id = FIRST_ID + Math.floorMod(counter.getAndIncrement(), ID_RANGE)
        manager.notify(id, notification)
        lastId = id
    }

    private fun NotificationManagerCompat.areChannelEnabled(id: String): Boolean =
        getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE

    private fun createChannels() {
        val readings = NotificationChannel(
            CHANNEL_READINGS,
            context.getString(R.string.channel_readings_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_readings_description)
            setSound(null, null)
            enableVibration(false)
        }
        val alarms = NotificationChannel(
            CHANNEL_ALARMS,
            context.getString(R.string.channel_alarms_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarms_description)
            enableVibration(true)
        }
        manager.createNotificationChannels(listOf(readings, alarms))
    }

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    companion object {
        const val CHANNEL_READINGS = "glucose_readings"
        const val CHANNEL_ALARMS = "glucose_alarms"
        private const val FIRST_ID = 1_000
        private const val ID_RANGE = 9_000
    }
}
