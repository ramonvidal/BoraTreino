package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Minutes, calories and distance the cardio steppers hold before a set is logged; 0 = not logged.
 * [intensity] is the console's intensity with up to 2 decimals (e.g. 0.79); null = not logged.
 */
data class CardioDraft(
    val minutes: Int,
    val calories: Int,
    val distanceM: Int,
    val intensity: Double? = null,
)

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
    const val MAX_INTENSITY = 100.0
    const val MAX_DISTANCE_M = 200_000
    const val CALORIE_STEP = 10
    const val DISTANCE_STEP_M = 100

    /** First intensity the stepper shows when it is null and "up" is pressed. */
    const val START_INTENSITY = 0.70

    /** Steppers' starting values: this session's last cardio set; else last time's first set; else 20 min. */
    fun prefill(lastTime: List<WorkoutSet>, thisSession: List<WorkoutSet>): CardioDraft {
        thisSession.lastOrNull()?.let { return toDraft(it) }
        lastTime.firstOrNull()?.let { return toDraft(it) }
        return CardioDraft(DEFAULT_MINUTES, 0, 0)
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

    /**
     * Intensity: +/-0.01 (two decimals, worked on whole hundredths so there is no float drift), clamped to
     * 0.0..MAX_INTENSITY; null + up -> [START_INTENSITY]; null + down -> null (still not logged).
     */
    fun stepIntensity(intensity: Double?, up: Boolean): Double? {
        if (intensity == null) return if (up) START_INTENSITY else null
        val units = Math.round(intensity * 100) + if (up) 1 else -1
        return (units.toDouble() / 100).coerceIn(0.0, MAX_INTENSITY)
    }

    /** [value] rounded half-up to 2 decimals and clamped to 0.0..MAX_INTENSITY. */
    fun roundIntensity(value: Double): Double =
        java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
            .coerceIn(0.0, MAX_INTENSITY)

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

    /** True for the fields typed with a decimal separator: distance (km) and intensity. */
    fun allowsDecimal(field: CardioField): Boolean = when (field) {
        CardioField.DISTANCE_KM, CardioField.INTENSITY -> true
        else -> false
    }

    /**
     * The number pad's starting text for [field]: "" when the value is not set or zero; distance as plain
     * kilometres without trailing zeros ("17.3"), intensity likewise ("0.79", "12", "0.7").
     */
    fun fieldText(draft: CardioDraft, field: CardioField): String = when (field) {
        CardioField.MINUTES -> intText(draft.minutes)
        CardioField.CALORIES -> intText(draft.calories)
        CardioField.INTENSITY -> decimalText(draft.intensity, 2)
        CardioField.DISTANCE_KM ->
            if (draft.distanceM <= 0) "" else java.math.BigDecimal(draft.distanceM).movePointLeft(3).plainText()
    }

    /**
     * [draft] with [field] set from the pad's [text] (comma or point decimals, surrounding spaces ignored).
     * Blank text clears the field (minutes, calories and distance go to 0, intensity to null; "0" keeps an
     * intensity of 0.0); text that is not a plain non-negative number returns null (the caller ignores it).
     * Values are rounded half-up to the field's precision (intensity: 2 decimals) and clamped to the same
     * ranges [toSet] uses. Never throws.
     */
    fun withField(draft: CardioDraft, field: CardioField, text: String): CardioDraft? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return clearField(draft, field)
        val value = parseNumber(trimmed) ?: return null
        return when (field) {
            CardioField.MINUTES -> draft.copy(minutes = value.toClampedInt(0, MAX_MINUTES))
            CardioField.CALORIES -> draft.copy(calories = value.toClampedInt(0, MAX_CALORIES))
            CardioField.DISTANCE_KM ->
                draft.copy(distanceM = value.movePointRight(3).toClampedInt(0, MAX_DISTANCE_M))
            CardioField.INTENSITY -> draft.copy(intensity = value.toClampedDecimal(2, MAX_INTENSITY))
        }
    }

    private fun clearField(draft: CardioDraft, field: CardioField): CardioDraft = when (field) {
        CardioField.MINUTES -> draft.copy(minutes = 0)
        CardioField.CALORIES -> draft.copy(calories = 0)
        CardioField.DISTANCE_KM -> draft.copy(distanceM = 0)
        CardioField.INTENSITY -> draft.copy(intensity = null)
    }

    private val NUMBER = Regex("""\d+(?:[.,]\d*)?|[.,]\d+""")

    /** Digits with at most one comma or point; no sign, exponent or letters. Null otherwise. */
    private fun parseNumber(text: String): java.math.BigDecimal? {
        if (!NUMBER.matches(text)) return null
        return java.math.BigDecimal(text.replace(',', '.').let { if (it.startsWith(".")) "0$it" else it.trimEnd('.') })
    }

    private fun java.math.BigDecimal.toClampedInt(min: Int, max: Int): Int {
        val rounded = setScale(0, java.math.RoundingMode.HALF_UP)
        return if (rounded > java.math.BigDecimal(max)) max else rounded.toInt().coerceAtLeast(min)
    }

    private fun java.math.BigDecimal.toClampedDecimal(scale: Int, max: Double): Double {
        val rounded = setScale(scale, java.math.RoundingMode.HALF_UP)
        return if (rounded > java.math.BigDecimal.valueOf(max)) max else rounded.toDouble().coerceAtLeast(0.0)
    }

    private fun intText(value: Int): String = if (value <= 0) "" else value.toString()

    private fun decimalText(value: Double?, scale: Int): String {
        if (value == null || value <= 0.0) return ""
        return java.math.BigDecimal.valueOf(value).setScale(scale, java.math.RoundingMode.HALF_UP).plainText()
    }

    private fun java.math.BigDecimal.plainText(): String =
        if (signum() == 0) "" else stripTrailingZeros().toPlainString()

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
        intensity = draft.intensity?.let { roundIntensity(it) },
    )

    /** Inverse of [toSet]; minutes are seconds / 60, rounded. */
    fun toDraft(set: WorkoutSet): CardioDraft =
        CardioDraft(minutesOf(set.seconds ?: 0), set.calories ?: 0, set.distanceM ?: 0, set.intensity)

    /**
     * A cardio set: no weight and no reps, and seconds, calories, distance or intensity. Every strength set has a
     * weight (0.0 = no load), so timed and distance strength sets are not cardio.
     */
    fun isCardio(set: WorkoutSet): Boolean =
        set.weightKg == null && set.reps == null &&
            (set.seconds != null || set.calories != null || set.distanceM != null || set.intensity != null)

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
    fun intensityAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.intensity }

    private fun timeWeighted(sets: List<WorkoutSet>, value: (WorkoutSet) -> Double?): Double? {
        val used = sets.filter { value(it) != null && (it.seconds ?: 0) > 0 }
        val total = used.sumOf { (it.seconds ?: 0).toLong() }
        if (total <= 0L) return null
        return used.sumOf { (value(it) ?: 0.0) * (it.seconds ?: 0) } / total
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
