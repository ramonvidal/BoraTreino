package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** A routine with its machines, items by position. */
data class RoutineWithItems(val routine: Routine, val items: List<RoutineItem>)

/** The plan with its routines, by position. */
data class RoutinePlanWithRoutines(val plan: RoutinePlan, val routines: List<RoutineWithItems>)

/** Which routine the owner picked for the session. */
sealed interface RoutineChoice {
    /** Follow [NextRoutineRules]. */
    data object Suggested : RoutineChoice

    /** No routine. */
    data object Free : RoutineChoice

    data class Chosen(val routineId: Long) : RoutineChoice
}

/** What Today shows about routines. */
data class TodayPlan(
    val mode: ScheduleMode,
    /** By position; empty means no routines. */
    val routines: List<Routine>,
    /** [NextRoutineRules.next]. */
    val suggested: Routine?,
    val choice: RoutineChoice,
    /** The open session's routine if it has one; else from [choice] (Suggested -> suggested). */
    val active: RoutineWithItems?,
    /** Nothing suggested today (either mode) and there are routines. */
    val restDay: Boolean,
    /** Machines hidden from the active routine for this training only. */
    val skipped: Set<Long> = emptySet(),
    /** The next training after today; set only when [restDay]. */
    val upcoming: UpcomingTraining? = null,
    /** True when [suggested] comes from a workout planned for today. */
    val plannedToday: Boolean = false,
)

/** The next training day (strictly after a date) and the routine suggested then. */
data class UpcomingTraining(val date: LocalDate, val routine: Routine)

object NextRoutineRules {
    /**
     * Each routine is either in rotation (empty weekdays) or on specific days (non-empty weekdays); [mode] is
     * ignored (kept for source compatibility). Order:
     * 1. The first routine of [planned] (planned for today's date, in the order the owner set, given by the caller)
     *    that is not in [doneToday] (ids of routines already trained today) wins, even on a rest day.
     * 2. No routines: null.
     * 3. The first on-specific-days routine (by position) whose weekdays contain [today] wins over rotation.
     * 4. No rotation routines: null.
     * 5. Rotation is offered only when [today] is in [trainingDays], or when [trainingDays] is empty and no routine
     *    is on specific days (train any day). Otherwise null (rest day).
     * 6. The pick is the rotation routine after [lastRoutineId] (the last rotation routine trained) by position,
     *    wrapping; the first rotation routine when it is null or no longer in rotation.
     */
    fun next(
        mode: ScheduleMode,
        routines: List<Routine>,
        lastRoutineId: Long?,
        today: DayOfWeek,
        trainingDays: Set<DayOfWeek> = emptySet(),
        planned: List<Routine> = emptyList(),
        doneToday: Set<Long> = emptySet(),
    ): Routine? {
        planned.firstOrNull { it.id !in doneToday }?.let { return it }
        if (routines.isEmpty()) return null
        val sorted = routines.sortedBy { it.position }
        val fixed = sorted.filter { it.weekdays.isNotEmpty() }
        fixed.firstOrNull { today in it.weekdays }?.let { return it }
        val rotation = sorted.filter { it.weekdays.isEmpty() }
        if (rotation.isEmpty()) return null
        val offered = if (trainingDays.isEmpty()) fixed.isEmpty() else today in trainingDays
        if (!offered) return null
        val i = rotation.indexOfFirst { it.id == lastRoutineId }
        return if (lastRoutineId == null || i < 0) rotation.first() else rotation[(i + 1) % rotation.size]
    }

    /**
     * The routine of the latest finished session (by start, then id) whose routine is in [routines] and in
     * rotation (empty weekdays); null when there is none. This is the `lastRoutineId` for [next].
     */
    fun lastRotationRoutineId(sessions: List<Session>, routines: List<Routine>): Long? {
        val rotation = routines.filter { it.weekdays.isEmpty() }.map { it.id }.toSet()
        return sessions
            .filter { it.endedAt != null && it.routineId != null && it.routineId in rotation }
            .maxWithOrNull(compareBy({ it.startedAt }, { it.id }))
            ?.routineId
    }

    /**
     * The first date strictly after [after] (looking at most 7 days ahead) that is a training day, with the
     * routine suggested then. Null when there is none or no routines. A routine in [planned] for a date
     * (when it exists in [routines]) wins over the schedule on that date; with several on one date, the one with the
     * lowest planned position (then routine position) wins.
     */
    fun upcoming(
        mode: ScheduleMode,
        routines: List<Routine>,
        lastRoutineId: Long?,
        trainingDays: Set<DayOfWeek>,
        after: LocalDate,
        planned: List<PlannedWorkout> = emptyList(),
    ): UpcomingTraining? {
        if (routines.isEmpty()) return null
        val byId = routines.associateBy { it.id }
        val plannedByDate = planned
            .mapNotNull { p -> byId[p.routineId]?.let { r -> Triple(p.date, p.position, r) } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, list) -> list.minWith(compareBy({ it.first }, { it.second.position })).second }
        for (offset in 1L..7L) {
            val date = after.plusDays(offset)
            val routine = plannedByDate[date]
                ?: next(mode, routines, lastRoutineId, date.dayOfWeek, trainingDays)
            if (routine != null) return UpcomingTraining(date, routine)
        }
        return null
    }

    /** The next free single letter A..Z not used as a shortName (case-insensitive); "R" + n if all used. */
    fun nextShortName(routines: List<Routine>): String {
        val used = routines.map { it.shortName.trim().uppercase() }.toSet()
        for (c in 'A'..'Z') if (c.toString() !in used) return c.toString()
        return "R" + (routines.size + 1)
    }
}
