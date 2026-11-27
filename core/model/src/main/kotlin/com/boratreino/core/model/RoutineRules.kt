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

    /** [extra]: the workout is flagged as an extra one and does not advance the rotation. */
    data class Chosen(val routineId: Long, val extra: Boolean = false) : RoutineChoice
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
     * The routines of [today]'s workouts, in the order they are offered; a day may hold several and none hides
     * another. Each routine is either in rotation (empty weekdays) or on specific days (non-empty weekdays); [mode]
     * is ignored (kept for source compatibility). In order, without duplicates, leaving out the ids in [doneToday]
     * (routines already trained today):
     * 1. [planned]: the routines planned for today's date, in the order the owner set (given by the caller), even on
     *    a rest day.
     * 2. The rotation slot: the rotation routine after [lastRoutineId] (the last rotation routine trained) by
     *    position, wrapping; the first rotation routine when it is null or no longer in rotation. Offered only when
     *    [today] is in [trainingDays], or when [trainingDays] is empty and no routine is on specific days (train any
     *    day). The slot is already filled, so left out, when a routine of [planned] is in rotation or a rotation
     *    routine is in [doneToday] (the rotation is trained once a day).
     * 3. Every on-specific-days routine whose weekdays contain [today], by position.
     */
    fun dayRoutines(
        mode: ScheduleMode,
        routines: List<Routine>,
        lastRoutineId: Long?,
        today: DayOfWeek,
        trainingDays: Set<DayOfWeek> = emptySet(),
        planned: List<Routine> = emptyList(),
        doneToday: Set<Long> = emptySet(),
    ): List<Routine> {
        val result = LinkedHashMap<Long, Routine>()
        planned.forEach { result.putIfAbsent(it.id, it) }
        val sorted = routines.sortedBy { it.position }
        val fixed = sorted.filter { it.weekdays.isNotEmpty() }
        val rotation = sorted.filter { it.weekdays.isEmpty() }
        val rotationIds = rotation.map { it.id }.toSet()
        val offered = if (trainingDays.isEmpty()) fixed.isEmpty() else today in trainingDays
        val slotFilled = planned.any { it.weekdays.isEmpty() } || doneToday.any { it in rotationIds }
        if (rotation.isNotEmpty() && offered && !slotFilled) {
            val i = rotation.indexOfFirst { it.id == lastRoutineId }
            val pick = if (lastRoutineId == null || i < 0) rotation.first() else rotation[(i + 1) % rotation.size]
            result.putIfAbsent(pick.id, pick)
        }
        fixed.filter { today in it.weekdays }.forEach { result.putIfAbsent(it.id, it) }
        return result.values.filter { it.id !in doneToday }
    }

    /**
     * The routine to train next today: the first of [dayRoutines]. When that list is empty because everything of the
     * day is in [doneToday], the schedule decides again as if nothing were planned or done (so after the day's
     * workouts Today still offers one more, the rotation routine after [lastRoutineId] first, rather than a rest
     * day). Null when the day holds no workout at all (rest day).
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
        val day = dayRoutines(mode, routines, lastRoutineId, today, trainingDays, planned, doneToday)
        if (day.isNotEmpty() || doneToday.isEmpty()) return day.firstOrNull()
        return dayRoutines(mode, routines, lastRoutineId, today, trainingDays).firstOrNull()
    }

    /**
     * The routine of the latest finished session (by start, then id) whose routine is in [routines] and in
     * rotation (empty weekdays) and not an extra workout ([Session.isExtra]); null when there is none. This is the `lastRoutineId` for [next].
     */
    fun lastRotationRoutineId(sessions: List<Session>, routines: List<Routine>): Long? {
        val rotation = routines.filter { it.weekdays.isEmpty() }.map { it.id }.toSet()
        return sessions
            .filter { it.endedAt != null && it.routineId != null && it.routineId in rotation && !it.isExtra }
            .maxWithOrNull(compareBy({ it.startedAt }, { it.id }))
            ?.routineId
    }

    /**
     * The first date strictly after [after] (looking at most 7 days ahead) that holds a workout, with its first
     * routine ([dayRoutines] with that date's [planned] routines, those that exist in [routines], by planned
     * position then routine position). Null when there is none or no routines.
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
        val plannedByDate = plannedByDate(routines, planned)
        for (offset in 1L..7L) {
            val date = after.plusDays(offset)
            val routine = next(mode, routines, lastRoutineId, date.dayOfWeek, trainingDays, plannedByDate[date].orEmpty())
            if (routine != null) return UpcomingTraining(date, routine)
        }
        return null
    }

    /** The routines of [planned] that exist in [routines], grouped by date, by planned position then routine position. */
    internal fun plannedByDate(routines: List<Routine>, planned: List<PlannedWorkout>): Map<LocalDate, List<Routine>> {
        val byId = routines.associateBy { it.id }
        return planned
            .mapNotNull { p -> byId[p.routineId]?.let { r -> Triple(p.date, p.position, r) } }
            .groupBy({ it.first }, { it.second to it.third })
            .mapValues { (_, list) -> list.sortedWith(compareBy({ it.first }, { it.second.position })).map { it.second }.distinct() }
    }

    /** The next free single letter A..Z not used as a shortName (case-insensitive); "R" + n if all used. */
    fun nextShortName(routines: List<Routine>): String {
        val used = routines.map { it.shortName.trim().uppercase() }.toSet()
        for (c in 'A'..'Z') if (c.toString() !in used) return c.toString()
        return "R" + (routines.size + 1)
    }
}
