package com.boratreino.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

data class SessionSets(val session: Session, val sets: List<WorkoutSet>)

/** All values are rounded to 10 kcal. */
data class DayEnergy(val kcal: Int, val restDayKcal: Int, val trainingBurnKcal: Int, val kind: DayKind)

enum class DayKind { TRAINED, TRAINING_EXPECTED, REST }

/** Estimate of a day's energy need. Never tracks food; an estimate, not advice. */
object EnergyRules {
    const val ACTIVITY_FACTOR = 1.2
    const val STRENGTH_MET = 4.0
    const val DEFAULT_SESSION_MINUTES = 60
    const val MIN_SESSION_MINUTES = 20
    const val MAX_SESSION_HOURS = 3
    private const val MIN_AGE = 10

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
     * Burn of the day's sessions: logged cardio calories, plus STRENGTH_MET * kg * hours when the
     * session has working non-cardio sets. Hours = ended - started, capped at 3 h; a closed session
     * with zero duration counts 20 min; an open session counts up to [now].
     */
    fun trainingBurnKcal(profile: BodyProfile, sessions: List<SessionSets>, now: Instant): Double =
        sessions.sumOf { (session, sets) ->
            val cardio = sets.filter { CardioRules.isCardio(it) }.sumOf { it.calories ?: 0 }.toDouble()
            val hasStrength = sets.filterNot { CardioRules.isCardio(it) }.working().isNotEmpty()
            cardio + if (hasStrength) STRENGTH_MET * profile.weightKg * hours(session, now) else 0.0
        }

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
                val burn = trainingBurnKcal(profile, sessions, now)
                DayEnergy(roundTo10((base + burn) * factor), rest, roundTo10(burn), DayKind.TRAINED)
            }
            trainingExpected -> {
                val burn = STRENGTH_MET * profile.weightKg * (DEFAULT_SESSION_MINUTES / 60.0)
                DayEnergy(roundTo10((base + burn) * factor), rest, roundTo10(burn), DayKind.TRAINING_EXPECTED)
            }
            else -> DayEnergy(rest, rest, 0, DayKind.REST)
        }
    }

    private fun goalFactor(profile: BodyProfile): Double = 1.0 + profile.coerced().goalAdjustmentPercent / 100.0

    private fun hours(session: Session, now: Instant): Double {
        val max = Duration.ofHours(MAX_SESSION_HOURS.toLong())
        val end = session.endedAt
        val duration = if (end != null) Duration.between(session.startedAt, end) else Duration.between(session.startedAt, now)
        val shown = when {
            end != null && duration.isZero -> Duration.ofMinutes(MIN_SESSION_MINUTES.toLong())
            duration.isNegative -> Duration.ZERO
            else -> duration
        }
        return minOf(shown, max).toMillis() / 3_600_000.0
    }

    private fun roundTo10(value: Double): Int = (value / 10.0).roundToInt() * 10
}
