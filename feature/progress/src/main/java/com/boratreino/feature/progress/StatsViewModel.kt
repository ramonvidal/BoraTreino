package com.boratreino.feature.progress

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.MachineRepository
import com.boratreino.core.data.MeasurementRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.data.StatsRepository
import com.boratreino.core.model.BodyMeasurement
import com.boratreino.core.model.MeasurementType
import com.boratreino.core.model.FrequencyRules
import com.boratreino.core.model.InsightRules
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.OverviewRules
import com.boratreino.core.model.RotationProjection
import com.boratreino.core.model.RoutineWithItems
import com.boratreino.core.model.StatsPeriod
import com.boratreino.core.model.StrengthSummaryRules
import com.boratreino.core.model.ProjectedWorkout
import com.boratreino.core.model.WeightEntry
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PERIOD_KEY = "statsPeriod"

private data class StatsInputs(
    val period: StatsPeriod,
    val goal: Int,
    val body: Pair<Double?, List<WeightEntry>>,
    val measurements: List<BodyMeasurement>,
)

/** What the Overview needs besides the report: training dates, machine groups, the plan and the next workout. */
private data class OverviewInputs(
    val trainingDates: Set<LocalDate>,
    val machineGroups: Map<Long, MuscleGroup>,
    val routines: List<RoutineWithItems>,
    val next: ProjectedWorkout?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    stats: StatsRepository,
    settings: TrainingSettings,
    body: BodySettings,
    private val savedState: SavedStateHandle,
    private val measurements: MeasurementRepository,
    history: HistoryRepository,
    machines: MachineRepository,
    routines: RoutineRepository,
    private val clock: Clock,
) : ViewModel() {

    private val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    /** The next workout, built the way the Calendar does it. */
    private val plan: Flow<Pair<List<RoutineWithItems>, ProjectedWorkout?>> = combine(
        routines.observePlanned(),
        routines.observePlan(),
        routines.observeLastRoutineId(),
        history.observeDay(LocalDate.now(clock)),
    ) { plannedList, plan, lastRoutineId, todaySessions ->
        val today = LocalDate.now(clock)
        val next = RotationProjection.project(
            today = today,
            routines = plan.routines.map { it.routine },
            lastRoutineId = lastRoutineId,
            trainingDays = plan.plan.trainingDays,
            planned = plannedList.filter { !it.date.isBefore(today) },
            trainedToday = todaySessions.any { it.session.endedAt != null },
        ).firstOrNull()
        plan.routines to next
    }

    private val overviewInputs: Flow<OverviewInputs> = combine(
        history.observeTrainingDates(),
        machines.observeMachines(includeArchived = true),
        plan,
    ) { dates, all, (planRoutines, next) ->
        OverviewInputs(dates, all.associate { it.id to it.muscleGroup }, planRoutines, next)
    }

    // Kept as the enum's name in the saved state so the choice survives rotation and process death.
    private val period: StateFlow<StatsPeriod> = savedState.getStateFlow(PERIOD_KEY, StatsPeriod.FOUR_WEEKS.name)
        .map { name -> StatsPeriod.entries.firstOrNull { it.name == name } ?: StatsPeriod.FOUR_WEEKS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StatsPeriod.FOUR_WEEKS)

    /** Latest weight entry's kg, else the profile's weight, plus the entries as stored. */
    private val bodyWeight: Flow<Pair<Double?, List<WeightEntry>>> =
        combine(body.profile, body.weights) { profile, weights ->
            (weights.firstOrNull()?.kg ?: profile?.weightKg) to weights
        }

    internal val state: StateFlow<StatsUiState> = combine(
        period,
        settings.weeklyGoal,
        bodyWeight,
        measurements.observeAll(),
    ) { period, goal, bodyPair, measured -> StatsInputs(period, goal, bodyPair, measured) }
        .combine(overviewInputs) { inputs, overview -> inputs to overview }
        .flatMapLatest { (inputs, overview) ->
            val (period, goal, bodyPair, measured) = inputs
            stats.observeStats(period, goal).map { report ->
                StatsUiState(
                    loaded = true,
                    period = period,
                    report = report,
                    weeklyGoal = goal,
                    bodyWeightKg = bodyPair.first,
                    weights = bodyPair.second,
                    measurements = measured,
                    findings = InsightRules.findings(
                        report = report,
                        trainingDates = overview.trainingDates,
                        weeklyGoal = goal,
                        firstDayOfWeek = firstDayOfWeek,
                        routines = overview.routines,
                        machineGroups = overview.machineGroups,
                    ),
                    frequency = FrequencyRules.summary(overview.trainingDates, goal, report.to, firstDayOfWeek, overview.next),
                    strengthSummary = StrengthSummaryRules.summary(report),
                    body = OverviewRules.body(measured),
                    habit = OverviewRules.habit(report),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    internal fun setPeriod(period: StatsPeriod) {
        savedState[PERIOD_KEY] = period.name
    }

    internal fun addMeasurement(date: LocalDate, type: MeasurementType, valueCm: Double) {
        viewModelScope.launch { measurements.add(date, type, valueCm) }
    }

    internal fun deleteMeasurement(id: Long) {
        viewModelScope.launch { measurements.delete(id) }
    }

    internal fun undoDeleteMeasurement(measurement: BodyMeasurement) {
        viewModelScope.launch { measurements.restore(measurement) }
    }
}
