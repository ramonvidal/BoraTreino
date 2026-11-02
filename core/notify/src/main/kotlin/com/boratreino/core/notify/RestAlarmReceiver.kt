package com.boratreino.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fired by AlarmManager when a rest ends. No dependency injection: it only swaps the countdown
 * notification for "Rest over" (alert-once, so the in-process timer finishing too does not buzz twice).
 */
internal class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_OVER) return
        Notifications.postRestOver(context)
    }

    companion object {
        const val ACTION_REST_OVER = "com.boratreino.core.notify.REST_OVER"
    }
}
