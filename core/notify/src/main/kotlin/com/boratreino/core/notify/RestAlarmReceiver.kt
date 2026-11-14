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
import kotlinx.coroutines.withTimeoutOrNull
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
        // 0 means a stale intent (the rest was cancelled while this alarm was being delivered): signal nothing.
        val endsAt = intent.getLongExtra(EXTRA_ENDS_AT, 0L)
        if (endsAt <= 0L) return
        // Keep the broadcast open first: finishing it lets the system freeze the process, which cut the beep.
        val pending = goAsync()
        Notifications.postRestOver(context)
        // One signal per rest: if the in-process timer already ended this rest, it buzzed and beeped.
        if (!RestSignal.claim(endsAt)) {
            pending.finish()
            return
        }
        RestSignal.buzz(context)
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                // Well under the ~10 s limit of a broadcast.
                withTimeoutOrNull(WAIT_LIMIT_MS) {
                    val settings = EntryPointAccessors.fromApplication(app, SettingsEntryPoint::class.java).trainingSettings()
                    if (settings.restTimer.first().beep) RestBeep.playAndWait()
                }
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
        private const val WAIT_LIMIT_MS = 5_000L
    }
}
