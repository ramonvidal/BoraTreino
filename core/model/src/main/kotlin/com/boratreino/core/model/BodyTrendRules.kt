package com.boratreino.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

object BodyTrendRules {
    const val MIN_BODY_POINTS = 3

    /**
     * Time-aware exponentially weighted moving average, ascending by date. The first point is unchanged; each next
     * point moves the average by the effective alpha 1 - (1 - alpha)^(gapDays / 7). Points on the same date are
     * first merged into their mean. Non-finite values are dropped; [alpha] is clamped to 0..1.
     */
    fun ewma(points: List<Pair<LocalDate, Double>>, alpha: Double = 0.3): List<Pair<LocalDate, Double>> {
        val a = alpha.coerceIn(0.0, 1.0)
        val daily = points.filter { it.second.isFinite() }
            .groupBy({ it.first }, { it.second })
            .map { (date, values) -> date to values.average() }
            .sortedBy { it.first }
        val out = ArrayList<Pair<LocalDate, Double>>(daily.size)
        for ((date, value) in daily) {
            val prev = out.lastOrNull()
            if (prev == null) {
                out += date to value
            } else {
                val gap = ChronoUnit.DAYS.between(prev.first, date).toDouble()
                val effective = 1.0 - (1.0 - a).pow(gap / 7.0)
                out += date to (prev.second + effective * (value - prev.second))
            }
        }
        return out
    }

    /** Waist / hips for each date that has both (hips > 0), ascending; with several of a type on a date the last listed wins. Not rounded. */
    fun waistHipRatio(measurements: List<BodyMeasurement>): List<Pair<LocalDate, Double>> {
        val waist = measurements.filter { it.type == MeasurementType.WAIST }.associate { it.date to it.valueCm }
        val hips = measurements.filter { it.type == MeasurementType.HIPS }.associate { it.date to it.valueCm }
        return waist.keys.intersect(hips.keys).sorted().mapNotNull { date ->
            val h = hips.getValue(date)
            val w = waist.getValue(date)
            if (h > 0.0 && w.isFinite() && h.isFinite()) date to w / h else null
        }
    }
}
