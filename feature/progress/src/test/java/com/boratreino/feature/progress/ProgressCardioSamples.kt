package com.boratreino.feature.progress

import com.boratreino.core.model.CardioWeek
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MachineProgress
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ProgressMetric
import com.boratreino.core.model.ProgressOverview
import com.boratreino.feature.progress.ProgressSamples.date
import java.time.LocalDate

/** Fixed cardio data for the Progress tests and screenshots. Today is Monday 2 Nov 2026, so weeks start on Mondays. */
internal object ProgressCardioSamples {
    val treadmill = ProgressSamples.machine(30, "Treadmill", MuscleGroup.CARDIO, MachineKind.CARDIO)

    /** The Monday of the week [weeksAgo] weeks before today's. */
    fun weekStart(weeksAgo: Int): LocalDate = ProgressSamples.today.minusWeeks(weeksAgo.toLong())

    /** Twelve weeks, oldest first, the last one being this week (45 min, 320 kcal, 3.2 km, 2 days). */
    val twelveWeeks: List<CardioWeek> = listOf(
        Triple(0, 0, 0) to 0,
        Triple(60, 420, 6000) to 2,
        Triple(90, 640, 9000) to 3,
        Triple(0, 0, 0) to 0,
        Triple(120, 850, 12000) to 3,
        Triple(150, 1000, 15000) to 4,
        Triple(135, 950, 13500) to 3,
        Triple(180, 1200, 18000) to 4,
        Triple(160, 1100, 16000) to 4,
        Triple(200, 1400, 20000) to 5,
        Triple(170, 1180, 17000) to 4,
        Triple(45, 320, 3200) to 2,
    ).mapIndexed { index, (totals, sessions) ->
        CardioWeek(
            weekStart = weekStart(11 - index),
            minutes = totals.first,
            calories = totals.second,
            distanceM = totals.third,
            sessions = sessions,
        )
    }

    /** All zero: a cardio machine that exists but has not been used in the last 12 weeks. */
    val emptyWeeks: List<CardioWeek> = (11 downTo 0).map { CardioWeek(weekStart(it), 0, 0, 0, 0) }

    /** Eight sessions from 3 Sep, minutes per session rising from 20 to 45. */
    val treadmillHistory = MachineProgress(
        treadmill,
        ProgressMetric.MINUTES,
        ProgressSamples.points(
            700,
            listOf(
                date(9, 3) to 20.0,
                date(9, 10) to 25.0,
                date(9, 24) to 30.0,
                date(10, 1) to 30.0,
                date(10, 13) to 35.0,
                date(10, 20) to 40.0,
                date(10, 28) to 40.0,
                date(11, 2) to 45.0,
            ),
            ProgressMetric.MINUTES,
        ),
        weeks = twelveWeeks,
    )

    /** The weight overview plus the treadmill, with this week's cardio card. */
    val overview = ProgressOverview(
        machines = listOf(treadmillHistory) + ProgressSamples.overview.machines,
        trainingsThisMonth = 9,
        recordsThisMonth = 4,
        cardioThisWeek = twelveWeeks.last(),
    )
}
