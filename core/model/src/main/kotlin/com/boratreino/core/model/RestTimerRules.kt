package com.boratreino.core.model

import java.time.Duration
import java.time.Instant

/** Whether the rest countdown runs after a logged set, and for how long by default. */
data class RestTimerSettings(
    val enabled: Boolean = true,
    val defaultSeconds: Int = RestTimerRules.DEFAULT_SECONDS,
)

object RestTimerRules {
    const val MIN_SECONDS = 15
    const val MAX_SECONDS = 600
    const val DEFAULT_SECONDS = 90
    const val EXTEND_SECONDS = 30
    val OPTIONS: List<Int> = listOf(60, 90, 120, 180)

    /** machine.restSeconds ?: defaultSeconds, clamped to MIN..MAX. */
    fun durationFor(machine: Machine, defaultSeconds: Int): Int =
        (machine.restSeconds ?: defaultSeconds).coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** Whole seconds left, rounded up, never negative. */
    fun remainingSeconds(endsAt: Instant, now: Instant): Int {
        val millis = Duration.between(now, endsAt).toMillis()
        if (millis <= 0) return 0
        return ((millis + 999) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** "1:30", "0:05", "10:00". */
    fun format(seconds: Int): String {
        val s = seconds.coerceAtLeast(0)
        val rest = s % 60
        return "${s / 60}:${if (rest < 10) "0" else ""}$rest"
    }
}
