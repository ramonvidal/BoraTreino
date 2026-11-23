package com.boratreino.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Minutes, calories and distance the cardio steppers hold before a set is logged; 0 = not logged.
 * [intensity] is the console's whole number; [watts], [heartRate] (averages), [wattsPerKg], [tss] and
 * [intensityFactor] are the bike console's summary values, as shown (null = not logged for all of them).
 */
data class CardioDraft(
    val minutes: Int,
    val calories: Int,
    val distanceM: Int,
    val intensity: Int? = null,
    val watts: Int? = null,
    val heartRate: Int? = null,
    val wattsPerKg: Double? = null,
    val tss: Int? = null,
    val intensityFactor: Double? = null,
)

/**
 * Cardio totals for one 7-day block of a stats period. [minutes] = sum of cardio seconds / 60, rounded;
 * [distanceM] = sum; [sessions] = distinct sessions with cardio; [intensityAvg] = time-weighted average over
 * the sets that have an intensity and seconds > 0 (null when none); [speedKmh] = total distance / total time
 * over the sets that have both (null when none). [powerAvg], [heartRateAvg] and [intensityFactorAvg] are
 * time-weighted averages over the sets that have the value and seconds > 0 (null when none); [tssTotal] is the
 * sum of the sets' TSS (null when no set has one).
 */
data class CardioBlock(
    val weekStart: LocalDate,
    val minutes: Int,
    val distanceM: Int,
    val sessions: Int,
    val intensityAvg: Double?,
    val speedKmh: Double?,
    val powerAvg: Double? = null,
    val heartRateAvg: Double? = null,
    val tssTotal: Int? = null,
    val intensityFactorAvg: Double? = null,
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
    const val MAX_WATTS = 2000
    const val MIN_HEART_RATE = 30
    const val MAX_HEART_RATE = 250
    const val MAX_WATTS_PER_KG = 10.0
    const val MAX_TSS = 999
    const val MAX_INTENSITY_FACTOR = 2.0

    /** First value a stepper shows when its value is null and "up" is pressed. */
    const val START_WATTS = 100
    const val START_HEART_RATE = 120
    const val START_WATTS_PER_KG = 1.0
    const val START_TSS = 30
    const val START_INTENSITY_FACTOR = 0.70
    const val MAX_DISTANCE_M = 200_000
    const val CALORIE_STEP = 10
    const val DISTANCE_STEP_M = 100

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

    /** +/-1, clamped to 0..MAX_INTENSITY; null + up -> 1; null + down -> null (still not logged). */
    fun stepIntensity(intensity: Int?, up: Boolean): Int? = when {
        intensity == null -> if (up) 1 else null
        else -> (if (up) intensity + 1 else intensity - 1).coerceIn(0, MAX_INTENSITY)
    }

    /** Watts: +/-5, clamped to 0..MAX_WATTS; null + up -> [START_WATTS]; null + down -> null (still not logged). */
    fun stepWatts(watts: Int?, up: Boolean): Int? = stepOptional(watts, up, START_WATTS, 5, 0, MAX_WATTS)

    /** Heart rate: +/-1, clamped to MIN_HEART_RATE..MAX_HEART_RATE; null + up -> [START_HEART_RATE]; null + down -> null. */
    fun stepHeartRate(heartRate: Int?, up: Boolean): Int? =
        stepOptional(heartRate, up, START_HEART_RATE, 1, MIN_HEART_RATE, MAX_HEART_RATE)

    /** TSS: +/-1, clamped to 0..MAX_TSS; null + up -> [START_TSS]; null + down -> null. */
    fun stepTss(tss: Int?, up: Boolean): Int? = stepOptional(tss, up, START_TSS, 1, 0, MAX_TSS)

    /** Watts per kg: +/-0.1 (one decimal), clamped to 0.0..MAX_WATTS_PER_KG; null + up -> [START_WATTS_PER_KG]; null + down -> null. */
    fun stepWattsPerKg(wattsPerKg: Double?, up: Boolean): Double? =
        stepOptionalDecimal(wattsPerKg, up, START_WATTS_PER_KG, 10, MAX_WATTS_PER_KG)

    /** Intensity factor: +/-0.01 (two decimals), clamped to 0.0..MAX_INTENSITY_FACTOR; null + up -> [START_INTENSITY_FACTOR]; null + down -> null. */
    fun stepIntensityFactor(intensityFactor: Double?, up: Boolean): Double? =
        stepOptionalDecimal(intensityFactor, up, START_INTENSITY_FACTOR, 100, MAX_INTENSITY_FACTOR)

    private fun stepOptional(value: Int?, up: Boolean, start: Int, step: Int, min: Int, max: Int): Int? = when {
        value == null -> if (up) start else null
        else -> (if (up) value + step else value - step).coerceIn(min, max)
    }

    /** [scale] = 10 for one decimal, 100 for two: works on whole steps so 0.7 + 0.01 is 0.71, not 0.7100000001. */
    private fun stepOptionalDecimal(value: Double?, up: Boolean, start: Double, scale: Int, max: Double): Double? {
        if (value == null) return if (up) start else null
        val units = Math.round(value * scale) + if (up) 1 else -1
        return (units.toDouble() / scale).coerceIn(0.0, max)
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

    /**
     * [draft] with every non-null value of [reading] filled in (clamped to the steppers' ranges); values the
     * reading doesn't have are kept.
     */
    fun applyReading(draft: CardioDraft, reading: BikeReading): CardioDraft = draft.copy(
        minutes = reading.minutes?.coerceIn(0, MAX_MINUTES) ?: draft.minutes,
        calories = reading.calories?.coerceIn(0, MAX_CALORIES) ?: draft.calories,
        distanceM = reading.distanceM?.coerceIn(0, MAX_DISTANCE_M) ?: draft.distanceM,
        watts = reading.watts?.coerceIn(0, MAX_WATTS) ?: draft.watts,
        heartRate = reading.heartRate?.coerceIn(MIN_HEART_RATE, MAX_HEART_RATE) ?: draft.heartRate,
        wattsPerKg = reading.wattsPerKg?.let { (Math.round(it * 10) / 10.0).coerceIn(0.0, MAX_WATTS_PER_KG) }
            ?: draft.wattsPerKg,
        tss = reading.tss?.coerceIn(0, MAX_TSS) ?: draft.tss,
        intensityFactor = reading.intensityFactor
            ?.let { (Math.round(it * 100) / 100.0).coerceIn(0.0, MAX_INTENSITY_FACTOR) } ?: draft.intensityFactor,
    )

    /** True for the fields typed with a decimal separator: distance (km), watts per kg and intensity factor. */
    fun allowsDecimal(field: CardioField): Boolean = when (field) {
        CardioField.DISTANCE_KM, CardioField.WATTS_PER_KG, CardioField.INTENSITY_FACTOR -> true
        else -> false
    }

    /**
     * The number pad's starting text for [field]: "" when the value is not set or zero; distance as plain
     * kilometres without trailing zeros ("17.3"), watts per kg and IF likewise ("1.5", "0.7").
     */
    fun fieldText(draft: CardioDraft, field: CardioField): String = when (field) {
        CardioField.MINUTES -> intText(draft.minutes)
        CardioField.CALORIES -> intText(draft.calories)
        CardioField.INTENSITY -> intText(draft.intensity ?: 0)
        CardioField.WATTS -> intText(draft.watts ?: 0)
        CardioField.HEART_RATE -> intText(draft.heartRate ?: 0)
        CardioField.TSS -> intText(draft.tss ?: 0)
        CardioField.DISTANCE_KM ->
            if (draft.distanceM <= 0) "" else java.math.BigDecimal(draft.distanceM).movePointLeft(3).plainText()
        CardioField.WATTS_PER_KG -> decimalText(draft.wattsPerKg, 1)
        CardioField.INTENSITY_FACTOR -> decimalText(draft.intensityFactor, 2)
    }

    /**
     * [draft] with [field] set from the pad's [text] (comma or point decimals, surrounding spaces ignored).
     * Blank text clears the field (minutes, calories and distance go to 0, the optional ones to null); text that
     * is not a plain non-negative number returns null (the caller ignores it). Values are rounded to the field's
     * precision and clamped to the same ranges [toSet] uses. Never throws.
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
            CardioField.INTENSITY -> draft.copy(intensity = value.toClampedInt(0, MAX_INTENSITY))
            CardioField.WATTS -> draft.copy(watts = value.toClampedInt(0, MAX_WATTS))
            CardioField.HEART_RATE -> draft.copy(heartRate = value.toClampedInt(MIN_HEART_RATE, MAX_HEART_RATE))
            CardioField.TSS -> draft.copy(tss = value.toClampedInt(0, MAX_TSS))
            CardioField.WATTS_PER_KG ->
                draft.copy(wattsPerKg = value.toClampedDecimal(1, MAX_WATTS_PER_KG))
            CardioField.INTENSITY_FACTOR ->
                draft.copy(intensityFactor = value.toClampedDecimal(2, MAX_INTENSITY_FACTOR))
        }
    }

    private fun clearField(draft: CardioDraft, field: CardioField): CardioDraft = when (field) {
        CardioField.MINUTES -> draft.copy(minutes = 0)
        CardioField.CALORIES -> draft.copy(calories = 0)
        CardioField.DISTANCE_KM -> draft.copy(distanceM = 0)
        CardioField.INTENSITY -> draft.copy(intensity = null)
        CardioField.WATTS -> draft.copy(watts = null)
        CardioField.HEART_RATE -> draft.copy(heartRate = null)
        CardioField.TSS -> draft.copy(tss = null)
        CardioField.WATTS_PER_KG -> draft.copy(wattsPerKg = null)
        CardioField.INTENSITY_FACTOR -> draft.copy(intensityFactor = null)
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

    private fun java.math.BigDecimal.toClampedDecimal(scale: Int, max: Double): Double =
        setScale(scale, java.math.RoundingMode.HALF_UP).toDouble().coerceIn(0.0, max)

    private fun intText(value: Int): String = if (value <= 0) "" else value.toString()

    private fun decimalText(value: Double?, scale: Int): String {
        if (value == null || value <= 0.0) return ""
        return java.math.BigDecimal(value).setScale(scale, java.math.RoundingMode.HALF_UP).plainText()
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
        intensity = draft.intensity?.coerceIn(0, MAX_INTENSITY),
        watts = draft.watts?.coerceIn(0, MAX_WATTS),
        heartRate = draft.heartRate?.coerceIn(MIN_HEART_RATE, MAX_HEART_RATE),
        wattsPerKg = draft.wattsPerKg?.let { (Math.round(it * 10) / 10.0).coerceIn(0.0, MAX_WATTS_PER_KG) },
        tss = draft.tss?.coerceIn(0, MAX_TSS),
        intensityFactor = draft.intensityFactor?.let { (Math.round(it * 100) / 100.0).coerceIn(0.0, MAX_INTENSITY_FACTOR) },
    )

    /** Inverse of [toSet]; minutes are seconds / 60, rounded. */
    fun toDraft(set: WorkoutSet): CardioDraft =
        CardioDraft(
            minutesOf(set.seconds ?: 0), set.calories ?: 0, set.distanceM ?: 0, set.intensity,
            set.watts, set.heartRate, set.wattsPerKg, set.tss, set.intensityFactor,
        )

    /** True when the set carries seconds, calories, distance, intensity, watts, heart rate, watts/kg, TSS or IF. */
    fun isCardio(set: WorkoutSet): Boolean =
        set.seconds != null || set.calories != null || set.distanceM != null || set.intensity != null ||
            set.watts != null || set.heartRate != null || set.wattsPerKg != null || set.tss != null ||
            set.intensityFactor != null

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
    fun intensityAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.intensity?.toDouble() }

    /** Time-weighted average watts of [sets] (those with watts and seconds > 0); null when none. */
    fun powerAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.watts?.toDouble() }

    /** Time-weighted average heart rate of [sets] (those with a heart rate and seconds > 0); null when none. */
    fun heartRateAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.heartRate?.toDouble() }

    /** Time-weighted average watts per kg of [sets] (those with the value and seconds > 0); null when none. */
    fun wattsPerKgAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.wattsPerKg }

    /** Time-weighted average intensity factor of [sets] (those with the value and seconds > 0); null when none. */
    fun intensityFactorAverage(sets: List<WorkoutSet>): Double? = timeWeighted(sets) { it.intensityFactor }

    /** Sum of the TSS of [sets]; null when no set has one. */
    fun tssTotal(sets: List<WorkoutSet>): Int? =
        sets.mapNotNull { it.tss }.takeIf { it.isNotEmpty() }?.sum()

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
                    powerAvg = powerAverage(groupSets),
                    heartRateAvg = heartRateAverage(groupSets),
                    tssTotal = tssTotal(groupSets),
                    intensityFactorAvg = intensityFactorAverage(groupSets),
                )
            }

    /** The current week's totals; same inputs as [weeks]. */
    fun thisWeek(
        sets: List<Pair<LocalDate, WorkoutSet>>,
        today: LocalDate,
        firstDayOfWeek: DayOfWeek,
    ): CardioWeek = weeks(sets, today, firstDayOfWeek, 1).single()
}
