package com.boratreino.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fired by AlarmManager when a rest ends. It swaps the countdown notification for "Rest over"
 * (alert-once, so the in-process timer finishing too does not buzz twice) and, when the setting
 * is on, beeps (the in-process timer cancels this alarm when it finishes first, so one beep only).
 */
internal class RestAlarmReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    internal interface SettingsEntryPoint {
        fun trainingSettings(): TrainingSettings
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_OVER) return
        Notifications.postRestOver(context)
        // One signal per rest: if the in-process timer already ended this rest, it buzzed and beeped.
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
        if (!RestSignal.claim(endsAt)) return
        RestSignal.buzz(context)
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).trainingSettings()
                if (settings.restTimer.first().beep) RestBeep.play()
            } catch (e: Exception) {
                // Settings unreadable: no beep.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REST_OVER = "com.boratreino.core.notify.REST_OVER"
        const val EXTRA_ENDS_AT = "ends_at_millis"
    }
}
