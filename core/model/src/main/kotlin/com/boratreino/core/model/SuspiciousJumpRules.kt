package com.boratreino.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

/** An upward jump in the top working weight between two consecutive sessions of [machine] that looks like a typo. */
data class SuspiciousJump(
    val machine: Machine,
    val fromDate: LocalDate,
    val toDate: LocalDate,
    val fromKg: Double,
    val toKg: Double,
) {
    val percent: Double get() = (toKg - fromKg) / fromKg * 100.0
    val days: Int get() = ChronoUnit.DAYS.between(fromDate, toDate).toInt()
}

/** Flags load jumps that probably are typos. A flag only: it never changes the data. */
object SuspiciousJumpRules {
    const val MIN_RATIO = 1.40
    const val MAX_DAYS_APART = 14
    const val MIN_JUMP_KG = 5.0

    /** A later session at this share of the jumped weight confirms the jump (it was trained with again). */
    const val CONFIRM_RATIO = 0.90

    /**
     * Top working weight per session, ascending: progress.points when progress.metric == WEIGHT, else
     * progress.measurePoints[WEIGHT], else empty (cardio, TIME, unloaded bodyweight).
     */
    fun weightSeries(progress: MachineProgress): List<SessionPoint> = when {
        progress.machine.kind == MachineKind.CARDIO -> emptyList()
        progress.metric == ProgressMetric.WEIGHT -> progress.points
        else -> progress.measurePoints[ProgressMetric.WEIGHT].orEmpty()
    }

    /**
     * The LATEST unconfirmed pair of consecutive points a, b in [weightPoints] (ascending by date then sessionId)
     * with a.value > 0, b.value >= a.value * MIN_RATIO, b.value - a.value >= max(MIN_JUMP_KG, 2 * machine.stepKg),
     * days(a.date, b.date) <= MAX_DAYS_APART, and b.date in [from]..[to]. A jump is confirmed (not flagged) when a
     * later session has a value of at least CONFIRM_RATIO of b.value. null when there is none. Cardio machines -> null.
     */
    fun find(machine: Machine, weightPoints: List<SessionPoint>, from: LocalDate, to: LocalDate): SuspiciousJump? {
        if (machine.kind == MachineKind.CARDIO) return null
        val points = weightPoints.sortedWith(compareBy({ it.date }, { it.sessionId }))
        val minRise = max(MIN_JUMP_KG, 2 * machine.stepKg)
        for (i in points.lastIndex downTo 1) {
            val a = points[i - 1]
            val b = points[i]
            if (b.date.isBefore(from) || b.date.isAfter(to)) continue
            if (a.value <= 0.0) continue
            if (b.value < a.value * MIN_RATIO || b.value - a.value < minRise) continue
            if (ChronoUnit.DAYS.between(a.date, b.date) > MAX_DAYS_APART) continue
            val confirmed = (i + 1..points.lastIndex).any { points[it].value >= b.value * CONFIRM_RATIO }
            if (confirmed) continue
            return SuspiciousJump(machine, a.date, b.date, a.value, b.value)
        }
        return null
    }
}
