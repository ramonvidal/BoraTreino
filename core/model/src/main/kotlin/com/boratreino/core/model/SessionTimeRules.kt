package com.boratreino.core.model

import java.time.Duration
import java.time.Instant

/**
 * Session times that account for cardio. A cardio set is logged when the cardio is finished, with its
 * minutes typed in, so `Session.startedAt` (the first set logged) can be a minute before `endedAt` for a
 * 60 min run. Read-time only: nothing is stored differently.
 */
object SessionTimeRules {
    /** Longest duration reported, in minutes: cardio sessions can be long. */
    const val MAX_DURATION_MINUTES = 600

    /**
     * When the session really began: the earliest of `session.startedAt` and, for each cardio set,
     * `loggedAt - seconds` (a cardio set of N seconds logged at T began at T - N). Cardio sets with
     * null seconds count as 0.
     */
    fun effectiveStart(session: Session, sets: List<WorkoutSet>): Instant {
        var start = session.startedAt
        for (set in sets) {
            if (!CardioRules.isCardio(set)) continue
            val began = set.loggedAt.minusSeconds((set.seconds ?: 0).toLong())
            if (began < start) start = began
        }
        return start
    }

    /**
     * Minutes from [effectiveStart] to `endedAt`, rounded to the nearest minute ((seconds + 30) / 60),
     * coerced to 1..[MAX_DURATION_MINUTES]. Null when the session is open or `endedAt` is not after the
     * effective start.
     */
    fun durationMinutes(session: Session, sets: List<WorkoutSet>): Int? {
        val end = session.endedAt ?: return null
        val start = effectiveStart(session, sets)
        if (end <= start) return null
        val seconds = Duration.between(start, end).seconds
        return ((seconds + 30L) / 60L).toInt().coerceIn(1, MAX_DURATION_MINUTES)
    }

    /** Total cardio time in [sets], in minutes: the sum of cardio seconds / 60, rounded. Null seconds count as 0. */
    fun cardioMinutes(sets: List<WorkoutSet>): Int {
        val seconds = sets.filter { CardioRules.isCardio(it) }.sumOf { (it.seconds ?: 0).toLong() }
        return ((seconds + 30L) / 60L).toInt()
    }
}
