package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class StallDirection { FLAT, FALLING }

sealed interface StallHint {
    data class MoreReps(val reps: Int) : StallHint
    data class MoreWeight(val kg: Double) : StallHint
}

sealed interface Finding {
    val isPositive: Boolean

    data class FrequencyDrop(val recentPerWeek: Double, val usualPerWeek: Double, val goal: Int) : Finding {
        override val isPositive = false
    }

    /** [routine]: where to add an exercise; null when there is no plan. */
    data class LowVolume(val group: MuscleGroup, val setsPerWeek: Double, val routine: Routine?) : Finding {
        override val isPositive = false
    }

    data class Stalled(
        val machine: Machine,
        val direction: StallDirection,
        /** Sessions from the best one to the last, inclusive. */
        val sessions: Int,
        /** The last session's best-e1RM set. */
        val lastTop: WorkoutSet,
        val bestEstimate: Double,
        val lastEstimate: Double,
        /** Null for FALLING. */
        val hint: StallHint?,
    ) : Finding {
        override val isPositive = false
    }

    /** [event] null when every record of the window is on a flagged machine. [others] = records in the window minus the named one. */
    data class NewRecord(val event: RecordEvent?, val others: Int) : Finding {
        override val isPositive = true
    }
}

/** "What matters now": picks at most [MAX_FINDINGS] things worth the owner's attention. Pure. */
object InsightRules {
    const val MAX_FINDINGS = 3
    const val LOW_VOLUME_BELOW = 10.0
    const val LOW_VOLUME_MIN_WEEKS = 2
    const val STALL_MIN_SESSIONS = 3
    const val STALL_MIN_HISTORY = 4
    const val STALL_ACTIVE_DAYS = 21
    const val FALLING_RATIO = 0.95
    const val REPS_HINT_STEP = 2
    const val RECORD_DAYS = 7
    const val DROP_RECENT_WEEKS = 2
    const val DROP_USUAL_WEEKS = 4
    const val DROP_MIN_DIFF = 1.0

    /** Problems first (frequency, volume, stall), then the good news; at most [MAX_FINDINGS]. */
    fun findings(
        report: StatsReport,
        trainingDates: Set<LocalDate>,
        weeklyGoal: Int,
        firstDayOfWeek: DayOfWeek,
        routines: List<RoutineWithItems>,
        machineGroups: Map<Long, MuscleGroup>,
    ): List<Finding> = listOfNotNull(
        frequencyDrop(trainingDates, weeklyGoal, report.to, firstDayOfWeek),
        lowVolume(report, routines, machineGroups),
        stalled(report),
        newRecord(report),
    ).take(MAX_FINDINGS)

    fun frequencyDrop(
        trainingDates: Set<LocalDate>,
        weeklyGoal: Int,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): Finding.FrequencyDrop? {
        val earliest = trainingDates.minOrNull() ?: return null
        val goal = weeklyGoal.coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)
        val current = WeekGoalRules.weekStart(today, firstDayOfWeek)
        val oldest = current.minusWeeks((DROP_RECENT_WEEKS + DROP_USUAL_WEEKS).toLong())
        if (earliest.isAfter(oldest)) return null
        fun mean(fromWeek: Int, count: Int): Double =
            (fromWeek until fromWeek + count).sumOf {
                WeekGoalRules.daysTrained(trainingDates, current.minusWeeks(it.toLong()), firstDayOfWeek)
            }.toDouble() / count
        val recent = mean(1, DROP_RECENT_WEEKS)
        val usual = mean(1 + DROP_RECENT_WEEKS, DROP_USUAL_WEEKS)
        if (recent >= goal || usual - recent < DROP_MIN_DIFF) return null
        return Finding.FrequencyDrop(recent, usual, goal)
    }

    fun lowVolume(
        report: StatsReport,
        routines: List<RoutineWithItems>,
        machineGroups: Map<Long, MuscleGroup>,
    ): Finding.LowVolume? {
        val recent = report.recentGroupSets
        if (recent.weeks < LOW_VOLUME_MIN_WEEKS) return null
        val (group, sets) = MuscleGroup.entries
            .filter { it != MuscleGroup.CARDIO }
            .mapNotNull { g -> recent.setsPerWeek[g]?.takeIf { it > 0.0 && it < LOW_VOLUME_BELOW }?.let { g to it } }
            .minWithOrNull(compareBy({ it.second }, { it.first.ordinal })) ?: return null
        val routine = routines
            .minWithOrNull(
                compareBy<RoutineWithItems> { r -> r.items.count { machineGroups[it.machineId] == group } }
                    .thenBy { it.routine.position },
            )?.routine
        return Finding.LowVolume(group, sets, routine)
    }

    fun stalled(report: StatsReport): Finding.Stalled? {
        val today = report.to
        val flagged = report.suspiciousJumps.map { it.machine.id }.toSet()
        val activeFrom = today.minusDays((STALL_ACTIVE_DAYS - 1).toLong())
        val groupSets = report.recentGroupSets.setsPerWeek

        class Candidate(val series: MachineSeries, val sessions: Int, val max: Double)

        val candidates = report.oneRmHistory.mapNotNull { series ->
            val machine = series.machine
            val points = series.points
            if (machine.archived || machine.id in flagged) return@mapNotNull null
            if (points.size < STALL_MIN_HISTORY) return@mapNotNull null
            if (points.last().date.isBefore(activeFrom)) return@mapNotNull null
            val max = points.maxOf { it.value }
            val i = points.indexOfFirst { it.value == max }
            val sessions = points.size - i
            if (sessions < STALL_MIN_SESSIONS) null else Candidate(series, sessions, max)
        }
        val best = candidates.sortedWith(
            compareByDescending<Candidate> { it.sessions }
                .thenBy { groupSets[it.series.machine.muscleGroup] ?: 0.0 }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.series.machine.name },
        ).firstOrNull() ?: return null

        val machine = best.series.machine
        val last = best.series.points.last()
        val falling = last.value <= best.max * FALLING_RATIO
        val top = last.topSet
        val hint = if (falling) {
            null
        } else {
            val reps = top.reps
            val weight = top.weightKg
            when {
                reps != null && reps + REPS_HINT_STEP <= OneRepMaxRules.MAX_REPS -> StallHint.MoreReps(reps + REPS_HINT_STEP)
                weight != null -> StallHint.MoreWeight(weight + machine.stepKg)
                else -> null
            }
        }
        return Finding.Stalled(
            machine = machine,
            direction = if (falling) StallDirection.FALLING else StallDirection.FLAT,
            sessions = best.sessions,
            lastTop = top,
            bestEstimate = best.max,
            lastEstimate = last.value,
            hint = hint,
        )
    }

    fun newRecord(report: StatsReport): Finding.NewRecord? {
        val since = report.to.minusDays((RECORD_DAYS - 1).toLong())
        val window = report.records.filter { !it.date.isBefore(since) }
        if (window.isEmpty()) return null
        val flagged = report.suspiciousJumps.map { it.machine.id }.toSet()
        val usable = window.filter { it.machine.id !in flagged }
        val byName = compareBy<RecordEvent, String>(String.CASE_INSENSITIVE_ORDER) { it.machine.name }
        val heaviest = usable.filter { it.record is Record.HeaviestWeight }
            .sortedWith(
                compareByDescending<RecordEvent> { (it.record as Record.HeaviestWeight).weightKg }
                    .thenByDescending { it.date }
                    .then(byName),
            ).firstOrNull()
        val named = heaviest ?: usable.sortedWith(compareByDescending<RecordEvent> { it.date }.then(byName)).firstOrNull()
        return Finding.NewRecord(named, if (named != null) window.size - 1 else window.size)
    }
}
