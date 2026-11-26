package com.boratreino.feature.progress

import com.boratreino.core.model.AcwrStatus
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.CardioBlock
import com.boratreino.core.model.CardioInsight
import com.boratreino.core.model.Forecast
import com.boratreino.core.model.MachineTrend
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.Muscle
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleVolume
import com.boratreino.core.model.PatternBucket
import com.boratreino.core.model.PatternReport
import com.boratreino.core.model.TrendFit
import com.boratreino.core.model.TrendResult
import com.boratreino.core.model.TrendVerdict
import com.boratreino.core.model.VolumeBand
import com.boratreino.core.model.WorkloadReport
import java.time.LocalDate
import java.util.Locale

/** Hand-made trends, loads, patterns and measurements for the phase 46 card tests and screenshots. Today is 2 Nov 2026. */
internal object Phase46Samples {
    /** One decimal, as the card tests read the numbers: "+1.2", "92.0". */
    val oneDecimal: (Double) -> String = { String.format(Locale.US, "%.1f", it) }

    /** A fit over [n] points with the given slope and 95% range; the other fields only have to be plausible. */
    fun fit(n: Int, slope: Double, low: Double, high: Double, r2: Double = 0.82): TrendFit = TrendFit(
        n = n,
        slopePerWeek = slope,
        slopeLow = low,
        slopeHigh = high,
        r2 = r2,
        fittedAtLast = 0.0,
        spanDays = 35,
        meanDay = 0.0,
        sxx = 1.0,
        residualSd = 0.5,
        tCritical = 2.57,
    )

    private fun trendResult(verdict: TrendVerdict, fit: TrendFit?, points: Int = 6) = TrendResult(
        verdict = verdict,
        fit = fit,
        points = points,
        neededPoints = 5,
        neededSpanDays = 14,
    )

    /** Rising: +1.2 kg per week, 95% from +0.4 to +2.0, six sessions. */
    val rising: TrendResult = trendResult(TrendVerdict.RISING, fit(6, 1.2, 0.4, 2.0))

    /** Plateau: the range includes zero. */
    val plateau: TrendResult = trendResult(TrendVerdict.PLATEAU, fit(6, 0.0, -0.3, 0.3, r2 = 0.01))

    /** Falling: -0.8 kg per week, 95% from -1.1 to -0.5. */
    val falling: TrendResult = trendResult(TrendVerdict.FALLING, fit(6, -0.8, -1.1, -0.5))

    /** Three sessions: nothing to fit yet. */
    val notEnough: TrendResult = trendResult(TrendVerdict.NOT_ENOUGH_DATA, null, points = 3)

    val risingForecast = Forecast(weeksAhead = 4.0, value = 92.0, low = 88.0, high = 96.0)
    val fallingForecast = Forecast(weeksAhead = 4.0, value = 84.0, low = 80.0, high = 88.0)

    /** A machine's trend: a [verdict] with [slope] per week (ignored for not enough data). */
    fun machineTrend(
        id: Long,
        name: String,
        verdict: TrendVerdict,
        slope: Double = 0.0,
        group: MuscleGroup = MuscleGroup.CHEST,
    ): MachineTrend {
        val trend = if (verdict == TrendVerdict.NOT_ENOUGH_DATA) {
            notEnough
        } else {
            trendResult(verdict, fit(6, slope, slope - 0.2, slope + 0.2))
        }
        return MachineTrend(id, name, group, trend)
    }

    /** Two rising, one plateau, one falling and one machine that needs more data. */
    val machineTrends: List<MachineTrend> = listOf(
        machineTrend(1, "Bench press", TrendVerdict.RISING, 1.5, MuscleGroup.CHEST),
        machineTrend(2, "Leg press", TrendVerdict.RISING, 0.9, MuscleGroup.LEGS),
        machineTrend(3, "Lat pulldown", TrendVerdict.PLATEAU, 0.0, MuscleGroup.BACK),
        machineTrend(4, "Bicep curl", TrendVerdict.FALLING, -0.4, MuscleGroup.ARMS),
        machineTrend(5, "Cable row", TrendVerdict.NOT_ENOUGH_DATA, group = MuscleGroup.BACK),
    )

    /** One muscle per band: none, below 10, in range and above 20 sets a week. */
    val volumes: List<MuscleVolume> = listOf(
        MuscleVolume(Muscle.CHEST, 0.0, VolumeBand.NONE),
        MuscleVolume(Muscle.BICEPS, 4.0, VolumeBand.BELOW),
        MuscleVolume(Muscle.QUADRICEPS, 14.0, VolumeBand.IN_RANGE),
        MuscleVolume(Muscle.UPPER_BACK, 24.0, VolumeBand.ABOVE),
    )

    /** A training load with the given [status]; the numbers are the same for every status. */
    fun workload(
        status: AcwrStatus,
        acwr: Double? = 1.14,
        historyDays: Int = 40,
    ) = WorkloadReport(
        acuteSets = 12,
        chronicWeeklyAverage = 10.5,
        acwr = acwr,
        status = status,
        historyDays = historyDays,
    )

    /** Weekday buckets with one that passed the test (Monday) and the others untested or not significant. */
    private val weekday = listOf(
        PatternBucket(1, 6, 2.4, 1.1, tValue = 2.18, significant = true, pValue = 0.012),
        PatternBucket(3, 2, -3.0, null),
        PatternBucket(5, 6, 4.0, 2.0, tValue = 2.0, significant = false, pValue = 0.10),
        PatternBucket(6, 3, -1.5, 1.2),
    )

    private val rest = listOf(
        PatternBucket(0, 5, 3.0, 1.5, tValue = 2.0, significant = false, pValue = 0.12),
        PatternBucket(1, 3, -1.0, 2.0),
        PatternBucket(2, 1, -4.0, null),
    )

    /** The same buckets with nothing that passed the test. */
    private val weekdayNoneSignificant = weekday.map { it.copy(significant = false) }
    private val restNoneSignificant = rest.map { it.copy(significant = false) }

    /**
     * Weekday and rest buckets. Buckets with fewer than five sessions are untested (no p-value); [anySignificant]
     * false gives the "no clear pattern" report.
     */
    fun patterns(sessionsUsed: Int = 18, enough: Boolean = true, anySignificant: Boolean = true): PatternReport =
        if (anySignificant) {
            PatternReport(weekday, rest, sessionsUsed, enough, anySignificant = true)
        } else {
            PatternReport(weekdayNoneSignificant, restNoneSignificant, sessionsUsed, enough, anySignificant = false)
        }

    /** Five weekly waist to hip ratios falling by 0.01 a week. */
    val waistHipWeekly: List<Pair<LocalDate, Double>> = listOf(
        ProgressSamples.date(9, 7) to 0.90,
        ProgressSamples.date(9, 14) to 0.89,
        ProgressSamples.date(9, 21) to 0.88,
        ProgressSamples.date(9, 28) to 0.87,
        ProgressSamples.date(10, 5) to 0.86,
    )

    /** Waist measurements, one a week from 3 Aug 2026, in the order given. */
    fun waist(vararg cm: Double): List<BodyMeasurement> = cm.mapIndexed { index, value ->
        BodyMeasurement(
            id = index + 1L,
            date = LocalDate.of(2026, 8, 3).plusWeeks(index.toLong()),
            type = MeasurementType.WAIST,
            valueCm = value,
        )
    }

    /** Three cardio weeks. */
    val cardioBlocks: List<CardioBlock> = listOf(
        CardioBlock(LocalDate.of(2026, 10, 5), 90, 22_000, 2, 11.0, 23.4),
        CardioBlock(LocalDate.of(2026, 10, 12), 135, 40_000, 2, 12.0, 24.1),
        CardioBlock(LocalDate.of(2026, 10, 19), 240, 52_400, 3, 13.0, 24.8),
    )

    /** Twelve sessions: speed rising, efficiency flat, latest 1.85 and best 2.1 km/h per level. */
    val cardioInsight = CardioInsight(
        sessions = 12,
        speedTrend = trendResult(TrendVerdict.RISING, fit(12, 0.25, 0.1, 0.4), points = 12),
        efficiencyTrend = trendResult(TrendVerdict.PLATEAU, fit(12, 0.0, -0.05, 0.05), points = 12),
        efficiencyLatest = 1.85,
        efficiencyBest = 2.1,
    )
}
