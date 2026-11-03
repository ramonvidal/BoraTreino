package com.boratreino.feature.progress

import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import java.time.Instant
import java.time.LocalDate

/** Fixed gym data for the Progress tests and screenshots. Today is 2 Nov 2026. */
internal object ProgressSamples {
    val today: LocalDate = LocalDate.of(2026, 11, 2)

    fun date(month: Int, day: Int, year: Int = 2026): LocalDate = LocalDate.of(year, month, day)

    fun machine(
        id: Long,
        name: String,
        group: MuscleGroup,
        kind: MachineKind = MachineKind.WEIGHT_REPS,
    ): Machine = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = kind,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    /**
     * A session's top point: a weight session is [value] kg x 8, a reps session is [value] reps.
     * [setCount] is the number of sets in the session.
     */
    fun point(
        sessionId: Long,
        date: LocalDate,
        value: Double,
        metric: ProgressMetric = ProgressMetric.WEIGHT,
        setCount: Int = 3,
    ): SessionPoint {
        val topSet = when (metric) {
            ProgressMetric.WEIGHT -> WorkoutSet(
                id = sessionId,
                sessionId = sessionId,
                machineId = 0,
                position = 1,
                weightKg = value,
                reps = 8,
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
            ProgressMetric.REPS -> WorkoutSet(
                id = sessionId,
                sessionId = sessionId,
                machineId = 0,
                position = 1,
                weightKg = null,
                reps = value.toInt(),
                seconds = null,
                calories = null,
                distanceM = null,
                loggedAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
            ProgressMetric.MINUTES -> WorkoutSet(
                id = sessionId,
                sessionId = sessionId,
                machineId = 0,
                position = 1,
                weightKg = null,
                reps = null,
                seconds = value.toInt() * 60,
                calories = null,
                distanceM = null,
                loggedAt = Instant.parse("2026-01-01T10:00:00Z"),
            )
        }
        return SessionPoint(sessionId, date, value, topSet, setCount)
    }

    /** Points from (date, value) rows, with session ids counting up from [firstSessionId]. */
    fun points(
        firstSessionId: Long,
        rows: List<Pair<LocalDate, Double>>,
        metric: ProgressMetric = ProgressMetric.WEIGHT,
    ): List<SessionPoint> = rows.mapIndexed { index, (date, value) ->
        point(firstSessionId + index, date, value, metric)
    }

    private val legPressOlder = listOf(
        date(11, 10, 2025) to 80.0,
        date(12, 15, 2025) to 82.5,
        date(1, 20) to 85.0,
        date(2, 24) to 87.5,
        date(3, 31) to 90.0,
        date(5, 5) to 92.5,
        date(6, 9) to 95.0,
        date(7, 14) to 97.5,
    )

    /** Fourteen sessions from 3 Sep to 2 Nov, rising from 100 to 125 kg. */
    private val legPressRecent = listOf(
        date(9, 3) to 100.0,
        date(9, 7) to 100.0,
        date(9, 11) to 102.5,
        date(9, 16) to 102.5,
        date(9, 21) to 105.0,
        date(9, 25) to 105.0,
        date(10, 1) to 107.5,
        date(10, 6) to 110.0,
        date(10, 12) to 110.0,
        date(10, 17) to 112.5,
        date(10, 22) to 115.0,
        date(10, 27) to 117.5,
        date(10, 30) to 120.0,
        date(11, 2) to 125.0,
    )

    val legPress: Machine = machine(1, "Leg press", MuscleGroup.LEGS)
    val legExtension: Machine = machine(2, "Leg extension", MuscleGroup.LEGS)
    val latPulldown: Machine = machine(3, "Lat pulldown", MuscleGroup.BACK)
    val seatedRow: Machine = machine(4, "Seated row", MuscleGroup.BACK)
    val pullUps: Machine = machine(5, "Pull-ups", MuscleGroup.BACK, MachineKind.BODYWEIGHT_REPS)
    val chestPress: Machine = machine(6, "Chest press", MuscleGroup.CHEST)

    /** Leg press with a year of history. */
    val legPressHistory = MachineProgress(
        legPress,
        ProgressMetric.WEIGHT,
        points(100, legPressOlder + legPressRecent),
    )

    val legExtensionHistory = MachineProgress(
        legExtension,
        ProgressMetric.WEIGHT,
        points(200, listOf(date(6, 10) to 40.0, date(7, 1) to 42.5, date(9, 15) to 42.5)),
    )

    val latPulldownHistory = MachineProgress(
        latPulldown,
        ProgressMetric.WEIGHT,
        points(300, listOf(
            date(9, 4) to 45.0,
            date(9, 14) to 47.5,
            date(9, 25) to 50.0,
            date(10, 2) to 50.0,
            date(10, 9) to 52.5,
            date(10, 20) to 52.5,
            date(10, 31) to 55.0,
        )),
    )

    val seatedRowHistory = MachineProgress(
        seatedRow,
        ProgressMetric.WEIGHT,
        points(400, listOf(
            date(9, 5) to 45.0,
            date(9, 19) to 45.0,
            date(10, 3) to 42.5,
            date(10, 27) to 42.5,
        )),
    )

    val chestPressHistory = MachineProgress(
        chestPress,
        ProgressMetric.WEIGHT,
        points(500, listOf(
            date(9, 8) to 60.0,
            date(9, 22) to 60.0,
            date(10, 6) to 57.5,
            date(10, 24) to 60.0,
        )),
    )

    /** Reps only: the best is 12 on 30 Oct. */
    val pullUpsHistory = MachineProgress(
        pullUps,
        ProgressMetric.REPS,
        points(600, listOf(
            date(9, 2) to 8.0,
            date(9, 9) to 9.0,
            date(9, 16) to 9.0,
            date(9, 23) to 9.0,
            date(10, 1) to 9.0,
            date(10, 7) to 10.0,
            date(10, 14) to 10.0,
            date(10, 21) to 11.0,
            date(10, 28) to 11.0,
            date(10, 30) to 12.0,
        ), ProgressMetric.REPS),
    )

    /** Machines in last-trained-first order, as the repository gives them. */
    val overview = ProgressOverview(
        machines = listOf(
            legPressHistory,
            latPulldownHistory,
            pullUpsHistory,
            seatedRowHistory,
            chestPressHistory,
            legExtensionHistory,
        ),
        trainingsThisMonth = 9,
        recordsThisMonth = 4,
    )
}
