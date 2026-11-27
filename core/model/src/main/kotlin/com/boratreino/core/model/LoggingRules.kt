package com.boratreino.core.model

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import java.time.Instant
import kotlin.math.roundToLong

/**
 * Weight and amount the steppers hold before a set is logged. [reps] is the amount for REPS kinds; for SECONDS kinds
 * [seconds] is and [reps] is 0; for METRES kinds [distanceM] is and [reps] is 0.
 */
data class SetDraft(
    val weightKg: Double,
    val reps: Int,
    val type: SetType = SetType.NORMAL,
    val seconds: Int = 0,
    val distanceM: Int = 0,
)

/** Rules for the logging path: what the steppers start at and how they move. */
object LoggingRules {
    const val DEFAULT_REPS = 10
    const val MAX_REPS = 100
    const val MAX_WEIGHT_KG = 1000.0
    const val DEFAULT_SECONDS = 30
    const val SECONDS_STEP = 5
    const val MAX_SECONDS = 3600
    const val DEFAULT_DISTANCE_M = 20
    const val DISTANCE_STEP_M = 5
    const val MAX_DISTANCE_M = 5000

    /** Weights closer than this are the same weight. */
    private const val EPSILON = 0.001

    /** Draft of [set] for [kind]: null weight counts 0.0; REPS reps default to DEFAULT_REPS, the others to their defaults. */
    fun draftOf(set: WorkoutSet, kind: MachineKind): SetDraft {
        val weight = set.weightKg ?: 0.0
        return when (kind.measure) {
            SetMeasure.SECONDS -> SetDraft(weight, 0, seconds = set.seconds ?: DEFAULT_SECONDS)
            SetMeasure.METRES -> SetDraft(weight, 0, distanceM = set.distanceM ?: DEFAULT_DISTANCE_M)
            else -> SetDraft(weight, set.reps ?: DEFAULT_REPS)
        }
    }

    private fun defaultDraft(kind: MachineKind): SetDraft = when (kind.measure) {
        SetMeasure.SECONDS -> SetDraft(0.0, 0, seconds = DEFAULT_SECONDS)
        SetMeasure.METRES -> SetDraft(0.0, 0, distanceM = DEFAULT_DISTANCE_M)
        else -> SetDraft(0.0, DEFAULT_REPS)
    }

    /**
     * Steppers' starting values (warm-ups never count): this session's last set if any; else [suggestion] if any
     * (REPS kinds only); else last time's first set; else the kind's default: 0 kg x DEFAULT_REPS, 0 kg for
     * DEFAULT_SECONDS, or 0 kg for DEFAULT_DISTANCE_M.
     */
    fun prefill(
        lastTime: List<WorkoutSet>,
        thisSession: List<WorkoutSet>,
        suggestion: Suggestion? = null,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
    ): SetDraft {
        thisSession.working().lastOrNull()?.let { return draftOf(it, kind) }
        if (suggestion != null && kind.measure == SetMeasure.REPS) return SetDraft(suggestion.weightKg, suggestion.reps)
        return lastTime.working().firstOrNull()?.let { draftOf(it, kind) } ?: defaultDraft(kind)
    }

    /** Last time's working set at index (working sets in thisSession), as a draft; null when there is none. */
    fun sameAgain(
        lastTime: List<WorkoutSet>,
        thisSession: List<WorkoutSet>,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
    ): SetDraft? = lastTime.working().getOrNull(thisSession.working().size)?.let { draftOf(it, kind) }

    /**
     * One step up or down. An off-grid weight first snaps to the next multiple of [stepKg] in that
     * direction (a weight within 0.001 of a multiple counts as on the grid). Clamped to 0..MAX_WEIGHT_KG.
     */
    fun stepWeight(weightKg: Double, stepKg: Double, up: Boolean): Double {
        if (stepKg <= 0.0) return weightKg.coerceIn(0.0, MAX_WEIGHT_KG)
        val units = weightKg / stepKg
        val nearest = units.roundToLong()
        val onGrid = abs(weightKg - nearest * stepKg) <= EPSILON
        val target = when {
            onGrid -> if (up) nearest + 1 else nearest - 1
            up -> ceil(units).toLong()
            else -> floor(units).toLong()
        }
        val stepped = ((target * stepKg) * 1000.0).roundToLong() / 1000.0
        return stepped.coerceIn(0.0, MAX_WEIGHT_KG)
    }

    /** +/-1, clamped to 1..MAX_REPS. */
    fun stepReps(reps: Int, up: Boolean): Int = (if (up) reps + 1 else reps - 1).coerceIn(1, MAX_REPS)

    /** +/-5 s on the 5 s grid (an off-grid value snaps in that direction), clamped to 1..MAX_SECONDS. */
    fun stepSeconds(seconds: Int, up: Boolean): Int = stepOnGrid(seconds, SECONDS_STEP, up, MAX_SECONDS)

    /** +/-5 m on the 5 m grid (an off-grid value snaps in that direction), clamped to 1..MAX_DISTANCE_M. */
    fun stepDistance(distanceM: Int, up: Boolean): Int = stepOnGrid(distanceM, DISTANCE_STEP_M, up, MAX_DISTANCE_M)

    private fun stepOnGrid(value: Int, step: Int, up: Boolean, max: Int): Int {
        val rem = Math.floorMod(value, step)
        val target = when {
            rem == 0 -> if (up) value + step else value - step
            up -> value - rem + step
            else -> value - rem
        }
        return target.coerceIn(1, max)
    }

    /** REPS: always true. SECONDS: seconds > 0. METRES: distanceM > 0. CARDIO: false. */
    fun canLog(draft: SetDraft, kind: MachineKind): Boolean = when (kind.measure) {
        null -> false
        SetMeasure.REPS -> true
        SetMeasure.SECONDS -> draft.seconds > 0
        SetMeasure.METRES -> draft.distanceM > 0
    }

    /** A new set in [kind]'s shape (see [WorkoutSet]); TIME forces weightKg = 0.0. Values are clamped. */
    fun toSet(
        id: Long,
        sessionId: Long,
        machineId: Long,
        position: Int,
        draft: SetDraft,
        kind: MachineKind,
        loggedAt: Instant,
    ): WorkoutSet {
        require(kind.isStrength) { "toSet is for strength kinds, not $kind" }
        return applyDraft(
            WorkoutSet(
                id = id, sessionId = sessionId, machineId = machineId, position = position,
                weightKg = 0.0, reps = null, seconds = null, calories = null, distanceM = null,
                loggedAt = loggedAt,
            ),
            draft,
            kind,
        )
    }

    /** [set] with weight, amount and type from [draft] in [kind]'s shape; id, session, position and loggedAt kept. */
    fun applyDraft(set: WorkoutSet, draft: SetDraft, kind: MachineKind): WorkoutSet {
        require(kind.isStrength) { "applyDraft is for strength kinds, not $kind" }
        val weight = if (kind.hasLoad) draft.weightKg.coerceIn(0.0, MAX_WEIGHT_KG) else 0.0
        return when (kind.measure) {
            SetMeasure.SECONDS -> set.copy(
                weightKg = weight, reps = null, seconds = draft.seconds.coerceIn(0, MAX_SECONDS),
                calories = null, distanceM = null, intensity = null, type = draft.type,
            )
            SetMeasure.METRES -> set.copy(
                weightKg = weight, reps = null, seconds = null,
                calories = null, distanceM = draft.distanceM.coerceIn(0, MAX_DISTANCE_M), intensity = null,
                type = draft.type,
            )
            else -> set.copy(
                weightKg = weight, reps = draft.reps.coerceIn(0, MAX_REPS), seconds = null,
                calories = null, distanceM = null, intensity = null, type = draft.type,
            )
        }
    }
}
