package com.boratreino.core.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

object ChartRules {
    private const val EPS = 1e-9

    private fun clean(x: Double): Double = (x * 1e9).roundToLong() / 1e9

    /** Smallest {1, 2, 2.5, 5, 10} x 10^n that is >= [raw]. */
    private fun niceStep(raw: Double): Double {
        if (!raw.isFinite() || raw <= 0.0) return 1.0
        val exp = floor(log10(raw)).toInt()
        val base = 10.0.pow(exp)
        for (m in doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
            if (m * base >= raw * (1 - EPS)) return m * base
        }
        return 10.0 * base
    }

    /**
     * A "nice" scale around [min]..[max]: step from {1, 2, 2.5, 5} x 10^n, roughly [targetTicks] ticks, bounds are
     * multiples of the step enclosing the data; equal min and max -> padded by one step each side; never below 0
     * for a non-negative input. Swapped input is swapped back; non-finite input counts as 0.
     */
    fun niceScale(min: Double, max: Double, targetTicks: Int = 4): ChartScale {
        var a = if (min.isFinite()) min else 0.0
        var b = if (max.isFinite()) max else 0.0
        if (a > b) { val t = a; a = b; b = t }
        val intervals = maxOf(targetTicks - 1, 1)
        val nonNegative = a >= 0.0

        val step: Double
        var lo: Double
        var hi: Double
        if (b - a <= EPS * maxOf(1.0, abs(b))) {
            val raw = if (abs(a) < EPS) 1.0 / intervals else abs(a) * 0.2 / intervals
            step = niceStep(raw)
            lo = (floor(a / step + EPS) - 1) * step
            hi = (ceil(a / step - EPS) + 1) * step
        } else {
            step = niceStep((b - a) / intervals)
            lo = floor(a / step + EPS) * step
            hi = ceil(b / step - EPS) * step
        }
        if (nonNegative && lo < 0.0) lo = 0.0
        if (hi <= lo) hi = lo + step
        val count = maxOf(((hi - lo) / step).roundToLong().toInt(), 1)
        val ticks = List(count + 1) { clean(lo + it * step) }
        return ChartScale(ticks.first(), ticks.last(), ticks)
    }

    /** x position in 0..1 of each date between [start] and [end] (inclusive), proportional to days; a single-day span -> 0.5 for every date. */
    fun timeFractions(dates: List<LocalDate>, start: LocalDate, end: LocalDate): List<Float> {
        val total = ChronoUnit.DAYS.between(start, end)
        if (total <= 0) return dates.map { 0.5f }
        return dates.map { (ChronoUnit.DAYS.between(start, it).toFloat() / total).coerceIn(0f, 1f) }
    }

    /**
     * First-of-month dates inside start..end, thinned to at most [maxTicks] (every 1, 2, 3, 6, 12 months as
     * needed); when the span is under 45 days, ticks every 7 days from [start] instead.
     */
    fun axisTicks(start: LocalDate, end: LocalDate, maxTicks: Int = 5): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        if (ChronoUnit.DAYS.between(start, end) < 45) {
            val out = ArrayList<LocalDate>()
            var d = start
            while (!d.isAfter(end)) {
                out.add(d)
                d = d.plusDays(7)
            }
            return out
        }
        val months = ArrayList<LocalDate>()
        var d = if (start.dayOfMonth == 1) start else start.withDayOfMonth(1).plusMonths(1)
        while (!d.isAfter(end)) {
            months.add(d)
            d = d.plusMonths(1)
        }
        val limit = maxOf(maxTicks, 1)
        var every = 1
        for (candidate in intArrayOf(1, 2, 3, 6, 12)) {
            every = candidate
            if ((months.size + candidate - 1) / candidate <= limit) break
        }
        while ((months.size + every - 1) / every > limit) every += 12
        return months.filterIndexed { i, _ -> i % every == 0 }
    }
}
