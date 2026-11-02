package com.boratreino.core.notify

import com.boratreino.core.model.RestTimerRules
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/** [endsAt] is null when no rest is running. */
data class RestTimerState(val endsAt: Instant? = null) {
    val isRunning: Boolean get() = endsAt != null
}

/** The rest countdown between sets: one at a time, shown on the machine screen and as a notification. */
interface RestTimer {
    val state: StateFlow<RestTimerState>

    /** Emits once when a running timer reaches zero (in this process only). Not on skip. */
    val finished: SharedFlow<Unit>

    /** Replaces a running timer. [seconds] is clamped to the model's min..max. */
    fun start(seconds: Int)

    fun extend(seconds: Int = RestTimerRules.EXTEND_SECONDS)

    /** Back to idle: cancels the notification and the alarm, no buzz. */
    fun skip()
}
