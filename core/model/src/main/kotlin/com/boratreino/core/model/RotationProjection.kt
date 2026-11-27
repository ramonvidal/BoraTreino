package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate

/** A routine the rotation (or a fixed day) puts on [date]. A date may have several, in the order of the day. */
data class ProjectedWorkout(val date: LocalDate, val routine: Routine)

/** Projects which routines the rotation and the fixed days put on each of the coming days. */
object RotationProjection {
    const val DAYS_AHEAD = 7

    /**
     * The routines the rotation and the fixed days put on [today] .. [today] + [daysAhead], by date and, within a
     * date, in [NextRoutineRules.dayRoutines] order (the rotation routine first, then the fixed ones). The rotation
     * pointer starts at [lastRoutineId] and is simulated day by day; it advances with the date's rotation routine.
     * A date's manually planned routines ([planned]) get no entry (the caller shows them as plans), but they do not
     * hide the others: the rotation routine and the fixed ones still get theirs, except that a planned rotation
     * routine fills that date's rotation slot and advances the pointer as if trained.
     * On [today], the routines in [doneToday] (trained today) get no entry, and a rotation routine among them fills
     * the rotation slot; the pointer moves from [lastRoutineId] as given (the caller already counts today's sessions
     * in it). When [trainedToday] and [doneToday] is empty (only free workouts, or a caller that does not pass the
     * ids), [today] gets no entry at all.
     */
    fun project(
        today: LocalDate,
        routines: List<Routine>,
        lastRoutineId: Long?,
        trainingDays: Set<DayOfWeek>,
        planned: List<PlannedWorkout>,
        trainedToday: Boolean,
        daysAhead: Int = DAYS_AHEAD,
        doneToday: Set<Long> = emptySet(),
    ): List<ProjectedWorkout> {
        if (routines.isEmpty()) return emptyList()
        val plannedByDate = NextRoutineRules.plannedByDate(routines, planned)
        var last = lastRoutineId
        val result = mutableListOf<ProjectedWorkout>()
        for (offset in 0L..daysAhead.toLong()) {
            val date = today.plusDays(offset)
            val plans = plannedByDate[date].orEmpty()
            plans.lastOrNull { it.weekdays.isEmpty() }?.let { last = it.id }
            if (offset == 0L && trainedToday && doneToday.isEmpty()) continue
            val done = if (offset == 0L) doneToday else emptySet()
            val day = NextRoutineRules.dayRoutines(ScheduleMode.IN_TURN, routines, last, date.dayOfWeek, trainingDays, plans, done)
            val planIds = plans.map { it.id }.toSet()
            day.filter { it.id !in planIds }.forEach { routine ->
                result += ProjectedWorkout(date, routine)
                if (routine.weekdays.isEmpty()) last = routine.id
            }
        }
        return result
    }
}
