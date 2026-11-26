package com.boratreino.core.model

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.sqrt

/**
 * An ordinary-least-squares line through (day number, value) points; see [TrendRules.fit].
 * All slopes are per week, the values are in the series' own unit.
 */
data class TrendFit(
    val n: Int,
    val slopePerWeek: Double,
    /** Lower end of the 95% confidence interval of the slope, per week. */
    val slopeLow: Double,
    /** Upper end of the 95% confidence interval of the slope, per week. */
    val slopeHigh: Double,
    /** Share of the variance the line explains, 0..1 (0 when the values are all equal). */
    val r2: Double,
    /** The fitted line evaluated at the last date. */
    val fittedAtLast: Double,
    /** Days between the first and the last date. */
    val spanDays: Int,
    /** Mean of the day numbers ([LocalDate.toEpochDay]). */
    val meanDay: Double,
    /** Sum of squared deviations of the day numbers from [meanDay] (days squared). */
    val sxx: Double,
    /** Residual standard deviation, sqrt(SSE / (n - 2)); 0 for a perfect fit. */
    val residualSd: Double,
    /** Two-sided 95% Student t critical value for n - 2 degrees of freedom (1.96 above 30). */
    val tCritical: Double,
    /** Day number of the last date; the forecast starts here. */
    val lastDay: Double = meanDay,
)

enum class TrendVerdict { RISING, FALLING, PLATEAU, NOT_ENOUGH_DATA }

data class TrendResult(
    val verdict: TrendVerdict,
    val fit: TrendFit?,
    val points: Int,
    val neededPoints: Int,
    val neededSpanDays: Int,
)

/** [value] with its 95% PREDICTION interval ([low]..[high]) [weeksAhead] weeks after the last date. */
data class Forecast(val weeksAhead: Double, val value: Double, val low: Double, val high: Double)

object TrendRules {
    const val MIN_POINTS = 5
    const val MIN_SPAN_DAYS = 14

    /** Two-sided 95% Student t critical values, index = degrees of freedom - 1 (df 1..30). */
    private val T_95 = doubleArrayOf(
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042,
    )

    /** Two-sided 95% critical value of Student's t with [df] degrees of freedom (df < 1 -> df 1; above 30 -> 1.96). */
    fun tCritical(df: Int): Double = if (df > T_95.size) 1.96 else T_95[max(df, 1) - 1]

    /**
     * OLS of value on day number ([LocalDate.toEpochDay]). Needs at least 3 finite points on at least 2 distinct
     * dates, else null. Several points on one date are all used. The slope is in units per week; its 95% interval
     * is slope +- t * residualSd / sqrt(sxx) with n - 2 degrees of freedom. A perfect fit (zero residuals) gives
     * residualSd 0 and slopeLow == slopeHigh == slope. All values equal gives slope 0 and r2 0. Never NaN or infinite.
     */
    fun fit(points: List<Pair<LocalDate, Double>>): TrendFit? {
        val clean = points.filter { it.second.isFinite() }
        val n = clean.size
        if (n < 3) return null
        val xs = clean.map { it.first.toEpochDay().toDouble() }
        val ys = clean.map { it.second }
        val meanX = xs.sum() / n
        val meanY = ys.sum() / n
        var sxx = 0.0
        var sxy = 0.0
        var syy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxx += dx * dx
            sxy += dx * dy
            syy += dy * dy
        }
        if (sxx <= 0.0) return null
        val flat = syy <= 1e-24 * (1.0 + meanY * meanY)
        val slopeDay = if (flat) 0.0 else sxy / sxx
        val sse = if (flat) 0.0 else max(0.0, syy - slopeDay * sxy)
        val zeroResiduals = sse <= 1e-12 * max(1.0, syy)
        val residualSd = if (zeroResiduals) 0.0 else sqrt(sse / (n - 2))
        val r2 = when {
            flat -> 0.0
            zeroResiduals -> 1.0
            else -> (1.0 - sse / syy).coerceIn(0.0, 1.0)
        }
        val t = tCritical(n - 2)
        val slopeWeek = slopeDay * 7.0
        val margin = t * residualSd / sqrt(sxx) * 7.0
        val lastDay = xs.max()
        val firstDay = xs.min()
        return TrendFit(
            n = n,
            slopePerWeek = slopeWeek,
            slopeLow = slopeWeek - margin,
            slopeHigh = slopeWeek + margin,
            r2 = r2,
            fittedAtLast = meanY + slopeDay * (lastDay - meanX),
            spanDays = (lastDay - firstDay).toInt(),
            meanDay = meanX,
            sxx = sxx,
            residualSd = residualSd,
            tCritical = t,
            lastDay = lastDay,
        )
    }

    /**
     * NOT_ENOUGH_DATA below [minPoints] points or [minSpanDays] days (the fit is still filled when computable);
     * otherwise RISING if the slope's 95% interval is above 0, FALLING if below 0, else PLATEAU.
     */
    fun verdict(
        points: List<Pair<LocalDate, Double>>,
        minPoints: Int = MIN_POINTS,
        minSpanDays: Int = MIN_SPAN_DAYS,
    ): TrendResult {
        val fit = fit(points)
        val enough = fit != null && points.size >= minPoints && fit.spanDays >= minSpanDays
        val verdict = when {
            fit == null || !enough -> TrendVerdict.NOT_ENOUGH_DATA
            fit.slopeLow > 0.0 -> TrendVerdict.RISING
            fit.slopeHigh < 0.0 -> TrendVerdict.FALLING
            else -> TrendVerdict.PLATEAU
        }
        return TrendResult(verdict, fit, points.size, minPoints, minSpanDays)
    }

    /**
     * The fitted line [weeksAhead] weeks after the last date, with the 95% prediction interval
     * value +- t * residualSd * sqrt(1 + 1/n + (x0 - meanDay)^2 / sxx).
     */
    fun forecast(fit: TrendFit, weeksAhead: Double): Forecast {
        val x0 = fit.lastDay + weeksAhead * 7.0
        val value = fit.fittedAtLast + fit.slopePerWeek * weeksAhead
        val dx = x0 - fit.meanDay
        val factor = sqrt(1.0 + 1.0 / fit.n + (if (fit.sxx > 0.0) dx * dx / fit.sxx else 0.0))
        val margin = fit.tCritical * fit.residualSd * factor
        return Forecast(weeksAhead, value, value - margin, value + margin)
    }

    /** Series: estimated 1RM if any, else cardio speed (km/h), else the machine's default points. */
    fun forMachine(progress: MachineProgress): TrendResult {
        val series = when {
            progress.oneRmPoints.isNotEmpty() -> progress.oneRmPoints
            !progress.cardioPoints[ProgressMetric.SPEED].isNullOrEmpty() -> progress.cardioPoints.getValue(ProgressMetric.SPEED)
            else -> progress.points
        }
        return verdict(series.map { it.date to it.value })
    }

}
