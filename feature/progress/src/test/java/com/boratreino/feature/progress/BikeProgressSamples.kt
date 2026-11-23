package com.boratreino.feature.progress

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import java.time.LocalDate
import java.time.ZoneOffset

/** A bike with two sessions (6 and 20 Oct 2026) and the bike console values, for the Progress tests (phase 42). */
internal object BikeProgressSamples {
    val bike = Machine(40L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val earlier = LocalDate.of(2026, 10, 6)
    private val later = LocalDate.of(2026, 10, 20)

    private fun point(sessionId: Long, date: LocalDate, value: Double) = SessionPoint(
        sessionId = sessionId,
        date = date,
        value = value,
        topSet = WorkoutSet(
            id = sessionId,
            sessionId = sessionId,
            machineId = bike.id,
            position = 0,
            weightKg = null,
            reps = null,
            seconds = 1200,
            calories = 180,
            distanceM = 5000,
            loggedAt = date.atStartOfDay(ZoneOffset.UTC).toInstant(),
        ),
        setCount = 1,
    )

    /** Two points for one measure: the earlier session's value, then the later one's. */
    private fun twoSessions(first: Double, second: Double) = listOf(
        point(1L, earlier, first),
        point(2L, later, second),
    )

    private val every: Map<ProgressMetric, List<SessionPoint>> = mapOf(
        ProgressMetric.SPEED to twoSessions(14.0, 15.0),
        ProgressMetric.POWER to twoSessions(150.0, 160.0),
        ProgressMetric.INTENSITY_FACTOR to twoSessions(0.75, 0.79),
        ProgressMetric.WATTS_PER_KG to twoSessions(2.2, 2.4),
        ProgressMetric.TSS to twoSessions(40.0, 48.0),
        ProgressMetric.HEART_RATE to twoSessions(138.0, 142.0),
        ProgressMetric.INTENSITY to twoSessions(10.0, 12.0),
        ProgressMetric.DISTANCE to twoSessions(4.8, 5.0),
        ProgressMetric.MINUTES to twoSessions(20.0, 20.0),
    )

    /** Every cardio measure has data, so every chip shows. The chart starts on speed, the default with distance. */
    val allChips: MachineProgress = MachineProgress(
        machine = bike,
        metric = ProgressMetric.SPEED,
        points = every.getValue(ProgressMetric.SPEED),
        cardioPoints = every,
    )

    /** Power, IF, TSS and time have data; speed, W/kg, heart rate, intensity and distance have none. */
    val someChips: MachineProgress = run {
        val withData = listOf(
            ProgressMetric.POWER,
            ProgressMetric.INTENSITY_FACTOR,
            ProgressMetric.TSS,
            ProgressMetric.MINUTES,
        ).associateWith { every.getValue(it) }
        MachineProgress(
            machine = bike,
            metric = ProgressMetric.POWER,
            points = withData.getValue(ProgressMetric.POWER),
            cardioPoints = CARDIO_METRICS.associateWith { withData[it] ?: emptyList() },
        )
    }
}
