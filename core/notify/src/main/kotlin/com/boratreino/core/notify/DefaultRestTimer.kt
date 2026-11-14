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
 * (system chronometer); an exact alarm-clock alarm ends the rest if the process is asleep or gone.
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
            // delay() only counts time the device is awake, so one long delay would fire late after the screen
            // was off. Waiting in short steps and reading the wall clock each time makes the end follow the clock;
            // the awake time adding up to the original wait is the other way out (a clock that does not move).
            var waited = 0L
            while (waited < wait && clock.millis() < endsAt.toEpochMilli()) {
                val step = minOf(POLL_MS, wait - waited)
                delay(step)
                waited += step
            }
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
        // The alarm may have fired first (screen off): then it already buzzed and beeped for this rest.
        val first = RestSignal.claim(endsAt.toEpochMilli())
        if (first) RestSignal.buzz(context)
        scope.launch {
            val beep = try {
                settings?.restTimer?.first()?.beep == true
            } catch (e: Exception) {
                false
            }
            if (beep && first) RestBeep.play()
        }
    }

    private fun alarmIntent(endsAtMillis: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST,
        alarmBroadcast().putExtra(RestAlarmReceiver.EXTRA_ENDS_AT, endsAtMillis),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarmBroadcast(): Intent =
        Intent(context, RestAlarmReceiver::class.java).setAction(RestAlarmReceiver.ACTION_REST_OVER)

    /**
     * An alarm-clock alarm: exact, it wakes the phone even in Doze and needs no special permission. An inexact
     * alarm (setAndAllowWhileIdle) can be held back for minutes with the screen off, which made the rest end late.
     */
    private fun setAlarm(endsAt: Instant) {
        try {
            val alarms = context.getSystemService(AlarmManager::class.java) ?: return
            val info = AlarmManager.AlarmClockInfo(endsAt.toEpochMilli(), Notifications.openAppIntent(context))
            alarms.setAlarmClock(info, alarmIntent(endsAt.toEpochMilli()))
        } catch (e: RuntimeException) {
            // No alarm service or not allowed: the in-process timer still runs.
        }
    }

    private fun cancelAlarm() {
        try {
            // NO_CREATE: look the scheduled alarm up without rewriting its extras (UPDATE_CURRENT with endsAt 0
            // changed the intent of an alarm that was already being delivered, so the receiver saw a wrong end).
            val existing = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST,
                alarmBroadcast(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: return
            context.getSystemService(AlarmManager::class.java)?.cancel(existing)
            existing.cancel()
        } catch (e: RuntimeException) {
            // Nothing to cancel.
        }
    }

    private companion object {
        const val ALARM_REQUEST = 1
        const val POLL_MS = 1_000L
    }
}
