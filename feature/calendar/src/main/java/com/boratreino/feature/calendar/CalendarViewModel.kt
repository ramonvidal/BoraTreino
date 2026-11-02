package com.boratreino.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boratreino.core.data.HistoryRepository
import com.boratreino.core.model.CalendarRules
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.settings.TrainingSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val sessions: List<SessionDetail> = emptyList(),
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

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val settings: TrainingSettings,
    private val clock: Clock,
) : ViewModel() {

    private val firstDayOfWeek: DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    private val eventChannel = Channel<CalendarEvent>(Channel.BUFFERED)
    internal val events: Flow<CalendarEvent> = eventChannel.receiveAsFlow()

    private val browsing = MutableStateFlow(LocalDate.now(clock).let { Browsing(YearMonth.from(it), it) })
    private val goalEditor = MutableStateFlow<Int?>(null)
    private val confirmDelete = MutableStateFlow<Long?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val content: Flow<CalendarUiState> = browsing.flatMapLatest { view ->
        combine(
            history.observeMonth(view.month),
            history.observeDay(view.selected),
            history.observeTrainingDates(),
            settings.weeklyGoal,
        ) { month, day, dates, goal ->
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
                sessions = day,
            )
        }
    }

    internal val state: StateFlow<CalendarUiState> = combine(content, goalEditor, confirmDelete) { s, goal, delete ->
        s.copy(goalEditor = goal, confirmDelete = delete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    // Month and day.

    internal fun previousMonth() {
        browsing.value = browsing.value.let { it.copy(month = it.month.minusMonths(1)) }
    }

    internal fun nextMonth() {
        browsing.value = browsing.value.let { it.copy(month = it.month.plusMonths(1)) }
    }

    /** Future days do nothing. */
    internal fun selectDay(date: LocalDate) {
        if (date.isAfter(LocalDate.now(clock))) return
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
