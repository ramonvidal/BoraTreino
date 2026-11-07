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
     * IN_TURN: the routine after the one of the most recent session that had a routine (by position, wrapping);
     * the first routine when there is none or it no longer exists. WEEKDAYS: the first routine (by position)
     * whose weekdays contain today's day; null on a rest day. Null when there are no routines.
     * IN_TURN with non-empty [trainingDays] that do not contain [today] is a rest day (null).
     * A non-null [planned] routine (planned for today's date) beats all of this, even on a rest day.
     */
    fun next(
        mode: ScheduleMode,
        routines: List<Routine>,
        lastRoutineId: Long?,
        today: DayOfWeek,
        trainingDays: Set<DayOfWeek> = emptySet(),
        planned: Routine? = null,
    ): Routine? {
        if (planned != null) return planned
        if (routines.isEmpty()) return null
        val sorted = routines.sortedBy { it.position }
        return when (mode) {
            ScheduleMode.IN_TURN -> {
                if (trainingDays.isNotEmpty() && today !in trainingDays) return null
                val i = sorted.indexOfFirst { it.id == lastRoutineId }
                if (lastRoutineId == null || i < 0) sorted.first() else sorted[(i + 1) % sorted.size]
            }
            ScheduleMode.WEEKDAYS -> sorted.firstOrNull { today in it.weekdays }
        }
    }

    /**
     * The first date strictly after [after] (looking at most 7 days ahead) that is a training day, with the
     * routine suggested then. Null when there is none or no routines. A routine in [planned] for a date
     * (when it exists in [routines]) wins over the schedule on that date.
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
        val plannedByDate = planned.associate { it.date to byId[it.routineId] }
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
