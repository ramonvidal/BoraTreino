package com.boratreino.core.model

import java.time.DayOfWeek

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
    /** WEEKDAYS mode, nothing suggested, and there are routines. */
    val restDay: Boolean,
)

object NextRoutineRules {
    /**
     * IN_TURN: the routine after the one of the most recent session that had a routine (by position, wrapping);
     * the first routine when there is none or it no longer exists. WEEKDAYS: the first routine (by position)
     * whose weekdays contain today's day; null on a rest day. Null when there are no routines.
     */
    fun next(mode: ScheduleMode, routines: List<Routine>, lastRoutineId: Long?, today: DayOfWeek): Routine? {
        if (routines.isEmpty()) return null
        val sorted = routines.sortedBy { it.position }
        return when (mode) {
            ScheduleMode.IN_TURN -> {
                val i = sorted.indexOfFirst { it.id == lastRoutineId }
                if (lastRoutineId == null || i < 0) sorted.first() else sorted[(i + 1) % sorted.size]
            }
            ScheduleMode.WEEKDAYS -> sorted.firstOrNull { today in it.weekdays }
        }
    }

    /** The next free single letter A..Z not used as a shortName (case-insensitive); "R" + n if all used. */
    fun nextShortName(routines: List<Routine>): String {
        val used = routines.map { it.shortName.trim().uppercase() }.toSet()
        for (c in 'A'..'Z') if (c.toString() !in used) return c.toString()
        return "R" + (routines.size + 1)
    }
}
