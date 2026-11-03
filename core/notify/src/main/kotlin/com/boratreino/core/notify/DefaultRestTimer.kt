package com.boratreino.core.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.boratreino.core.model.RestTimerRules
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

/**
 * A coroutine counts down while the process lives; the ongoing notification counts down by itself
 * (system chronometer); an inexact alarm that may run in Doze ends the rest if the process is gone.
 */
internal class DefaultRestTimer internal constructor(
    private val context: Context,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val settings: TrainingSettings? = null,
) : RestTimer {

    @Inject
    constructor(@ApplicationContext context: Context, clock: Clock, settings: TrainingSettings) :
        this(context, clock, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), settings)

    private val _state = MutableStateFlow(RestTimerState())
    override val state: StateFlow<RestTimerState> = _state.asStateFlow()

    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val finished: SharedFlow<Unit> = _finished.asSharedFlow()

    private var job: Job? = null

    @Synchronized
    override fun start(seconds: Int) {
        val clamped = seconds.coerceIn(RestTimerRules.MIN_SECONDS, RestTimerRules.MAX_SECONDS)
        Notifications.cancel(context, Notifications.REST_OVER_ID)
        run(clock.instant().plusSeconds(clamped.toLong()), clamped)
    }

    @Synchronized
    override fun extend(seconds: Int) {
        val endsAt = _state.value.endsAt ?: return
        val extended = endsAt.plusSeconds(seconds.toLong())
        val longest = clock.instant().plusSeconds(RestTimerRules.MAX_SECONDS.toLong())
        val newEnd = if (extended.isAfter(longest)) longest else extended
        val added = (newEnd.epochSecond - endsAt.epochSecond).toInt().coerceAtLeast(0)
        run(newEnd, _state.value.durationSeconds + added)
    }

    @Synchronized
    override fun skip() {
        job?.cancel()
        job = null
        _state.value = RestTimerState()
        cancelAlarm()
        Notifications.cancel(context, Notifications.REST_ONGOING_ID)
        Notifications.cancel(context, Notifications.REST_OVER_ID)
    }

    private fun run(endsAt: Instant, durationSeconds: Int) {
        job?.cancel()
        _state.value = RestTimerState(endsAt, durationSeconds)
        Notifications.postRestOngoing(context, endsAt)
        setAlarm(endsAt)
        val wait = (endsAt.toEpochMilli() - clock.millis()).coerceAtLeast(0L)
        job = scope.launch {
            delay(wait)
            complete(endsAt)
        }
    }

    @Synchronized
    private fun complete(endsAt: Instant) {
        // A newer start/extend replaced this run while the coroutine was waking up.
        if (_state.value.endsAt != endsAt) return
        job = null
        _state.value = RestTimerState()
        cancelAlarm()
        Notifications.postRestOver(context)
        _finished.tryEmit(Unit)
        scope.launch {
            // The alarm is cancelled above, so this is the only beep for this rest.
            val beep = try {
                settings?.restTimer?.first()?.beep == true
            } catch (e: Exception) {
                false
            }
            if (beep) RestBeep.play()
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        Intent(context, RestAlarmReceiver::class.java).setAction(RestAlarmReceiver.ACTION_REST_OVER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun setAlarm(endsAt: Instant) {
        try {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt.toEpochMilli(), alarmIntent())
        } catch (e: RuntimeException) {
            // No alarm service or not allowed: the in-process timer still runs.
        }
    }

    private fun cancelAlarm() {
        try {
            context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent())
        } catch (e: RuntimeException) {
            // Nothing to cancel.
        }
    }

    private companion object {
        const val ALARM_REQUEST = 1
    }
}
