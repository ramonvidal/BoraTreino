package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.MeasurementRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.Machine
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.RoutinePlan
import com.boratreino.core.model.RoutinePlanWithRoutines
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.StatsReport
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.TrainingSettings
import com.boratreino.core.testing.answering
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf

/** The clock of the Stats tests: 2 Nov 2026 at noon UTC, the day [StatsSamples] is built for. */
internal val statsTestClock: Clock = Clock.fixed(Instant.parse("2026-11-02T12:00:00Z"), ZoneOffset.UTC)

/**
 * A Stats view model over fake repositories: [report] for every period and goal, a weekly goal of 4, no body data,
 * no measurements, no training dates and no plan.
 */
internal fun statsViewModel(
    handle: SavedStateHandle = SavedStateHandle(),
    report: StatsReport = StatsSamples.full,
): StatsViewModel = StatsViewModel(
    answering<StatsRepository>("observeStats" to flowOf(report)),
    answering<TrainingSettings>("weeklyGoal" to flowOf(4)),
    answering<BodySettings>(
        "profile" to flowOf<BodyProfile?>(null),
        "weights" to flowOf(emptyList<WeightEntry>()),
    ),
    handle,
    answering<MeasurementRepository>("observeAll" to flowOf(emptyList<BodyMeasurement>())),
    answering<HistoryRepository>(
        "observeTrainingDates" to flowOf(emptySet<LocalDate>()),
        "observeDay" to flowOf(emptyList<SessionDetail>()),
    ),
    answering<MachineRepository>("observeMachines" to flowOf(emptyList<Machine>())),
    answering<RoutineRepository>(
        "observePlanned" to flowOf(emptyList<PlannedWorkout>()),
        "observePlan" to flowOf(RoutinePlanWithRoutines(RoutinePlan(1L, ScheduleMode.IN_TURN), emptyList())),
        "observeLastRoutineId" to flowOf<Long?>(null),
    ),
    statsTestClock,
)
