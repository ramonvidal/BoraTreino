package com.boratreino.feature.progress

import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.BodySnapshot
import com.boratreino.core.model.Finding
import com.boratreino.core.model.FrequencySummary
import com.boratreino.core.model.HabitSummary
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OverviewRules
import com.boratreino.core.model.Record
import com.boratreino.core.model.RecordEvent
import com.boratreino.core.model.StallDirection
import com.boratreino.core.model.StallHint
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.StrengthDirection
import com.boratreino.core.model.StrengthRow
import com.boratreino.core.model.StrengthSummary
import com.boratreino.core.model.SuspiciousJump
import com.boratreino.core.model.WeekCount
import com.boratreino.core.model.WorkoutSet
import java.time.Instant
import java.time.LocalDate

/**
 * Overview data in the shape of the owner's mock-up. Today is 2 Nov 2026. The findings are the owner-like three:
 * shoulders at 4 sets a week, a lateral raise stuck for 4 trainings, and a calf raise record with 10 others. The
 * Biceps curl jump is flagged (20 kg to 35 kg in 3 days) and is only under "Check this value".
 */
internal object OverviewSamples {
    val today: LocalDate = StatsSamples.today

    private val legPress = ProgressSamples.machine(1, "Leg press", MuscleGroup.LEGS)
    private val latPulldown = ProgressSamples.machine(3, "Lat pulldown", MuscleGroup.BACK)
    private val bicepCurl = ProgressSamples.machine(5, "Biceps curl", MuscleGroup.ARMS)
    private val lateralRaise = ProgressSamples.machine(7, "Lateral raise", MuscleGroup.SHOULDERS)
    private val calfRaise = ProgressSamples.machine(8, "Calf raise", MuscleGroup.LEGS)
    private val legExtension = ProgressSamples.machine(9, "Leg extension", MuscleGroup.LEGS)

    private fun topSet(machineId: Long, weightKg: Double, reps: Int) = WorkoutSet(
        id = machineId,
        sessionId = machineId,
        machineId = machineId,
        position = 0,
        weightKg = weightKg,
        reps = reps,
        seconds = null,
        calories = null,
        distanceM = null,
        loggedAt = Instant.parse("2026-11-02T10:00:00Z"),
    )

    val lowVolume = Finding.LowVolume(MuscleGroup.SHOULDERS, setsPerWeek = 4.0, routine = null)

    val stalledFlat = Finding.Stalled(
        machine = lateralRaise,
        direction = StallDirection.FLAT,
        sessions = 4,
        lastTop = topSet(7L, 12.0, 12),
        bestEstimate = 16.4,
        lastEstimate = 16.4,
        hint = StallHint.MoreReps(14),
    )

    val newRecord = Finding.NewRecord(
        event = RecordEvent(calfRaise, today.minusDays(1), Record.HeaviestWeight(60.0)),
        others = 10,
    )

    val frequencyDrop = Finding.FrequencyDrop(recentPerWeek = 1.5, usualPerWeek = 3.5, goal = 4)

    val stalledFalling = Finding.Stalled(
        machine = legExtension,
        direction = StallDirection.FALLING,
        sessions = 3,
        lastTop = topSet(9L, 40.0, 10),
        bestEstimate = 60.0,
        lastEstimate = 54.0,
        hint = null,
    )

    /** The three findings of the owner's case, in the order the rules pick them. */
    val ownerLike: List<Finding> = listOf(lowVolume, stalledFlat, newRecord)

    val frequency = FrequencySummary(
        thisWeek = 2,
        goal = 4,
        weeks = listOf(
            WeekCount(LocalDate.of(2026, 10, 12), days = 3, isCurrent = false),
            WeekCount(LocalDate.of(2026, 10, 19), days = 4, isCurrent = false),
            WeekCount(LocalDate.of(2026, 10, 26), days = 1, isCurrent = false),
            WeekCount(LocalDate.of(2026, 11, 1), days = 2, isCurrent = true),
        ),
        streak = 2,
        next = null,
    )

    val strength = StrengthSummary(
        risers = listOf(
            StrengthRow(
                machine = legPress,
                before = 95.0,
                after = 100.0,
                changePercent = 5.3,
                direction = StrengthDirection.RISING,
                sparkline = listOf(95.0, 100.0),
                sessionsInPeriod = 2,
                lastTop = topSet(1L, 100.0, 5),
            ),
        ),
        flatOrFalling = listOf(
            StrengthRow(
                machine = latPulldown,
                before = 70.0,
                after = 70.0,
                changePercent = 0.0,
                direction = StrengthDirection.FLAT,
                sparkline = listOf(70.0, 70.0),
                sessionsInPeriod = 2,
                lastTop = topSet(3L, 70.0, 10),
            ),
        ),
        toCheck = listOf(
            SuspiciousJump(bicepCurl, fromDate = today.minusDays(3), toDate = today, fromKg = 20.0, toKg = 35.0),
        ),
    )

    private val waist = BodyMeasurement(id = 1, date = today.minusDays(2), type = MeasurementType.WAIST, valueCm = 82.5)

    val measurements: List<BodyMeasurement> = listOf(waist)

    val body = OverviewRules.body(measurements)

    val habit = HabitSummary(
        since = today.minusDays(27),
        cardioSessions = 3,
        cardioMinutes = 75,
        cardioDistanceM = 0,
        averageSessionMinutes = 60,
        minutesBetweenSets = 3.0,
    )

    /** The full report, with the period the Overview shows by default. */
    val report: StatsReport = StatsSamples.full.copy(period = StatsPeriod.FOUR_WEEKS)

    /** The Overview state of the owner's case: the three findings, every section filled. */
    fun state(
        findings: List<Finding> = ownerLike,
        report: StatsReport = this.report,
        frequency: FrequencySummary? = this.frequency,
        strength: StrengthSummary = this.strength,
        body: BodySnapshot = this.body,
        habit: HabitSummary? = this.habit,
        measurements: List<BodyMeasurement> = this.measurements,
    ): StatsUiState = StatsUiState(
        loaded = true,
        period = StatsPeriod.FOUR_WEEKS,
        report = report,
        weeklyGoal = 4,
        findings = findings,
        frequency = frequency,
        strengthSummary = strength,
        body = body,
        habit = habit,
        measurements = measurements,
    )
}
