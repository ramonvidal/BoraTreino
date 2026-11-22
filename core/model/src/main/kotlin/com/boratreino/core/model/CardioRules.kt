package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Minutes, calories and distance the cardio steppers hold before a set is logged; 0 = not logged.
 * [intensity] is the console's whole number (null = not logged).
 */
data class CardioDraft(val minutes: Int, val calories: Int, val distanceM: Int, val intensity: Int? = null)

/**
 * Cardio totals for one 7-day block of a stats period. [minutes] = sum of cardio seconds / 60, rounded;
 * [distanceM] = sum; [sessions] = distinct sessions with cardio; [intensityAvg] = time-weighted average over
 * the sets that have an intensity and seconds > 0 (null when none); [speedKmh] = total distance / total time
 * over the sets that have both (null when none).
 */
data class CardioBlock(
    val weekStart: LocalDate,
    val minutes: Int,
    val distanceM: Int,
    val sessions: Int,
    val intensityAvg: Double?,
    val speedKmh: Double?,
)

/** Cardio totals for the week starting on [weekStart]; [sessions] counts distinct days with cardio. */
data class CardioWeek(
    val weekStart: LocalDate,
    val minutes: Int,
    val calories: Int,
    val distanceM: Int,
    val sessions: Int,
)

/** Rules for logging cardio: what the steppers start at, how they move, and weekly totals. */
object CardioRules {
    const val DEFAULT_MINUTES = 20
    const val MAX_MINUTES = 600
    const val MAX_CALORIES = 5000
    const val MAX_INTENSITY = 999
    const val MAX_DISTANCE_M = 200_000
    const val CALORIE_STEP = 10
    const val DISTANCE_STEP_M = 100

    /** Steppers' starting values: this session's last cardio set; else last time's first set; else 20 min. */
    fun prefill(lastTime: List<WorkoutSet>, thisSession: List<WorkoutSet>): CardioDraft {
        thisSession.lastOrNull()?.let { return toDraft(it) }
        lastTime.firstOrNull()?.let { return toDraft(it) }
        return CardioDraft(DEFAULT_MINUTES, 0, 0, null)
    }

    /** Last time's set at index thisSession.size, as a draft; null when there is none. */
    fun sameAgain(lastTime: List<WorkoutSet>, thisSession: List<WorkoutSet>): CardioDraft? =
        lastTime.getOrNull(thisSession.size)?.let { toDraft(it) }

    /** +/-1, clamped to 0..MAX_MINUTES. */
    fun stepMinutes(minutes: Int, up: Boolean): Int =
        (if (up) minutes + 1 else minutes - 1).coerceIn(0, MAX_MINUTES)

    /** +/-10; an off-grid value first snaps to the next multiple of 10 in that direction. Clamped to 0..MAX_CALORIES. */
    fun stepCalories(calories: Int, up: Boolean): Int = stepOnGrid(calories, CALORIE_STEP, up, MAX_CALORIES)

    /** +/-100 m; an off-grid value first snaps to the next multiple of 100 in that direction. Clamped to 0..MAX_DISTANCE_M. */
    fun stepDistance(distanceM: Int, up: Boolean): Int = stepOnGrid(distanceM, DISTANCE_STEP_M, up, MAX_DISTANCE_M)

    /** +/-1, clamped to 0..MAX_INTENSITY; null + up -> 1; null + down -> null (still not logged). */
    fun stepIntensity(intensity: Int?, up: Boolean): Int? = when {
        intensity == null -> if (up) 1 else null
        else -> (if (up) intensity + 1 else intensity - 1).coerceIn(0, MAX_INTENSITY)
    }

    /** Average speed in km/h = distance / time; null unless both [distanceM] and [seconds] are > 0. */
    fun speedKmh(distanceM: Int?, seconds: Int?): Double? =
        if (distanceM != null && seconds != null && distanceM > 0 && seconds > 0) {
            distanceM.toDouble() / seconds * 3.6
        } else {
            null
        }

    private fun stepOnGrid(value: Int, step: Int, up: Boolean, max: Int): Int {
        val rem = Math.floorMod(value, step)
        val target = when {
            rem == 0 -> if (up) value + step else value - step
            up -> value - rem + step
            else -> value - rem
        }
        return target.coerceIn(0, max)
    }

    /** A cardio set can be logged when it has minutes or calories. */
    fun canLog(draft: CardioDraft): Boolean = draft.minutes > 0 || draft.calories > 0

    /** The set for [draft]: seconds = minutes x 60; zero values become null; no weight or reps. */
    fun toSet(
        id: Long,
        sessionId: Long,
        machineId: Long,
        position: Int,
        draft: CardioDraft,
        loggedAt: Instant,
    ): WorkoutSet = WorkoutSet(
        id = id,
        sessionId = sessionId,
        machineId = machineId,
        position = position,
        weightKg = null,
        reps = null,
        seconds = if (draft.minutes > 0) draft.minutes * 60 else null,
        calories = draft.calories.takeIf { it > 0 },
        distanceM = draft.distanceM.takeIf { it > 0 },
        loggedAt = loggedAt,
        intensity = draft.intensity?.coerceIn(0, MAX_INTENSITY),
    )

    /** Inverse of [toSet]; minutes are seconds / 60, rounded. */
    fun toDraft(set: WorkoutSet): CardioDraft =
        CardioDraft(minutesOf(set.seconds ?: 0), set.calories ?: 0, set.distanceM ?: 0, set.intensity)

    /** True when the set carries seconds, calories, distance or intensity. */
    fun isCardio(set: WorkoutSet): Boolean = set.seconds != null || set.calories != null || set.distanceM != null || set.intensity != null

    /** Whole minutes in [seconds], rounded to the nearest minute. */
    internal fun minutesOf(seconds: Int): Int = ((seconds.toLong() + 30L) / 60L).toInt()

    /**
     * Exactly [count] weeks ending with the week of [today], oldest first, zero-filled. [sets] pairs each set
     * with the date of its session; sets that are not cardio are ignored. [CardioWeek.sessions] = distinct
     * dates with cardio in that week.
     */
    fun weeks(
        sets: List<Pair<LocalDate, WorkoutSet>>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
        count: Int,
    ): List<CardioWeek> {
        if (count <= 0) return emptyList()
        val thisStart = WeekGoalRules.weekStart(today, firstDayOfWeek)
        val byWeek = sets
            .filter { isCardio(it.second) }
            .groupBy { WeekGoalRules.weekStart(it.first, firstDayOfWeek) }
        return (count - 1 downTo 0).map { back ->
            val start = thisStart.minusWeeks(back.toLong())
            val group = byWeek[start].orEmpty()
            CardioWeek(
                weekStart = start,
                minutes = minutesOf(group.sumOf { it.second.seconds ?: 0 }),
                calories = group.sumOf { it.second.calories ?: 0 },
                distanceM = group.sumOf { it.second.distanceM ?: 0 },
                sessions = group.map { it.first }.toSet().size,
            )
        }
    }

    /** Time-weighted average intensity of [sets] (those with an intensity and seconds > 0); null when none. */
    fun intensityAverage(sets: List<WorkoutSet>): Double? {
        val used = sets.filter { it.intensity != null && (it.seconds ?: 0) > 0 }
        val total = used.sumOf { (it.seconds ?: 0).toLong() }
        if (total <= 0L) return null
        return used.sumOf { (it.intensity ?: 0).toLong() * (it.seconds ?: 0) }.toDouble() / total
    }

    /** Total distance / total time over the sets that have both; null when none. */
    fun averageSpeedKmh(sets: List<WorkoutSet>): Double? {
        val used = sets.filter { (it.distanceM ?: 0) > 0 && (it.seconds ?: 0) > 0 }
        return speedKmh(used.sumOf { it.distanceM ?: 0 }.takeIf { used.isNotEmpty() }, used.sumOf { it.seconds ?: 0 })
    }

    /**
     * One [CardioBlock] per 7-day block (starting [periodStart]) of the period that has cardio sets, oldest first.
     * [sets] pairs each set with its session's date; non-cardio sets and dates outside the period are ignored.
     */
    fun blocks(sets: List<Pair<LocalDate, WorkoutSet>>, periodStart: LocalDate): List<CardioBlock> =
        sets
            .filter { isCardio(it.second) && !it.first.isBefore(periodStart) }
            .groupBy { java.time.temporal.ChronoUnit.DAYS.between(periodStart, it.first) / 7 }
            .toSortedMap()
            .map { (index, group) ->
                val groupSets = group.map { it.second }
                CardioBlock(
                    weekStart = periodStart.plusDays(index * 7),
                    minutes = minutesOf(groupSets.sumOf { it.seconds ?: 0 }),
                    distanceM = groupSets.sumOf { it.distanceM ?: 0 },
                    sessions = groupSets.map { it.sessionId }.toSet().size,
                    intensityAvg = intensityAverage(groupSets),
                    speedKmh = averageSpeedKmh(groupSets),
                )
            }

    /** The current week's totals; same inputs as [weeks]. */
    fun thisWeek(
        sets: List<Pair<LocalDate, WorkoutSet>>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): CardioWeek = weeks(sets, today, firstDayOfWeek, 1).single()
}
