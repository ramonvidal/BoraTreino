package com.boratreino.feature.progress

import com.boratreino.core.model.Consistency
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.MuscleRecency
import com.boratreino.core.model.MuscleWeek
import com.boratreino.core.model.Plateau
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import com.boratreino.core.model.RepRange
import com.boratreino.core.model.RepRangeShare
import com.boratreino.core.model.SessionTimes
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StrengthTrend
import com.boratreino.core.model.WeekStats
import java.time.LocalDate

/** Hand-made statistics reports for the Stats tests and screenshots. Today is 2 Nov 2026. */
internal object StatsSamples {
    val today: LocalDate = ProgressSamples.today

    /** Monday of the first of the 13 weeks (12 weeks before the week of 2 Nov). */
    val from: LocalDate = today.minusWeeks(12)

    private val legsPress = ProgressSamples.machine(1, "Leg press", MuscleGroup.LEGS)
    private val benchPress = ProgressSamples.machine(2, "Bench press", MuscleGroup.CHEST)
    private val latPulldown = ProgressSamples.machine(3, "Lat pulldown", MuscleGroup.BACK)
    private val shoulderPress = ProgressSamples.machine(4, "Shoulder press", MuscleGroup.SHOULDERS)
    private val bicepCurl = ProgressSamples.machine(5, "Bicep curl", MuscleGroup.ARMS)
    val treadmill = ProgressSamples.machine(6, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)

    /** Legs volume per week: sums to 12 400 kg ("12.4 t"). */
    private val legsVolume = listOf(800.0, 900.0, 1000.0, 1000.0, 1100.0, 1000.0, 900.0, 1000.0, 1000.0, 1100.0, 1000.0, 800.0, 800.0)

    /** Chest volume per week: sums to 8 200 kg ("8.2 t"). */
    private val chestVolume = List(12) { 600.0 } + 1000.0

    /** Back is trained in one week only: 850 kg (shown as "850 kg"). */
    private const val BACK_VOLUME = 850.0
    private const val BACK_WEEK = 6

    private fun weekStart(i: Int): LocalDate = from.plusWeeks(i.toLong())

    private val fullMuscleWeeks: List<MuscleWeek> = (0 until 13).flatMap { i ->
        buildList {
            add(MuscleWeek(weekStart(i), MuscleGroup.LEGS, 10, legsVolume[i]))
            add(MuscleWeek(weekStart(i), MuscleGroup.CHEST, 8, chestVolume[i]))
            if (i == BACK_WEEK) add(MuscleWeek(weekStart(i), MuscleGroup.BACK, 6, BACK_VOLUME))
        }
    }

    private val fullWeeks: List<WeekStats> = (0 until 13).map { i ->
        val volume = legsVolume[i] + chestVolume[i] + if (i == BACK_WEEK) BACK_VOLUME else 0.0
        val sets = 18 + if (i == BACK_WEEK) 6 else 0
        // Weeks 3 and 9 are missed (1 day); a few weeks hit 4 days.
        val days = when (i) {
            3, 9 -> 1
            2, 5, 8, 12 -> 4
            else -> 3
        }
        WeekStats(weekStart(i), sessions = days, trainingDays = days, workingSets = sets, volumeKg = volume)
    }

    /** Every group in enum order; CARDIO was never trained. */
    private val recency: List<MuscleRecency> = listOf(
        MuscleRecency(MuscleGroup.LEGS, today, 0),
        MuscleRecency(MuscleGroup.CHEST, today.minusDays(2), 2),
        MuscleRecency(MuscleGroup.BACK, today.minusDays(5), 5),
        MuscleRecency(MuscleGroup.SHOULDERS, today.minusDays(9), 9),
        MuscleRecency(MuscleGroup.ARMS, today.minusDays(1), 1),
        MuscleRecency(MuscleGroup.CORE, today.minusDays(30), 30),
        MuscleRecency(MuscleGroup.CARDIO, null, null),
    )

    private val consistency = Consistency(
        weeksCounted = 12,
        weeksMetGoal = 9,
        goal = 4,
        sessionsPerWeek = 2.8,
        bestWeekStart = LocalDate.of(2026, 10, 12),
        bestWeekTrainingDays = 4,
    )

    val strength: List<StrengthTrend> = listOf(
        StrengthTrend(benchPress, before = 60.0, after = 66.0, changeKg = 6.0, changePercent = 10.0),
        StrengthTrend(legsPress, before = 95.0, after = 100.0, changeKg = 5.0, changePercent = 5.3),
        StrengthTrend(latPulldown, before = 70.0, after = 70.0, changeKg = 0.0, changePercent = 0.0),
    )

    val plateaus: List<Plateau> = listOf(Plateau(latPulldown, sessionsWithoutImprovement = 5, bestEstimate = 70.0))

    val records: List<RecordEvent> = listOf(
        RecordEvent(legsPress, LocalDate.of(2026, 10, 30), Record.HeaviestWeight(100.0)),
        RecordEvent(shoulderPress, LocalDate.of(2026, 10, 26), Record.MostReps(weightKg = 40.0, reps = 12)),
        RecordEvent(benchPress, LocalDate.of(2026, 10, 19), Record.HeaviestWeight(60.0)),
        RecordEvent(bicepCurl, LocalDate.of(2026, 10, 5), Record.MostReps(weightKg = 20.0, reps = 15)),
        RecordEvent(latPulldown, LocalDate.of(2026, 9, 14), Record.HeaviestWeight(65.0)),
    )

    val repRanges: List<RepRangeShare> = listOf(
        RepRangeShare(RepRange.R1_5, 12, 10.0),
        RepRangeShare(RepRange.R6_8, 36, 30.0),
        RepRangeShare(RepRange.R9_12, 54, 45.0),
        RepRangeShare(RepRange.R13_PLUS, 18, 15.0),
    )

    val noRepRanges: List<RepRangeShare> = RepRange.entries.map { RepRangeShare(it, 0, 0.0) }

    /** 13 weeks, three muscle groups with volume, strength trends with a plateau, 5 records. */
    val full = StatsReport(
        period = StatsPeriod.THREE_MONTHS,
        from = from,
        to = today,
        weeks = fullWeeks,
        muscleWeeks = fullMuscleWeeks,
        recency = recency,
        consistency = consistency,
        strength = strength,
        overallStrengthPercent = 6.4,
        records = records,
        plateaus = plateaus,
        repRanges = repRanges,
        times = SessionTimes(sessionsCounted = 30, averageMinutes = 62, averageMinutesBetweenSets = 3.1),
    )

    private fun emptyWeeks(count: Int) = (0 until count).map { WeekStats(today.minusWeeks((count - 1 - it).toLong()), 0, 0, 0, 0.0) }

    private val neverRecency = MuscleGroup.entries.map { MuscleRecency(it, null, null) }

    /** Nothing logged in the period. */
    val empty = StatsReport(
        period = StatsPeriod.FOUR_WEEKS,
        from = today.minusWeeks(3),
        to = today,
        weeks = emptyWeeks(4),
        muscleWeeks = emptyList(),
        recency = neverRecency,
        consistency = Consistency(3, 0, 4, 0.0, null, 0),
        strength = emptyList(),
        overallStrengthPercent = null,
        records = emptyList(),
        plateaus = emptyList(),
        repRanges = noRepRanges,
        times = SessionTimes(0, null, null),
    )

    /** Only cardio sessions: sessions and training days but no working (strength) sets. */
    val cardioOnly = StatsReport(
        period = StatsPeriod.FOUR_WEEKS,
        from = today.minusWeeks(3),
        to = today,
        weeks = (0 until 4).map { WeekStats(today.minusWeeks((3 - it).toLong()), sessions = 2, trainingDays = 2, workingSets = 0, volumeKg = 0.0) },
        muscleWeeks = emptyList(),
        recency = MuscleGroup.entries.map { if (it == MuscleGroup.CARDIO) MuscleRecency(it, today.minusDays(1), 1) else MuscleRecency(it, null, null) },
        consistency = Consistency(3, 0, 4, 2.0, today.minusWeeks(1), 2),
        strength = emptyList(),
        overallStrengthPercent = null,
        records = emptyList(),
        plateaus = emptyList(),
        repRanges = noRepRanges,
        times = SessionTimes(8, 40, null),
    )

    fun state(report: StatsReport = full, period: StatsPeriod = report.period) =
        StatsUiState(loaded = true, period = period, report = report, weeklyGoal = 4)
}
