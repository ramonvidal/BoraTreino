package com.boratreino.core.model

import java.time.LocalDate
import java.time.YearMonth

/** What a machine's progress chart measures. [ONE_RM] is for chart values only (see [ProgressRules.oneRmPoints]). */
enum class ProgressMetric { WEIGHT, REPS, MINUTES, ONE_RM }

/** One point per session for one machine; [topSet] is the set that gave [value]. */
data class SessionPoint(
    val sessionId: Long,
    val date: LocalDate,
    val value: Double,
    val topSet: WorkoutSet,
    val setCount: Int,
)

/** A rise from [from] to [to]. */
data class Improvement(val from: Double, val to: Double) {
    val delta: Double get() = to - from
    val percent: Double get() = if (from > 0) delta / from * 100 else 0.0
}

enum class ProgressRange { THREE_MONTHS, ONE_YEAR, ALL }

/** Axis bounds and tick values, ascending; the first tick is [min] and the last is [max]. */
data class ChartScale(val min: Double, val max: Double, val ticks: List<Double>)

/** A machine's whole history as chart points, ascending. */
data class MachineProgress(
    val machine: Machine,
    val metric: ProgressMetric,
    val points: List<SessionPoint>,
    /** The last 12 weeks, oldest first; only for cardio machines. */
    val weeks: List<CardioWeek> = emptyList(),
    /** Estimated 1RM per session (raw, unrounded); empty for cardio and when no set is eligible. */
    val oneRmPoints: List<SessionPoint> = emptyList(),
)

data class ProgressOverview(
    /** Machines with at least one point, last trained first (ties: name). */
    val machines: List<MachineProgress>,
    val trainingsThisMonth: Int,
    val recordsThisMonth: Int,
    /** This week's cardio totals; null when not computed. */
    val cardioThisWeek: CardioWeek? = null,
)

object ProgressRules {
    /** CARDIO machine -> MINUTES; BODYWEIGHT_REPS machine with no set whose weight > 0 -> REPS; else WEIGHT. */
    fun metricFor(machine: Machine, sets: List<WorkoutSet>): ProgressMetric = when {
        machine.kind == MachineKind.CARDIO -> ProgressMetric.MINUTES
        machine.kind == MachineKind.BODYWEIGHT_REPS && sets.working().none { (it.weightKg ?: 0.0) > 0.0 } -> ProgressMetric.REPS
        else -> ProgressMetric.WEIGHT
    }

    /**
     * One point per session that has at least one of this machine's sets, ascending by date then session id.
     * [sessionDates] maps session id -> its local date; sets whose session is missing are ignored.
     * WEIGHT: topSet = heaviest weight (null counts 0), ties -> more reps, then later position. REPS: topSet = most
     * reps, ties -> heavier, then later position. value = that set's weight or reps. MINUTES: value = total minutes
     * of the session's sets; topSet = the longest set, ties -> later position.
     */
    fun points(
        sets: List<WorkoutSet>,
        sessionDates: Map<Long, LocalDate>,
        metric: ProgressMetric,
    ): List<SessionPoint> {
        val order: Comparator<WorkoutSet> = when (metric) {
            ProgressMetric.WEIGHT -> compareBy<WorkoutSet>({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }, { it.position })
            ProgressMetric.REPS -> compareBy<WorkoutSet>({ it.reps ?: 0 }, { it.weightKg ?: 0.0 }, { it.position })
            ProgressMetric.MINUTES -> compareBy<WorkoutSet>({ it.seconds ?: 0 }, { it.position })
            ProgressMetric.ONE_RM -> return oneRmPoints(sets, sessionDates)
        }
        return sets
            .filter { it.isWorking && it.sessionId in sessionDates }
            .groupBy { it.sessionId }
            .map { (sessionId, group) ->
                val top = group.maxWith(order)
                val value = when (metric) {
                    ProgressMetric.WEIGHT -> top.weightKg ?: 0.0
                    ProgressMetric.REPS -> (top.reps ?: 0).toDouble()
                    ProgressMetric.MINUTES -> CardioRules.minutesOf(group.sumOf { it.seconds ?: 0 }).toDouble()
                    ProgressMetric.ONE_RM -> top.weightKg ?: 0.0
                }
                SessionPoint(sessionId, sessionDates.getValue(sessionId), value, top, group.size)
            }
            .sortedWith(compareBy({ it.date }, { it.sessionId }))
    }

    /**
     * One point per session that has an eligible set (see [OneRepMaxRules]), ascending by date then session id.
     * value = the session's best estimate, topSet = the set that gave it, setCount = working sets of this machine
     * in that session. Sets whose session is missing from [sessionDates] are ignored.
     */
    fun oneRmPoints(sets: List<WorkoutSet>, sessionDates: Map<Long, LocalDate>): List<SessionPoint> =
        sets
            .filter { it.isWorking && it.sessionId in sessionDates }
            .groupBy { it.sessionId }
            .mapNotNull { (sessionId, group) ->
                val top = OneRepMaxRules.bestSet(group) ?: return@mapNotNull null
                val value = OneRepMaxRules.estimate(top) ?: return@mapNotNull null
                SessionPoint(sessionId, sessionDates.getValue(sessionId), value, top, group.size)
            }
            .sortedWith(compareBy({ it.date }, { it.sessionId }))

    /** THREE_MONTHS: date >= today minus 3 months (calendar months); ONE_YEAR: minus 1 year; ALL: everything. Order kept. */
    fun inRange(points: List<SessionPoint>, range: ProgressRange, today: LocalDate): List<SessionPoint> {
        val from = when (range) {
            ProgressRange.THREE_MONTHS -> today.minusMonths(3)
            ProgressRange.ONE_YEAR -> today.minusYears(1)
            ProgressRange.ALL -> return points
        }
        return points.filter { !it.date.isBefore(from) }
    }

    /** Highest value; ties -> the most recent. Null when empty. */
    fun best(points: List<SessionPoint>): SessionPoint? =
        points.maxWithOrNull(compareBy({ it.value }, { it.date }, { it.sessionId }))

    /** last.value - first.value of the given points (already range-filtered); null with fewer than 2 points. */
    fun change(points: List<SessionPoint>): Double? =
        if (points.size < 2) null else points.last().value - points.first().value

    /**
     * Best value in the last [days] days (date > today - days) against the best value before that
     * (date <= today - days). Null when either side has no point or the recent best is not higher than the
     * earlier best.
     */
    fun improvement(points: List<SessionPoint>, today: LocalDate, days: Int = 30): Improvement? {
        val cut = today.minusDays(days.toLong())
        val recent = points.filter { it.date.isAfter(cut) }.maxOfOrNull { it.value } ?: return null
        val earlier = points.filter { !it.date.isAfter(cut) }.maxOfOrNull { it.value } ?: return null
        return if (recent > earlier) Improvement(earlier, recent) else null
    }

    /**
     * Records logged in [month]: replay every machine's sets in loggedAt order (then position) and count those
     * where RecordRules.recordFor(set, earlier sets of the same machine) != null whose session date is in [month].
     */
    fun recordsInMonth(sets: List<WorkoutSet>, sessionDates: Map<Long, LocalDate>, month: YearMonth): Int {
        var count = 0
        for (group in sets.groupBy { it.machineId }.values) {
            val earlier = ArrayList<WorkoutSet>()
            for (set in group.sortedWith(compareBy({ it.loggedAt }, { it.position }))) {
                if (!set.isWorking) {
                    earlier.add(set)
                    continue
                }
                val date = sessionDates[set.sessionId]
                if (date != null && YearMonth.from(date) == month && RecordRules.recordFor(set, earlier) != null) {
                    count++
                }
                earlier.add(set)
            }
        }
        return count
    }

    /** Distinct training dates in [month]. */
    fun trainingDaysInMonth(sessionDates: Collection<LocalDate>, month: YearMonth): Int =
        sessionDates.filter { YearMonth.from(it) == month }.toSet().size
}
