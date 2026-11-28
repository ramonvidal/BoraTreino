package com.boratreino.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

data class SessionSets(val session: Session, val sets: List<WorkoutSet>)

/** All values are rounded to 10 kcal. */
data class DayEnergy(val kcal: Int, val restDayKcal: Int, val trainingBurnKcal: Int, val kind: DayKind)

enum class DayKind { TRAINED, TRAINING_EXPECTED, REST }

/** One day of [EnergyHistory]: [kcal] the day's estimate, [burnKcal] its net training part, both rounded to 10. */
data class EnergyPoint(val date: LocalDate, val kcal: Int, val burnKcal: Int, val trained: Boolean)

/** Daily estimates over a range; averages are rounded to 10 and null when there is too little data. */
data class EnergyHistory(
    val points: List<EnergyPoint>,
    val last7AverageKcal: Int?,
    val last28AverageKcal: Int?,
    val averageBurnKcal: Int?,
    val trainedDays: Int,
)

/** Estimate of a day's energy need. Never tracks food; an estimate, not advice. */
object EnergyRules {
    const val ACTIVITY_FACTOR = 1.2
    const val STRENGTH_MET = 4.0
    const val DEFAULT_SESSION_MINUTES = 60
    const val MIN_SESSION_MINUTES = 20
    const val MAX_SESSION_HOURS = 3
    private const val MIN_AGE = 10
    private const val MINUTES_PER_DAY = 1440.0
    private const val SHORT_WINDOW = 7
    private const val LONG_WINDOW = 28

    /** today.year - birthYear, at least 10. */
    fun age(profile: BodyProfile, today: LocalDate): Int = (today.year - profile.birthYear).coerceAtLeast(MIN_AGE)

    /** The manual rate when set, else Mifflin-St Jeor. */
    fun bmr(profile: BodyProfile, today: LocalDate): Double {
        profile.manualBmrKcal?.let { return it.toDouble() }
        val sexTerm = if (profile.sex == Sex.MALE) 5.0 else -161.0
        return 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * age(profile, today) + sexTerm
    }

    /** bmr * 1.2 * (1 + goal/100), rounded to 10. */
    fun restDayKcal(profile: BodyProfile, today: LocalDate): Int =
        roundTo10(bmr(profile, today) * ACTIVITY_FACTOR * goalFactor(profile))

    /**
     * NET burn of the day's sessions: only the extra over the baseline (1.2 x BMR already covers the whole day).
     * Strength: (STRENGTH_MET - 1) * kg * hours when the session has working non-cardio sets; hours = ended - effective
     * start (SessionTimeRules, so cardio minutes count), capped at 3 h; a closed session with zero duration counts
     * 20 min; an open session counts up to [now]. Cardio: per set, logged calories minus the resting burn of its
     * minutes (bmr / 1440 per minute, [date]'s BMR), never below zero.
     */
    fun trainingBurnKcal(profile: BodyProfile, date: LocalDate, sessions: List<SessionSets>, now: Instant): Double {
        val restingPerMinute = bmr(profile, date) / MINUTES_PER_DAY
        return sessions.sumOf { (session, sets) ->
            val cardio = sets.filter { CardioRules.isCardio(it) }.sumOf { set ->
                val minutes = (set.seconds ?: 0) / 60.0
                maxOf(0.0, (set.calories ?: 0) - restingPerMinute * minutes)
            }
            val hasStrength = sets.filterNot { CardioRules.isCardio(it) }.working().isNotEmpty()
            cardio + if (hasStrength) (STRENGTH_MET - 1.0) * profile.weightKg * hours(session, sets, now) else 0.0
        }
    }

    /**
     * A day's estimate: baseline (1.2 x BMR) plus the NET training burn, times the goal factor. Rest days are the
     * baseline alone; [trainingExpected] only matters for a day without sets.
     */
    fun day(
        profile: BodyProfile,
        date: LocalDate,
        now: Instant,
        sessions: List<SessionSets>,
        trainingExpected: Boolean,
    ): DayEnergy {
        val rest = restDayKcal(profile, date)
        val base = bmr(profile, date) * ACTIVITY_FACTOR
        val factor = goalFactor(profile)
        return when {
            sessions.any { it.sets.isNotEmpty() } -> {
                val burn = trainingBurnKcal(profile, date, sessions, now)
                DayEnergy(roundTo10((base + burn) * factor), rest, roundTo10(burn), DayKind.TRAINED)
            }
            trainingExpected -> {
                val burn = (STRENGTH_MET - 1.0) * profile.weightKg * (DEFAULT_SESSION_MINUTES / 60.0)
                DayEnergy(roundTo10((base + burn) * factor), rest, roundTo10(burn), DayKind.TRAINING_EXPECTED)
            }
            else -> DayEnergy(rest, rest, 0, DayKind.REST)
        }
    }

    /** The weight on [date]: the newest entry dated on or before it, else the profile's weight. */
    fun weightOn(profile: BodyProfile, weights: List<WeightEntry>, date: LocalDate): Double =
        weights.filter { !it.date.isAfter(date) }.maxByOrNull { it.date }?.kg ?: profile.weightKg

    /**
     * One point per calendar day from [from] to [to] (never past [to]; days without sets are rest days), each from
     * [day] with that day's weight. Averages are rounded to 10; the 28-day one needs at least 7 points.
     */
    fun history(
        profile: BodyProfile,
        weights: List<WeightEntry>,
        sessionsByDate: Map<LocalDate, List<SessionSets>>,
        from: LocalDate,
        to: LocalDate,
        now: Instant,
    ): EnergyHistory {
        val points = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { date ->
            val result = day(
                profile = profile.copy(weightKg = weightOn(profile, weights, date)),
                date = date,
                now = now,
                sessions = sessionsByDate[date].orEmpty(),
                trainingExpected = false,
            )
            EnergyPoint(date, result.kcal, result.trainingBurnKcal, result.kind == DayKind.TRAINED)
        }.toList()
        val trained = points.filter { it.trained }
        return EnergyHistory(
            points = points,
            last7AverageKcal = points.takeIf { it.isNotEmpty() }?.let { averageOfLast(it, SHORT_WINDOW) },
            last28AverageKcal = points.takeIf { it.size >= SHORT_WINDOW }?.let { averageOfLast(it, LONG_WINDOW) },
            averageBurnKcal = trained.takeIf { it.isNotEmpty() }?.let { roundTo10(it.map { p -> p.burnKcal }.average()) },
            trainedDays = trained.size,
        )
    }

    private fun averageOfLast(points: List<EnergyPoint>, n: Int): Int =
        roundTo10(points.takeLast(n).map { it.kcal }.average())

    private fun goalFactor(profile: BodyProfile): Double = 1.0 + profile.coerced().goalAdjustmentPercent / 100.0

    private fun hours(session: Session, sets: List<WorkoutSet>, now: Instant): Double {
        val max = Duration.ofHours(MAX_SESSION_HOURS.toLong())
        val end = session.endedAt
        val start = SessionTimeRules.effectiveStart(session, sets)
        val duration = if (end != null) Duration.between(start, end) else Duration.between(start, now)
        val shown = when {
            end != null && duration.isZero -> Duration.ofMinutes(MIN_SESSION_MINUTES.toLong())
            duration.isNegative -> Duration.ZERO
            else -> duration
        }
        return minOf(shown, max).toMillis() / 3_600_000.0
    }

    private fun roundTo10(value: Double): Int = (value / 10.0).roundToInt() * 10
}
