package com.boratreino.core.notify

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.Instant

/** Creates the notification channels. Safe to call more than once; the app calls it at startup. */
fun createNotificationChannels(context: Context) {
    try {
        val manager = NotificationManagerCompat.from(context)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(Notifications.REST_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notify_rest_channel))
                .setDescription(context.getString(R.string.notify_rest_channel_description))
                .setVibrationEnabled(true)
                .setVibrationPattern(Notifications.REST_VIBRATION)
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(Notifications.REMINDERS_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notify_reminders_channel))
                .setDescription(context.getString(R.string.notify_reminders_channel_description))
                .build(),
        )
    } catch (e: RuntimeException) {
        // No notification service: nothing to create, nothing to show.
    }
}

/** Builds and posts this module's notifications. Every post is guarded by the permission and never throws. */
internal object Notifications {
    const val REST_CHANNEL_ID = "rest"
    const val REMINDERS_CHANNEL_ID = "reminders"
    const val REST_ONGOING_ID = 1_001
    const val REST_OVER_ID = 1_002
    const val REMINDER_ID = 2_001
    val REST_VIBRATION = longArrayOf(0, 250, 120, 250)

    /** Opens the app without this module having to know its activity. */
    fun openAppIntent(context: Context): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    /** Silent, ongoing, with a system chronometer counting down to [endsAt]. */
    fun postRestOngoing(context: Context, endsAt: Instant) {
        val notification = NotificationCompat.Builder(context, REST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_rest))
            .setContentText(context.getString(R.string.notify_next_set))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(endsAt.toEpochMilli())
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setContentIntent(openAppIntent(context))
            .build()
        post(context, REST_ONGOING_ID, notification)
    }

    /**
     * Swaps the countdown for the alert. Alert-once, so the alarm and the in-process timer can both
     * call it and the phone buzzes once.
     */
    fun postRestOver(context: Context) {
        cancel(context, REST_ONGOING_ID)
        val notification = NotificationCompat.Builder(context, REST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notify_rest_over))
            .setContentText(context.getString(R.string.notify_next_set))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(REST_VIBRATION)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        post(context, REST_OVER_ID, notification)
    }

    fun postReminder(context: Context, title: String, text: String?) {
        val builder = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
        if (text != null) {
            builder.setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        post(context, REMINDER_ID, builder.build())
    }

    fun cancel(context: Context, id: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(id)
        } catch (e: RuntimeException) {
            // Notification service unavailable.
        }
    }

    @Suppress("MissingPermission") // Checked by NotificationPermission.isGranted before notify.
    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        if (!NotificationPermission.isGranted(context)) return
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: RuntimeException) {
            // SecurityException or a missing service: the timer still works without the notification.
        }
    }
}
