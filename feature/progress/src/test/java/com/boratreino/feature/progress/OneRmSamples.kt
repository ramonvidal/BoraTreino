package com.boratreino.feature.progress

import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.OneRepMaxRules
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.SessionPoint
import com.boratreino.core.model.WorkoutSet
import com.boratreino.feature.progress.ProgressSamples.date
import java.time.Instant
import java.time.LocalDate

/** Estimated 1RM data for the tests: each session is one weight x reps set with its Epley estimate. */
internal object OneRmSamples {
    /** (date, weight, reps) rows. */
    fun oneRmPoints(firstSessionId: Long, rows: List<Triple<LocalDate, Double, Int>>): List<SessionPoint> =
        rows.mapIndexed { index, (day, weight, reps) ->
            val id = firstSessionId + index
            val set = WorkoutSet(id, id, 0, 1, weight, reps, null, null, null, Instant.parse("2026-01-01T10:00:00Z"))
            SessionPoint(id, day, OneRepMaxRules.estimate(weight, reps)!!, set, 3)
        }

    /** Top weight rises 100 -> 120 while the estimate follows the reps: 80 x 8 is the first estimate (101.33). */
    val rows = listOf(
        Triple(date(9, 3), 80.0, 8),
        Triple(date(9, 17), 85.0, 8),
        Triple(date(10, 1), 90.0, 6),
        Triple(date(10, 15), 100.0, 3),
        Triple(date(10, 29), 95.0, 8),
        Triple(date(11, 2), 100.0, 5),
    )

    val legPress = ProgressSamples.legPress

    /** Leg press with both views available. */
    val withOneRm = MachineProgress(
        legPress,
        ProgressMetric.WEIGHT,
        ProgressSamples.points(
            100,
            listOf(
                date(9, 3) to 80.0,
                date(9, 17) to 85.0,
                date(10, 1) to 90.0,
                date(10, 15) to 100.0,
                date(10, 29) to 95.0,
                date(11, 2) to 100.0,
            ),
        ),
        oneRmPoints = oneRmPoints(100, rows),
    )

    /** Only warm-ups or high-rep sets: no estimate. */
    val withoutEligibleSets = MachineProgress(
        ProgressSamples.legExtension,
        ProgressMetric.WEIGHT,
        ProgressSamples.legExtensionHistory.points,
        oneRmPoints = emptyList(),
    )
}
