package com.boratreino.feature.calendar

import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.showUndo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.boratreino.core.model.SessionDetail
import java.text.NumberFormat
import com.boratreino.core.model.SetSummary
import com.boratreino.core.model.working
import com.boratreino.core.ui.RoutineBadge
import com.boratreino.core.ui.Stepper
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
            WeekCard(state, actions.onOpenGoal)
            MonthCard(state, actions)
            DayDetail(state, actions)
        }
    }

    if (state.goalEditor != null) {
        GoalDialog(state.goalEditor, actions)
    }
    if (state.confirmDelete != null) {
        DeleteDialog(actions)
    }
}

/** "This week: 2 of 4", a circle per goal day and the streak; tapping it opens the goal dialog. */
@Composable
private fun WeekCard(state: CalendarUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.calendar_week_progress, state.daysTrained, state.weeklyGoal),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(state.weeklyGoal) { index ->
                    val filled = index < state.daysTrained
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
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
    }
}

/** The month header with its arrows, the weekday initials and the grid of days. */
@Composable
private fun MonthCard(state: CalendarUiState, actions: CalendarActions) {
    val monthFormat = remember { DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()) }
    val grid = remember(state.month, state.firstDayOfWeek) { CalendarRules.monthGrid(state.month, state.firstDayOfWeek) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
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
                                    isToday = date == state.today,
                                    isSelected = date == state.selected,
                                    enabled = !date.isAfter(state.today),
                                    onClick = { actions.onSelectDay(date) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One day: its number, and a badge when it has training. */
@Composable
private fun DayCell(
    date: LocalDate,
    label: String?,
    isToday: Boolean,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val numberColor = when {
        !enabled -> MaterialTheme.colorScheme.outline
        isSelected && label == null -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected && label == null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.Normal,
                color = numberColor,
            )
        }
        if (label != null) {
            RoutineBadge(label, selected = isSelected)
        }
    }
}

/** The selected day: a heading, then one card per session, or "Nothing logged". */
@Composable
private fun DayDetail(state: CalendarUiState, actions: CalendarActions) {
    val titleFormat = remember { DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()) }
    Text(
        state.selected.format(titleFormat),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (state.sessions.isEmpty()) {
        if (state.loaded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.calendar_nothing_logged), style = MaterialTheme.typography.titleMedium)
                    FilledTonalButton(onClick = actions.onAddTraining, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(painterResource(CoreUiR.drawable.ic_add), contentDescription = null)
                        Text(stringResource(R.string.calendar_add_training), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    } else {
        state.sessions.forEach { detail -> SessionCard(detail, state, actions) }
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

@Composable
private fun SessionCard(detail: SessionDetail, state: CalendarUiState, actions: CalendarActions) {
    val resources = LocalContext.current.resources
    val timeFormat = remember(state.zone) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).withZone(state.zone)
    }
    val session = detail.session
    // Warm-ups are not counted: only working sets, and machines that have some.
    val setCount = detail.machines.sumOf { it.sets.working().size }
    val machineCount = detail.machines.count { it.sets.working().isNotEmpty() }
    val routineName = detail.routine?.name ?: stringResource(R.string.calendar_free_training)
    val title = stringResource(
        R.string.calendar_session_summary,
        routineName,
        resources.machinesText(machineCount),
        resources.setsText(setCount),
    )
    val start = timeFormat.format(session.startedAt)
    val end = session.endedAt
    val time = when {
        end == null -> stringResource(R.string.calendar_time_open, start)
        end == session.startedAt -> start
        else -> stringResource(R.string.calendar_time_range, start, timeFormat.format(end))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
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
                OutlinedButton(
                    onClick = { actions.onEditSession(session.id) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_edit), contentDescription = null)
                    Text(stringResource(R.string.calendar_edit), Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { actions.onDeleteSession(session.id) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.calendar_delete), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** The weekly goal: a stepper (1 to 7) and Save. */
@Composable
private fun GoalDialog(goal: Int, actions: CalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissGoal,
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
            Button(onClick = actions.onSaveGoal, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(CoreUiR.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissGoal, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        },
    )
}

@Composable
private fun DeleteDialog(actions: CalendarActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDelete,
        title = { Text(stringResource(R.string.calendar_delete_title)) },
        text = { Text(stringResource(R.string.calendar_delete_message)) },
        confirmButton = {
            TextButton(
                onClick = actions.onConfirmDelete,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(CoreUiR.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissDelete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(CoreUiR.string.cancel))
            }
        },
    )
}
