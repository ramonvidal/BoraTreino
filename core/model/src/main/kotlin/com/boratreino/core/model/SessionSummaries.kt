package com.boratreino.core.model

import java.time.Instant
import kotlin.math.abs

/** A short description of the sets logged on one machine. */
sealed interface SetSummary {
    /** All sets equal: "3 x 10 at 40 kg". */
    data class Uniform(val count: Int, val weightKg: Double, val reps: Int) : SetSummary

    /** Same weight, different reps: "40 kg: 10, 10, 8". */
    data class SameWeight(val weightKg: Double, val reps: List<Int>) : SetSummary

    /** Different weights: "40 kg x 10, 42.5 kg x 8". */
    data class Mixed(val sets: List<SetDraft>) : SetSummary

    /** Cardio sets: [count] sets with their totals; 0 = not logged. */
    data class Cardio(val count: Int, val minutes: Int, val calories: Int, val distanceM: Int) : SetSummary

    /**
     * Weight + time, weight + distance, or time sets (no reps). [sets] are in position order, with reps 0 and the
     * amount in seconds ([SetMeasure.SECONDS]) or distanceM ([SetMeasure.METRES]).
     */
    data class Measured(val measure: SetMeasure, val sets: List<SetDraft>) : SetSummary {
        /** All sets share the weight (within 0.001) and the amount. */
        val uniform: Boolean
            get() {
                val first = sets.firstOrNull() ?: return true
                return sets.all {
                    abs(it.weightKg - first.weightKg) <= EPSILON && amountOf(it) == amountOf(first)
                }
            }

        private fun amountOf(draft: SetDraft): Int = if (measure == SetMeasure.SECONDS) draft.seconds else draft.distanceM
    }

    companion object {
        private const val EPSILON = 0.001

        /**
         * Warm-up sets are left out. Null when there are no working sets. All cardio sets -> Cardio (totals). All sets without reps but with seconds -> Measured(SECONDS); without reps but with distance -> Measured(METRES). Otherwise sets are taken in position
         * order; weights equal within 0.001.
         */
        fun of(allSets: List<WorkoutSet>): SetSummary? {
            val sets = allSets.working()
            if (sets.isEmpty()) return null
            if (sets.all { CardioRules.isCardio(it) }) {
                return Cardio(
                    count = sets.size,
                    minutes = CardioRules.minutesOf(sets.sumOf { it.seconds ?: 0 }),
                    calories = sets.sumOf { it.calories ?: 0 },
                    distanceM = sets.sumOf { it.distanceM ?: 0 },
                )
            }
            val ordered = sets.sortedBy { it.position }
            if (ordered.all { it.reps == null && it.seconds != null }) {
                return Measured(
                    SetMeasure.SECONDS,
                    ordered.map { SetDraft(it.weightKg ?: 0.0, 0, seconds = it.seconds ?: 0) },
                )
            }
            if (ordered.all { it.reps == null && it.distanceM != null }) {
                return Measured(
                    SetMeasure.METRES,
                    ordered.map { SetDraft(it.weightKg ?: 0.0, 0, distanceM = it.distanceM ?: 0) },
                )
            }
            val drafts = sets.sortedBy { it.position }.map { SetDraft(it.weightKg ?: 0.0, it.reps ?: 0) }
            val first = drafts.first()
            if (drafts.any { abs(it.weightKg - first.weightKg) > EPSILON }) return Mixed(drafts)
            return if (drafts.all { it.reps == first.reps }) {
                Uniform(drafts.size, first.weightKg, first.reps)
            } else {
                SameWeight(first.weightKg, drafts.map { it.reps })
            }
        }
    }
}

/** A machine with what was done on it last time and in the open session. */
data class MachineLog(
    val machine: Machine,
    val lastTime: List<WorkoutSet>,
    val lastTimeAt: Instant?,
    val thisSession: List<WorkoutSet>,
    /** This machine's item in the active routine, if any. */
    val target: RoutineItem? = null,
    /** Progression suggestion for the next set, if any. */
    val suggestion: Suggestion? = null,
)

/** One machine's sets within a session. */
data class SessionMachine(val machine: Machine, val sets: List<WorkoutSet>)

/** The open session; [machines] has the most recently logged first. */
data class OpenSession(val session: Session, val machines: List<SessionMachine>)

/** Totals shown after finishing a session. */
data class SessionSummary(val sessionId: Long, val machineCount: Int, val setCount: Int, val recordCount: Int = 0)
