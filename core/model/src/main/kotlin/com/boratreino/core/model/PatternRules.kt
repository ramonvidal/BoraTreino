package com.boratreino.core.model

import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * [meanIndex] = mean percent difference from each exercise's own average; [standardError] null when [n] < 2.
 * [tValue] = meanIndex / standardError (null when the standard error is null or 0). [pValue] = raw two-sided p of a
 * one-sample t-test of the mean index against 0 (df = n - 1); null when the bucket was not tested (n below
 * [PatternRules.MIN_PER_BUCKET] or standard error null or 0). [significant] = survived the Holm-Bonferroni correction
 * across all tested buckets (weekday and rest together) at alpha 0.05.
 */
data class PatternBucket(
    val key: Int,
    val n: Int,
    val meanIndex: Double,
    val standardError: Double?,
    val tValue: Double? = null,
    val significant: Boolean = false,
    val pValue: Double? = null,
)

data class PatternReport(
    val weekday: List<PatternBucket>,
    val rest: List<PatternBucket>,
    val sessionsUsed: Int,
    val enough: Boolean,
    /** True when at least one bucket in [weekday] or [rest] is [PatternBucket.significant]. */
    val anySignificant: Boolean = false,
)

object PatternRules {
    const val MIN_SESSIONS = 15
    const val MIN_PER_BUCKET = 5
    const val ALPHA = 0.05
    private const val MIN_POINTS_PER_MACHINE = 3

    /**
     * Does performance (estimated 1RM per session) depend on weekday or on rest days? Per machine, each session's
     * index = (value / the machine's mean value - 1) * 100. Machines with fewer than 3 [MachineProgress.oneRmPoints]
     * (cardio has none) or a mean <= 0 are skipped. Weekday key = DayOfWeek.value (1..7), every session counts.
     * Rest key = days since the same machine's previous session: 0 = 0..3, 1 = 4..6, 2 = 7+; a machine's first
     * session has no rest value and is left out of [PatternReport.rest]. Buckets are ascending by key, empty ones
     * omitted, small ones (n < [MIN_PER_BUCKET]) kept but never tested.
     * Buckets with n >= [MIN_PER_BUCKET] and a standard error > 0 get a two-sided t-test of the mean index against 0;
     * the p-values of both families together go through Holm-Bonferroni at [ALPHA] to set [PatternBucket.significant]. [PatternReport.sessionsUsed] = sessions of the machines used.
     */
    fun analyze(progresses: List<MachineProgress>): PatternReport {
        val weekday = HashMap<Int, MutableList<Double>>()
        val rest = HashMap<Int, MutableList<Double>>()
        var used = 0
        for (progress in progresses) {
            val points = progress.oneRmPoints.sortedWith(compareBy({ it.date }, { it.sessionId }))
            if (points.size < MIN_POINTS_PER_MACHINE) continue
            val mean = points.sumOf { it.value } / points.size
            if (mean <= 0.0) continue
            used += points.size
            points.forEachIndexed { i, point ->
                val index = (point.value / mean - 1.0) * 100.0
                weekday.getOrPut(point.date.dayOfWeek.value) { ArrayList() }.add(index)
                if (i > 0) {
                    val gap = ChronoUnit.DAYS.between(points[i - 1].date, point.date)
                    val key = when {
                        gap <= 3 -> 0
                        gap <= 6 -> 1
                        else -> 2
                    }
                    rest.getOrPut(key) { ArrayList() }.add(index)
                }
            }
        }
        val weekdayBuckets = buckets(weekday)
        val restBuckets = buckets(rest)
        val pooled = weekdayBuckets + restBuckets
        val flags = holmFlags(pooled.map { it.pValue })
        val marked = pooled.mapIndexed { i, b -> b.copy(significant = flags[i]) }
        val weekdayOut = marked.subList(0, weekdayBuckets.size)
        val restOut = marked.subList(weekdayBuckets.size, marked.size)
        return PatternReport(weekdayOut, restOut, used, used >= MIN_SESSIONS, flags.any { it })
    }

    private fun buckets(groups: Map<Int, List<Double>>): List<PatternBucket> =
        groups.entries.sortedBy { it.key }.map { (key, values) ->
            val n = values.size
            val mean = values.average()
            val se = if (n < 2) null else sqrt(values.sumOf { (it - mean) * (it - mean) } / (n - 1)) / sqrt(n.toDouble())
            val t = if (se != null && se > 0.0) mean / se else null
            val pValue = if (t != null && n >= MIN_PER_BUCKET) twoSidedP(t, n - 1) else null
            PatternBucket(key, n, mean, se, t, false, pValue)
        }

    /**
     * Holm-Bonferroni at [ALPHA]: with m tested p-values sorted ascending, the i-th (0-based) is rejected while
     * p <= ALPHA / (m - i); the first failure stops it. Null p-values (untested) are never rejected and not counted in m.
     */
    fun holmFlags(pValues: List<Double?>): List<Boolean> {
        val tested = pValues.withIndex().filter { it.value != null }.sortedBy { it.value }
        val m = tested.size
        val flags = BooleanArray(pValues.size)
        for ((rank, entry) in tested.withIndex()) {
            if (entry.value!! <= ALPHA / (m - rank)) flags[entry.index] = true else break
        }
        return flags.toList()
    }

    /** Two-sided p of Student's t with [df] degrees of freedom: I_{df/(df+t^2)}(df/2, 1/2). */
    fun twoSidedP(t: Double, df: Int): Double {
        val x = df / (df + t * t)
        return regularizedBeta(x, df / 2.0, 0.5).coerceIn(0.0, 1.0)
    }

    /** Regularized incomplete beta I_x(a, b) (Numerical Recipes betai / betacf, modified Lentz). */
    fun regularizedBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val front = exp(lnGamma(a + b) - lnGamma(a) - lnGamma(b) + a * ln(x) + b * ln(1.0 - x))
        return if (x < (a + 1.0) / (a + b + 2.0)) {
            front * betaContinuedFraction(x, a, b) / a
        } else {
            1.0 - front * betaContinuedFraction(1.0 - x, b, a) / b
        }
    }

    private fun betaContinuedFraction(x: Double, a: Double, b: Double): Double {
        val tiny = 1e-300
        val qab = a + b
        val qap = a + 1.0
        val qam = a - 1.0
        var c = 1.0
        var d = 1.0 - qab * x / qap
        if (abs(d) < tiny) d = tiny
        d = 1.0 / d
        var h = d
        for (m in 1..500) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((qam + m2) * (a + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            h *= d * c
            aa = -(a + m) * (qab + m) * x / ((a + m2) * (qap + m2))
            d = 1.0 + aa * d
            if (abs(d) < tiny) d = tiny
            c = 1.0 + aa / c
            if (abs(c) < tiny) c = tiny
            d = 1.0 / d
            val delta = d * c
            h *= delta
            if (abs(delta - 1.0) < 1e-14) break
        }
        return h
    }

    /** ln Gamma(x) for x > 0 (Lanczos, g = 7, n = 9). */
    private fun lnGamma(x: Double): Double {
        if (x < 0.5) return ln(Math.PI / abs(sin(Math.PI * x))) - lnGamma(1.0 - x)
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028, 771.32342877765313,
            -176.61502916214059, 12.507343278686905, -0.13857109526572012, 9.9843695780195716e-6,
            1.5056327351493116e-7,
        )
        val xm = x - 1.0
        var sum = c[0]
        for (i in 1 until 9) sum += c[i] / (xm + i)
        val t = xm + 7.5
        return 0.5 * ln(2.0 * Math.PI) + (xm + 0.5) * ln(t) - t + ln(sum)
    }
}
