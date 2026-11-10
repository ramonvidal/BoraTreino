package com.boratreino.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.data.RoutineRepository
import com.boratreino.core.model.CalendarRules
import com.boratreino.core.model.BodyProfile
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.EnergyRules
import com.boratreino.core.model.PlannedWorkout
import com.boratreino.core.model.Routine
import com.boratreino.core.model.SessionSets
import com.boratreino.core.settings.BodySettings
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The quiet energy line of the selected day: the estimate in [kcal] (rounded to 10) and what kind of day it was. */
internal data class DayEnergyUi(val kcal: Int, val kind: DayKind)

/** A routine planned for a day: its id and the texts shown (name on the card, short name in the grid). */
internal data class PlannedUi(val routineId: Long, val name: String, val shortName: String)

/** One routine in the "Plan a workout" chooser. */
internal data class RoutineChoiceUi(val id: Long, val name: String)

/** The planned routines (today and later) with the routine choices, as one value for combine(). */
private data class PlanningData(val planned: Map<LocalDate, List<PlannedUi>>, val routines: List<RoutineChoiceUi>)

/**
 * Everything the Calendar tab shows. [dayLabels] has the badge text of every training day of
 * [month]; [sessions] are the [selected] day's sessions. [goalEditor] is the goal dialog's value
 * (null = closed); [confirmDelete] is the session waiting for the delete confirmation.
 */
internal data class CalendarUiState(
    val loaded: Boolean = false,
    val today: LocalDate = LocalDate.of(2000, 1, 1),
    val zone: ZoneId = ZoneId.of("UTC"),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val month: YearMonth = YearMonth.of(2000, 1),
    val selected: LocalDate = LocalDate.of(2000, 1, 1),
    val weeklyGoal: Int = WeekGoalRules.DEFAULT_GOAL,
    val daysTrained: Int = 0,
    val streak: Int = 0,
    val dayLabels: Map<LocalDate, String> = emptyMap(),
    /** The routine behind each day's badge (first session with a routine), for the badge colour. */
    val dayRoutineIds: Map<LocalDate, Long> = emptyMap(),
    val sessions: List<SessionDetail> = emptyList(),
    /** Null for future days, without a profile, or when the owner switched the estimate off. */
    val energy: DayEnergyUi? = null,
    /** Routines planned for today and later days; past plans are ignored. */
    val planned: Map<LocalDate, List<PlannedUi>> = emptyMap(),
    /** The routines the chooser lists. */
    val routines: List<RoutineChoiceUi> = emptyList(),
    /** The "Plan a workout" chooser for the selected day is open. */
    val planChooser: Boolean = false,
    val goalEditor: Int? = null,
    val confirmDelete: Long? = null,
)

internal sealed interface CalendarEvent {
    /** The session is deleted; the screen offers Undo. */
    data class Deleted(val detail: SessionDetail) : CalendarEvent

    /** A session was added for a day; the screen opens its editor. */
    data class OpenSession(val sessionId: Long) : CalendarEvent
    data object Failed : CalendarEvent
}

/** Which month is shown and which day is selected. */
private data class Browsing(val month: YearMonth, val selected: LocalDate)

/** A second past midnight, so the new day's date is surely the one read. */
private const val MIDNIGHT_MARGIN_MS = 1_000L
private const val RECHECK_MS = 60_000L
private const val MAX_UNCHANGED_WAKES = 2

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val routineRepository: RoutineRepository,
    private val settings: TrainingSettings,
    private val body: BodySettings,
    private val clock: Clock,
) : ViewModel() {

    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private val eventChannel = Channel<CalendarEvent>(Channel.BUFFERED)
    internal val events: Flow<CalendarEvent> = eventChannel.receiveAsFlow()

    private val browsing = MutableStateFlow(LocalDate.now(clock).let { Browsing(YearMonth.from(it), it) })
    private val goalEditor = MutableStateFlow<Int?>(null)
    private val confirmDelete = MutableStateFlow<Long?>(null)
    private val planChooser = MutableStateFlow(false)

    /**
     * Today's date, re-emitted after the next midnight so the planned workouts follow the day when the app stays open.
     * If the date has not changed after waking up (a clock that does not move, as in tests, or an early wake-up),
     * it checks once more a minute later and then stops, so it never spins.
     */
    private val currentDate: Flow<LocalDate> = flow {
        var date = LocalDate.now(clock)
        emit(date)
        var unchanged = 0
        while (unchanged < MAX_UNCHANGED_WAKES) {
            val now = ZonedDateTime.now(clock)
            val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val wait = if (unchanged == 0) Duration.between(now, nextMidnight).toMillis() + MIDNIGHT_MARGIN_MS else RECHECK_MS
            delay(wait)
            val again = LocalDate.now(clock)
            if (again != date) {
                date = again
                emit(date)
                unchanged = 0
            } else {
                unchanged++
            }
        }
    }.distinctUntilChanged()

    private val planning: Flow<PlanningData> = combine(
        routineRepository.observePlanned(),
        routineRepository.observePlan(),
        currentDate,
    ) { plannedList: List<PlannedWorkout>, plan, today ->
        val routines: List<Routine> = plan.routines.map { it.routine }
        val byId = routines.associateBy { it.id }
        PlanningData(
            planned = plannedList
                .filter { !it.date.isBefore(today) }
                // observePlanned() already comes ordered by date, position and routine: keep that order.
                .mapNotNull { p -> byId[p.routineId]?.let { r -> p.date to PlannedUi(r.id, r.name, r.shortName) } }
                .groupBy({ it.first }, { it.second }),
            routines = routines.map { RoutineChoiceUi(it.id, it.name) },
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<CalendarUiState> = browsing.flatMapLatest { view ->
        combine(
            history.observeMonth(view.month),
            history.observeDay(view.selected),
            history.observeTrainingDates(),
            settings.weeklyGoal,
            // combine() takes at most five flows with a typed lambda: the body flows and the plans go in as one.
            combine(body.profile, body.showEnergyEstimate, planning) { profile, show, plan -> Pair(profile.takeIf { show }, plan) },
        ) { month, day, dates, goal, extras ->
            val (profile, plan) = extras
            val today = LocalDate.now(clock)
            CalendarUiState(
                loaded = true,
                today = today,
                zone = clock.zone,
                firstDayOfWeek = firstDayOfWeek,
                month = view.month,
                selected = view.selected,
                weeklyGoal = goal,
                daysTrained = WeekGoalRules.daysTrained(dates, today, firstDayOfWeek),
                streak = WeekGoalRules.streak(dates, goal, today, firstDayOfWeek),
                dayLabels = month.mapValues { (_, sessions) -> CalendarRules.dayLabel(sessions) },
                dayRoutineIds = month.mapNotNull { (date, sessions) ->
                    sessions.sortedBy { it.session.startedAt }.firstNotNullOfOrNull { it.routine?.id }?.let { date to it }
                }.toMap(),
                sessions = day,
                energy = dayEnergy(profile, view.selected, today, day),
                planned = plan.planned,
                routines = plan.routines,
            )
        }
    }

    private fun dayEnergy(profile: BodyProfile?, date: LocalDate, today: LocalDate, day: List<SessionDetail>): DayEnergyUi? {
        if (profile == null || date.isAfter(today)) return null
        val sessions = day.map { detail -> SessionSets(detail.session, detail.machines.flatMap { it.sets }) }
        val result = EnergyRules.day(profile, date, Instant.now(clock), sessions, trainingExpected = false)
        return DayEnergyUi(result.kcal, result.kind)
    }

    internal val state: StateFlow<CalendarUiState> = combine(content, goalEditor, confirmDelete, planChooser) { s, goal, delete, chooser ->
        s.copy(goalEditor = goal, confirmDelete = delete, planChooser = chooser)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    // Month and day.

    internal fun previousMonth() {
        browsing.value = browsing.value.let { it.copy(month = it.month.minusMonths(1)) }
    }

    internal fun nextMonth() {
        browsing.value = browsing.value.let { it.copy(month = it.month.plusMonths(1)) }
    }

    /** Future days can be selected too, to plan a workout on them. */
    internal fun selectDay(date: LocalDate) {
        browsing.value = browsing.value.copy(selected = date)
    }

    // Weekly goal.

    internal fun openGoal() {
        goalEditor.value = state.value.weeklyGoal
    }

    internal fun stepGoal(up: Boolean) {
        val current = goalEditor.value ?: return
        goalEditor.value = (if (up) current + 1 else current - 1).coerceIn(WeekGoalRules.MIN_GOAL, WeekGoalRules.MAX_GOAL)
    }

    internal fun dismissGoal() {
        goalEditor.value = null
    }

    internal fun saveGoal() {
        val goal = goalEditor.value ?: return
        goalEditor.value = null
        write { settings.setWeeklyGoal(goal) }
    }

    // Planning a workout on the selected day (today or later).

    internal fun openPlanChooser() {
        if (browsing.value.selected.isBefore(LocalDate.now(clock))) return
        planChooser.value = true
    }

    internal fun dismissPlanChooser() {
        planChooser.value = false
    }

    /** One tap on a routine plans it for the selected day and closes the chooser. */
    internal fun plan(routineId: Long) {
        val day = browsing.value.selected
        planChooser.value = false
        if (day.isBefore(LocalDate.now(clock))) return
        write { routineRepository.planWorkout(day, routineId) }
    }

    internal fun unplan(routineId: Long) {
        val day = browsing.value.selected
        write { routineRepository.unplanWorkout(day, routineId) }
    }

    /** Moves a planned routine one place up or down among the selected day's plans, keeping the stored order otherwise. */
    internal fun reorderPlanned(routineId: Long, up: Boolean) {
        val day = browsing.value.selected
        val ids = state.value.planned[day].orEmpty().map { it.routineId }.toMutableList()
        val from = ids.indexOf(routineId)
        val to = if (up) from - 1 else from + 1
        if (from < 0 || to !in ids.indices) return
        ids[from] = ids[to].also { ids[to] = ids[from] }
        write { routineRepository.reorderPlanned(day, ids) }
    }

    // Sessions of the selected day.

    internal fun addTraining() {
        val day = browsing.value.selected
        write {
            val id = history.addSession(day, null)
            eventChannel.send(CalendarEvent.OpenSession(id))
        }
    }

    internal fun askDelete(sessionId: Long) {
        confirmDelete.value = sessionId
    }

    internal fun dismissDelete() {
        confirmDelete.value = null
    }

    internal fun confirmDelete() {
        val id = confirmDelete.value ?: return
        confirmDelete.value = null
        write {
            val deleted = history.deleteSession(id) ?: return@write
            eventChannel.send(CalendarEvent.Deleted(deleted))
        }
    }

    internal fun restore(detail: SessionDetail) {
        write { history.restoreSession(detail) }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                eventChannel.send(CalendarEvent.Failed)
            }
        }
    }
}
