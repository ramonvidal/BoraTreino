package com.boratreino.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.CalendarRules
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.WeekGoalRules
import com.boratreino.core.model.working
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.MoveButtons
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.shortLabel
import com.boratreino.core.ui.showUndo
import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.theme.Sunny
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch
import com.boratreino.core.ui.R as CoreUiR

/** What the Calendar tab can ask for. */
internal data class CalendarActions(
    val onOpenGoal: () -> Unit = {},
    val onStepGoal: (up: Boolean) -> Unit = {},
    val onSaveGoal: () -> Unit = {},
    val onDismissGoal: () -> Unit = {},
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onSelectDay: (LocalDate) -> Unit = {},
    val onEditSession: (sessionId: Long) -> Unit = {},
    val onDeleteSession: (sessionId: Long) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onAddTraining: () -> Unit = {},
    val onOpenPlanChooser: () -> Unit = {},
    val onDismissPlanChooser: () -> Unit = {},
    val onPlan: (routineId: Long) -> Unit = {},
    val onUnplan: (routineId: Long) -> Unit = {},
    val onMovePlanned: (routineId: Long, up: Boolean) -> Unit = { _, _ -> },
    /** Unused: the global schedule mode no longer changes behaviour. */
    val onScheduleModeChange: (ScheduleMode) -> Unit = {},
    val onTrainingDaysChange: (Set<DayOfWeek>) -> Unit = {},
)

@Composable
internal fun CalendarScreen(
    onOpenSession: (sessionId: Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val deletedText = stringResource(R.string.calendar_deleted)
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Only sent once the repository write has finished.
                is CalendarEvent.OpenSession -> onOpenSession(event.sessionId)
                is CalendarEvent.Deleted -> launch {
                    if (snackbar.showUndo(deletedText, undoLabel)) viewModel.restore(event.detail)
                }
                CalendarEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    CalendarContent(
        state = state,
        actions = CalendarActions(
            onOpenGoal = viewModel::openGoal,
            onStepGoal = viewModel::stepGoal,
            onSaveGoal = viewModel::saveGoal,
            onDismissGoal = viewModel::dismissGoal,
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onSelectDay = viewModel::selectDay,
            onEditSession = onOpenSession,
            onDeleteSession = viewModel::askDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
            onAddTraining = viewModel::addTraining,
            onOpenPlanChooser = viewModel::openPlanChooser,
            onDismissPlanChooser = viewModel::dismissPlanChooser,
            onPlan = viewModel::plan,
            onUnplan = viewModel::unplan,
            onMovePlanned = viewModel::reorderPlanned,
            onTrainingDaysChange = viewModel::setTrainingDays,
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun CalendarContent(
    state: CalendarUiState,
    actions: CalendarActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeekStrip(state, actions)
            MonthCard(state, actions)
            DayDetail(state, actions)
            ScheduleCard(state, actions)
        }
    }

    if (state.goalEditor != null) {
        GoalDialog(state.goalEditor, actions)
    }
    if (state.confirmDelete != null) {
        DeleteDialog(actions)
    }
    if (state.planChooser) {
        val open = state.routines.filter { r -> state.planned[state.selected].orEmpty().none { it.routineId == r.id } }
        PlanChooserDialog(open, actions)
    }
}

/** The routine letter of a day label, or null when the day had no routine (the label is then a dot). */
private fun letterOf(label: String?): String? = label?.takeIf { it.isNotBlank() && it != NO_ROUTINE_LABEL }

private const val NO_ROUTINE_LABEL = "•"

/** A hairline between rows of one card. */
@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sunny.Line))
}

/** "Rotation days": the days the rotation routines are trained, each routine's schedule and the week preview. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleCard(state: CalendarUiState, actions: CalendarActions) {
    val preview = state.weekPreview.map { "${it.date.dayOfWeek.shortLabel()} ${it.shortName}".trim() }
    val rotationText = stringResource(R.string.calendar_in_rotation)
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.calendar_rotation_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.calendar_rotation_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.values().forEach { day ->
                    BoraChip(
                        label = day.shortLabel(),
                        selected = day in state.trainingDays,
                        onClick = {
                            val next = if (day in state.trainingDays) state.trainingDays - day else state.trainingDays + day
                            actions.onTrainingDaysChange(next)
                        },
                    )
                }
            }
            state.routines.forEach { routine ->
                val schedule = if (routine.weekdays.isEmpty()) {
                    rotationText
                } else {
                    routine.weekdays.sorted().map { it.shortLabel() }.joinToString(", ")
                }
                Text(
                    "${routine.name} · $schedule",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (preview.isNotEmpty()) {
                Text(
                    stringResource(R.string.calendar_week_preview, preview.joinToString(", ")),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                stringResource(R.string.calendar_rotation_pointer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * This week at a glance: the goal ("2 of 4", dots, streak; tapping opens the goal dialog) and the seven days as
 * round cells (trained = green, planned = dashed, today = ink ring); tapping a day selects it.
 */
@Composable
private fun WeekStrip(state: CalendarUiState, actions: CalendarActions) {
    val start = WeekGoalRules.weekStart(state.today, state.firstDayOfWeek)
    val days = (0L until DAYS_IN_WEEK).map { start.plusDays(it) }
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = actions.onOpenGoal)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.calendar_week_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.calendar_week_goal, state.daysTrained, state.weeklyGoal),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(state.weeklyGoal) { index ->
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < state.daysTrained) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )
                    }
                }
            }
            Text(
                text = if (state.streak > 0) {
                    pluralStringResource(R.plurals.calendar_streak, state.streak, state.streak)
                } else {
                    pluralStringResource(R.plurals.calendar_streak_start, state.weeklyGoal, state.weeklyGoal)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            days.forEach { date ->
                WeekCell(date, state, onClick = { actions.onSelectDay(date) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One day of the strip: weekday initial, the mark, the day number. */
@Composable
private fun WeekCell(date: LocalDate, state: CalendarUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = state.dayLabels[date]
    val planned = state.planned[date].orEmpty()
    val trained = date in state.trainedDates || label != null
    val kind = when {
        trained -> DayMarkKind.Trained
        planned.isNotEmpty() -> DayMarkKind.Planned
        else -> DayMarkKind.Rest
    }
    val letter = if (trained) letterOf(label) else planned.firstOrNull()?.shortName?.takeIf { it.isNotBlank() }
    val selected = date == state.selected
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        DayMark(
            kind = kind,
            size = 36.dp,
            isToday = date == state.today,
            restFill = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            when {
                letter != null -> Text(
                    letter,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = markContentColor(kind),
                    maxLines = 1,
                )
                kind == DayMarkKind.Trained -> CheckMark()
                else -> Unit
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (date == state.today) FontWeight.ExtraBold else FontWeight.Normal,
        )
    }
}

private const val DAYS_IN_WEEK = 7L

/** The month header with its arrows, the weekday initials, the grid of days and the legend. */
@Composable
private fun MonthCard(state: CalendarUiState, actions: CalendarActions) {
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()) }
    val grid = remember(state.month, state.firstDayOfWeek) { CalendarRules.monthGrid(state.month, state.firstDayOfWeek) }
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onPreviousMonth, modifier = Modifier.size(48.dp)) {
                Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(R.string.calendar_previous_month))
            }
            Text(
                state.month.format(monthFormat),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = actions.onNextMonth, modifier = Modifier.size(48.dp)) {
                // No forward chevron in :core:ui: the back arrow, turned around.
                Icon(
                    painterResource(CoreUiR.drawable.ic_back),
                    stringResource(R.string.calendar_next_month),
                    modifier = Modifier.rotate(180f),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (offset in 0L until 7L) {
                val day = state.firstDayOfWeek.plus(offset)
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                label = state.dayLabels[date],
                                planned = state.planned[date].orEmpty(),
                                isToday = date == state.today,
                                isSelected = date == state.selected,
                                onClick = { actions.onSelectDay(date) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Legend()
    }
}

/** One line under the grid: what the filled dot and the dashed ring mean. */
@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DayMark(DayMarkKind.Trained, 12.dp) {}
        Text(
            stringResource(R.string.calendar_legend_trained),
            modifier = Modifier.padding(start = 6.dp, end = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayMark(DayMarkKind.Planned, 12.dp) {}
        Text(
            stringResource(R.string.calendar_legend_planned),
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One day of the grid, 44 dp tall: a round mark with the day number (and the routine letter under it when the day
 * is trained or planned). Trained = filled green, planned = dashed outline, today = ink ring, selected = yellow.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    label: String?,
    planned: List<PlannedUi>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val kind = when {
        label != null -> DayMarkKind.Trained
        planned.isNotEmpty() -> DayMarkKind.Planned
        else -> DayMarkKind.Rest
    }
    val letter = if (label != null) letterOf(label) else planned.firstOrNull()?.shortName?.takeIf { it.isNotBlank() }
    Box(
        Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DayMark(kind = kind, size = 40.dp, isToday = isToday, selected = isSelected) {
            CellText(
                number = date.dayOfMonth.toString(),
                letter = letter,
                color = markContentColor(kind),
                bold = isToday,
            )
        }
    }
}

/** The selected day: a heading, then the action first (plan / add), the sessions and the plans. */
@Composable
private fun DayDetail(state: CalendarUiState, actions: CalendarActions) {
    val titleFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }
    Text(
        state.selected.format(titleFormat),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val isPast = state.selected.isBefore(state.today)
    val isToday = state.selected == state.today
    if (state.sessions.isNotEmpty()) {
        state.sessions.forEach { detail -> SessionCard(detail, state, actions) }
    } else if (state.loaded && isPast) {
        BoraCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.calendar_nothing_logged),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoraButton(
                    text = stringResource(R.string.calendar_add_training),
                    onClick = actions.onAddTraining,
                    modifier = Modifier.fillMaxWidth(),
                    style = BoraButtonStyle.Tonal,
                    leadingIcon = CoreUiR.drawable.ic_add,
                )
            }
        }
    }
    if (state.loaded && !isPast) {
        PlanSection(
            planned = state.planned[state.selected].orEmpty(),
            routines = state.routines,
            hasSessions = state.sessions.isNotEmpty(),
            offerAdd = isToday && state.sessions.isEmpty(),
            actions = actions,
        )
    }
    state.energy?.let { energy ->
        val amount = NumberFormat.getIntegerInstance().format(energy.kcal)
        Text(
            if (energy.kind == DayKind.REST) stringResource(R.string.calendar_energy_rest, amount)
            else stringResource(R.string.calendar_energy_day, amount),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Today or later: the planned routines as rows (letter, name, move, remove), then "Plan a workout" while routines
 * are left (filled when nothing is logged yet, tonal otherwise). Today with nothing logged also offers "Add a workout".
 */
@Composable
private fun PlanSection(
    planned: List<PlannedUi>,
    routines: List<RoutineChoiceUi>,
    hasSessions: Boolean,
    offerAdd: Boolean,
    actions: CalendarActions,
) {
    if (planned.isNotEmpty()) {
        BoraCard(modifier = Modifier.fillMaxWidth()) {
            planned.forEachIndexed { index, item ->
                if (index > 0) Hairline()
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.shortName.isNotBlank()) {
                        RoutineBadge(item.shortName, selected = false, modifier = Modifier.padding(end = 12.dp))
                    }
                    Text(item.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    if (planned.size >= 2) {
                        MoveButtons(
                            canMoveUp = index > 0,
                            canMoveDown = index < planned.lastIndex,
                            onUp = { actions.onMovePlanned(item.routineId, true) },
                            onDown = { actions.onMovePlanned(item.routineId, false) },
                        )
                    }
                    IconButton(onClick = { actions.onUnplan(item.routineId) }, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(CoreUiR.drawable.ic_close), stringResource(R.string.calendar_plan_remove))
                    }
                }
            }
        }
    }
    if (routines.any { r -> planned.none { it.routineId == r.id } }) {
        BoraButton(
            text = stringResource(R.string.calendar_plan_workout),
            onClick = actions.onOpenPlanChooser,
            modifier = Modifier.fillMaxWidth(),
            style = if (hasSessions) BoraButtonStyle.Tonal else BoraButtonStyle.Filled,
            leadingIcon = CoreUiR.drawable.ic_add,
        )
    }
    if (offerAdd) {
        BoraButton(
            text = stringResource(R.string.calendar_add_training),
            onClick = actions.onAddTraining,
            modifier = Modifier.fillMaxWidth(),
            style = BoraButtonStyle.Tonal,
            leadingIcon = CoreUiR.drawable.ic_add,
        )
    }
}

/** The routines to plan from: one tap on a routine plans it and closes. */
@Composable
private fun PlanChooserDialog(routines: List<RoutineChoiceUi>, actions: CalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissPlanChooser,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text(stringResource(R.string.calendar_plan_title)) },
        text = {
            LazyColumn {
                items(routines, key = { it.id }) { routine ->
                    // The letter first: with a rotation the owner thinks "A, B, C", not the names.
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { actions.onPlan(routine.id) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (routine.shortName.isNotBlank()) {
                            RoutineBadge(routine.shortName, selected = false)
                        }
                        Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Start)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            BoraButton(
                text = stringResource(CoreUiR.string.cancel),
                onClick = actions.onDismissPlanChooser,
                modifier = Modifier.width(DIALOG_BUTTON_WIDTH),
                style = BoraButtonStyle.Text,
            )
        },
    )
}

private val DIALOG_BUTTON_WIDTH = 120.dp

/** A finished or open session: routine badge and name, the counts, the time, the exercises, Edit and Delete. */
@Composable
private fun SessionCard(detail: SessionDetail, state: CalendarUiState, actions: CalendarActions) {
    val resources = LocalContext.current.resources
    val timeFormat = remember(state.zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(state.zone)
    }
    val session = detail.session
    // Warm-ups are not counted: only working sets, and exercises that have some.
    val setCount = detail.machines.sumOf { it.sets.working().size }
    val machineCount = detail.machines.count { it.sets.working().isNotEmpty() }
    val routineName = detail.routine?.name ?: stringResource(R.string.calendar_free_training)
    val counts = stringResource(R.string.calendar_session_counts, resources.machinesText(machineCount), resources.setsText(setCount))
    val start = timeFormat.format(session.startedAt)
    val end = session.endedAt
    val time = when {
        end == null -> stringResource(R.string.calendar_time_open, start)
        end == session.startedAt -> start
        else -> stringResource(R.string.calendar_time_range, start, timeFormat.format(end))
    }
    val shortName = detail.routine?.shortName.orEmpty()
    BoraCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shortName.isNotBlank()) {
                    RoutineBadge(shortName, selected = true, modifier = Modifier.padding(end = 12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(routineName, style = MaterialTheme.typography.titleMedium)
                    Text(counts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(time, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            detail.machines.forEach { entry ->
                val summary = SetSummary.of(entry.sets)
                Column {
                    Text(entry.machine.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    if (summary != null) {
                        Text(
                            summaryLabel(entry.machine, summary),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BoraButton(
                    text = stringResource(R.string.calendar_edit),
                    onClick = { actions.onEditSession(session.id) },
                    modifier = Modifier.weight(1f),
                    style = BoraButtonStyle.Tonal,
                    leadingIcon = CoreUiR.drawable.ic_edit,
                )
                DangerTextButton(
                    text = stringResource(R.string.calendar_delete),
                    onClick = { actions.onDeleteSession(session.id) },
                    modifier = Modifier.weight(1f),
                    leadingIcon = CoreUiR.drawable.ic_delete,
                )
            }
        }
    }
}

/** The weekly goal: a stepper (1 to 7) and Save. */
@Composable
private fun GoalDialog(goal: Int, actions: CalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissGoal,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text(stringResource(R.string.calendar_goal_title)) },
        text = {
            Stepper(
                label = stringResource(R.string.calendar_goal_label),
                value = goal.toString(),
                onMinus = { actions.onStepGoal(false) },
                onPlus = { actions.onStepGoal(true) },
                onValueClick = {},
            )
        },
        confirmButton = {
            BoraButton(
                text = stringResource(CoreUiR.string.save),
                onClick = actions.onSaveGoal,
                modifier = Modifier.width(DIALOG_BUTTON_WIDTH),
            )
        },
        dismissButton = {
            BoraButton(
                text = stringResource(CoreUiR.string.cancel),
                onClick = actions.onDismissGoal,
                modifier = Modifier.width(DIALOG_BUTTON_WIDTH),
                style = BoraButtonStyle.Text,
            )
        },
    )
}

@Composable
private fun DeleteDialog(actions: CalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDelete,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text(stringResource(R.string.calendar_delete_title)) },
        text = { Text(stringResource(R.string.calendar_delete_message)) },
        confirmButton = {
            DangerTextButton(
                text = stringResource(CoreUiR.string.delete),
                onClick = actions.onConfirmDelete,
                modifier = Modifier.width(DIALOG_BUTTON_WIDTH),
            )
        },
        dismissButton = {
            BoraButton(
                text = stringResource(CoreUiR.string.cancel),
                onClick = actions.onDismissDelete,
                modifier = Modifier.width(DIALOG_BUTTON_WIDTH),
                style = BoraButtonStyle.Text,
            )
        },
    )
}
