package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** A routine the rotation (or a fixed day) puts on [date]. */
data class ProjectedWorkout(val date: LocalDate, val routine: Routine)

/** Projects which routine the rotation puts on each of the coming days. */
object RotationProjection {
    const val DAYS_AHEAD = 7

    /**
     * The routines the rotation and the fixed days put on [today] .. [today] + [daysAhead], by date (at most one
     * entry per date). The rotation pointer starts at [lastRoutineId] and is simulated day by day with
     * [NextRoutineRules.next]; it advances only when the produced routine is a rotation one (empty weekdays).
     * A date with a manual plan in [planned] gets no entry (planned wins), but a planned rotation routine
     * advances the pointer as if trained. [today] gets no entry when [trainedToday]; the pointer still moves
     * from [lastRoutineId] as given (the caller already counts today's session in it).
     */
    fun project(
        today: LocalDate,
        routines: List<Routine>,
        lastRoutineId: Long?,
        trainingDays: Set<DayOfWeek>,
        planned: List<PlannedWorkout>,
        trainedToday: Boolean,
        daysAhead: Int = DAYS_AHEAD,
    ): List<ProjectedWorkout> {
        if (routines.isEmpty()) return emptyList()
        val byId = routines.associateBy { it.id }
        val plannedByDate = planned
            .mapNotNull { p -> byId[p.routineId]?.let { r -> Triple(p.date, p.position, r) } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.first }, { it.second.position })).map { it.second } }
        var last = lastRoutineId
        val result = mutableListOf<ProjectedWorkout>()
        for (offset in 0L..daysAhead.toLong()) {
            val date = today.plusDays(offset)
            val plans = plannedByDate[date]
            if (plans != null) {
                plans.lastOrNull { it.weekdays.isEmpty() }?.let { last = it.id }
                continue
            }
            if (offset == 0L && trainedToday) continue
            val routine = NextRoutineRules.next(ScheduleMode.IN_TURN, routines, last, date.dayOfWeek, trainingDays)
                ?: continue
            result += ProjectedWorkout(date, routine)
            if (routine.weekdays.isEmpty()) last = routine.id
        }
        return result
    }
}
