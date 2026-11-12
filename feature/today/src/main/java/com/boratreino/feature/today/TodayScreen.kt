package com.boratreino.feature.today

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.DayKind
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.RepeatMode
import com.boratreino.core.model.RoutineChoice
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.Mascot
import com.boratreino.core.ui.MoveButtons
import com.boratreino.core.ui.label
import com.boratreino.core.ui.showUndo
import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.targetText
import com.boratreino.core.ui.theme.Sunny
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import com.boratreino.core.ui.R as CoreUiR

/** What the Today tab can ask for; `:app` and the graph decide where each goes. */
internal data class TodayActions(
    val onOpenMachine: (machineId: Long) -> Unit = {},
    val onAllMachines: () -> Unit = {},
    /** The empty state's "Set up in 3 steps": opens the first-run setup (wired in `:app`). */
    val onStartSetup: () -> Unit = {},
    val onAddMachine: () -> Unit = {},
    val onDone: () -> Unit = {},
    val onStartEditing: () -> Unit = {},
    val onFinishEditing: () -> Unit = {},
    val onMoveMachine: (machineId: Long, up: Boolean) -> Unit = { _, _ -> },
    val onRemoveMachine: (machineId: Long) -> Unit = {},
    /** Opens the full editor of the active ficha (Edit sets and reps). */
    val onEditRoutine: () -> Unit = {},
    val onChooseRoutine: (RoutineChoice) -> Unit = {},
    val onSkipMachine: (machineId: Long) -> Unit = {},
    val onSkipToday: () -> Unit = {},
    /** The row menu: opens the machine editor like the pencil. */
    val onEditMachine: (machineId: Long) -> Unit = {},
    /** The row menu's "Skip today": deletes the sets already logged (with Undo) or hides the machine. */
    val onMenuSkip: (machineId: Long) -> Unit = {},
    val onRemoveFromRoutine: () -> Unit = {},
    val onDismissSkip: () -> Unit = {},
    val onRepeatSame: () -> Unit = {},
    val onRepeatSuggested: () -> Unit = {},
    val summary: SummaryActions = SummaryActions(),
)

@Composable
internal fun TodayScreen(
    onOpenMachine: (Long) -> Unit,
    onAllMachines: () -> Unit,
    onEditRoutine: (Long) -> Unit,
    onEditMachine: (Long) -> Unit = {},
    onStartSetup: () -> Unit = {},
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalContext.current.resources
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)
    val context = LocalContext.current

    // Leaving the screen ends edit mode.
    DisposableEffect(viewModel) { onDispose { viewModel.finishEditing() } }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // The summary sheet replaces the old Done snackbar; it opens from the state.
                is TodayEvent.Finished -> Unit
                is TodayEvent.Share -> launch {
                    // Drawing the picture is off the main thread; opening the share sheet is not.
                    val chooser = withContext(Dispatchers.IO) {
                        runCatching { shareChooser(context, event.summary) }.getOrNull()
                    }
                    val started = chooser != null && runCatching { context.startActivity(chooser) }.isSuccess
                    if (!started) viewModel.shareFailed()
                }
                is TodayEvent.Skipped -> launch {
                    val text = resources.getString(R.string.today_skipped, event.machine.name)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.unskip(event.machine.id)
                }
                is TodayEvent.Discarded -> launch {
                    val text = resources.getString(R.string.today_menu_removed_today)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.undoDiscard(event.machine.id, event.sets)
                }
                is TodayEvent.Removed -> launch {
                    val text = resources.getString(R.string.today_removed_from, event.routineName)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.restoreItem(event.item)
                }
                is TodayEvent.Repeated -> launch {
                    val text = resources.getQuantityString(
                        R.plurals.today_repeated, event.outcome.machineCount, event.outcome.machineCount,
                    )
                    if (snackbar.showUndo(text, undoLabel)) viewModel.undoRepeat(event.outcome)
                }
                TodayEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    TodayContent(
        state = state,
        actions = TodayActions(
            onOpenMachine = onOpenMachine,
            onAllMachines = onAllMachines,
            onStartSetup = onStartSetup,
            onAddMachine = onAllMachines,
            onDone = viewModel::finish,
            onStartEditing = viewModel::startEditing,
            onFinishEditing = viewModel::finishEditing,
            onMoveMachine = viewModel::moveMachine,
            onRemoveMachine = viewModel::removeMachine,
            onEditRoutine = {
                state.routines?.activeId?.let {
                    viewModel.finishEditing()
                    onEditRoutine(it)
                }
            },
            onChooseRoutine = viewModel::choose,
            onSkipMachine = viewModel::askSkip,
            onSkipToday = viewModel::skipToday,
            onEditMachine = onEditMachine,
            onMenuSkip = viewModel::skipFromMenu,
            onRemoveFromRoutine = viewModel::removeFromRoutine,
            onDismissSkip = viewModel::dismissSkip,
            onRepeatSame = { viewModel.repeatLast(RepeatMode.SAME) },
            onRepeatSuggested = { viewModel.repeatLast(RepeatMode.SUGGESTED) },
            summary = SummaryActions(
                onShare = viewModel::shareSummary,
                onUpdateRoutine = viewModel::applyRoutineUpdate,
                onUndoRoutineUpdate = viewModel::undoRoutineUpdate,
                onNotFinished = viewModel::reopenFromSummary,
                onClose = viewModel::dismissSummary,
            ),
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@Composable
internal fun TodayContent(
    state: TodayUiState,
    actions: TodayActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val session = state.session
            val routines = state.routines
            when {
                routines == null && session == null -> emptyToday(state, actions)
                routines == null && session != null -> freeSession(state, session, null, actions)
                routines != null && routines.activeName != null -> routineToday(state, routines, actions)
                routines != null && session != null -> freeSession(state, session, routines, actions)
                routines != null && routines.restDay -> restDay(state, routines, actions)
                routines != null -> freeSession(state, null, routines, actions)
            }
        }
    }

    state.skipDialog?.let { SkipMachineDialog(it, actions) }
    state.summary?.let { TodaySummarySheet(it.copy(mascotSex = state.mascotSex), actions.summary) }
}

/** "Wednesday, 11 November" in the phone's language; null without a date. */
@Composable
private fun dateText(date: LocalDate?): String? {
    if (date == null) return null
    val locale = Locale.getDefault()
    return remember(date, locale) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

/** The yellow card at the top: date, title, optional subtitle and progress ring, then [content] (switcher and the main button). */
@Composable
private fun HeroCard(
    date: String?,
    title: String,
    subtitle: String?,
    ringFraction: Float?,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(end = if (ringFraction != null) 12.dp else 0.dp)) {
                if (date != null) {
                    Text(
                        date,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Sunny.Ink.copy(alpha = 0.75f),
                    )
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Sunny.Ink,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Sunny.Ink.copy(alpha = 0.85f))
                }
            }
            if (ringFraction != null) ProgressRing(ringFraction)
        }
        content()
    }
}

/** The small pill in the hero that names the routine and opens the routine sheet. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RoutineSwitcher(routines: RoutineUi, onChoose: (RoutineChoice) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    val name = routines.activeName
    val label = when {
        name == null -> stringResource(R.string.today_free)
        routines.chips.any { it.selected && it.suggested } -> stringResource(R.string.today_switcher_next, name)
        else -> name
    }
    Row(
        Modifier.heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(Sunny.Ink.copy(alpha = 0.1f))
            .clickable(role = Role.Button) { open = true }
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Sunny.Ink)
        Spacer(Modifier.width(4.dp))
        Icon(painterResource(CoreUiR.drawable.ic_expand_more), contentDescription = null, Modifier.size(18.dp), tint = Sunny.Ink)
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.today_switcher_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    routines.chips.forEach { chip ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BoraChip(
                                label = chip.shortName,
                                selected = chip.selected,
                                onClick = { open = false; onChoose(RoutineChoice.Chosen(chip.id)) },
                                large = true,
                            )
                            if (chip.suggested && !chip.selected) {
                                Text(
                                    stringResource(R.string.today_chip_next),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    BoraChip(
                        label = stringResource(R.string.today_free),
                        selected = routines.freeSelected,
                        onClick = { open = false; onChoose(RoutineChoice.Free) },
                        large = true,
                    )
                }
            }
        }
    }
}

/** App name and the date in large type; used by the screen without any routine and without a session. */
private fun LazyListScope.plainHeader(state: TodayUiState) {
    item(key = "today_header") {
        val date = dateText(state.today)
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp)) {
            Text(
                stringResource(R.string.today_app_name),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (date != null) {
                Text(date, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** No routines and no session: the mascot above the message, the recent exercises and the main action. */
private fun LazyListScope.emptyToday(state: TodayUiState, actions: TodayActions) {
    plainHeader(state)
    energyLine(state.energy)
    item(key = "mascot") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Mascot(state.mascotSex, size = 120.dp) }
    }
    item(key = "setup") {
        BoraButton(
            text = stringResource(R.string.today_setup),
            onClick = actions.onStartSetup,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    recentList(state.recent, actions, R.string.today_pick_to_start)
    item(key = "all") { AddButton(stringResource(R.string.today_all_machines), actions.onAllMachines) }
}

/** The recent exercises in one card under a title (no session yet). */
private fun LazyListScope.recentList(recent: List<com.boratreino.core.model.Machine>, actions: TodayActions, title: Int) {
    item(key = "title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
    if (recent.isNotEmpty()) {
        item(key = "recent") {
            BoraCard(Modifier.fillMaxWidth()) {
                recent.forEachIndexed { index, machine ->
                    if (index > 0) RowDivider()
                    PlainExerciseRow(
                        machine = machine,
                        supporting = machine.muscleGroup.label(),
                        onClick = { actions.onOpenMachine(machine.id) },
                    )
                }
            }
        }
    }
}

/** An open session without a routine (or no routine chosen): the free-training hero and the session's exercises. */
private fun LazyListScope.freeSession(
    state: TodayUiState,
    session: SessionUi?,
    routines: RoutineUi?,
    actions: TodayActions,
) {
    item(key = "hero") {
        val resources = LocalContext.current.resources
        HeroCard(
            date = dateText(state.today),
            title = stringResource(R.string.today_hero_free),
            subtitle = session?.let { resources.machinesAndSets(it.machineCount, it.setCount) },
            ringFraction = null,
        ) {
            if (routines != null) RoutineSwitcher(routines, actions.onChooseRoutine)
            BoraButton(
                text = stringResource(R.string.today_add_machine),
                onClick = actions.onAddMachine,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = CoreUiR.drawable.ic_add,
            )
            if (session != null) {
                BoraButton(
                    text = stringResource(R.string.today_hero_finish),
                    onClick = actions.onDone,
                    modifier = Modifier.fillMaxWidth(),
                    style = BoraButtonStyle.Text,
                )
            }
        }
    }
    if (session != null) {
        item(key = "session") {
            BoraCard(Modifier.fillMaxWidth()) { SessionRows(session.machines, actions) }
        }
    } else {
        recentList(state.recent, actions, R.string.today_pick_to_start)
    }
    energyLine(state.energy)
}

/** The session's exercises as rows of one card. */
@Composable
private fun SessionRows(machines: List<SessionMachineUi>, actions: TodayActions) {
    machines.forEachIndexed { index, item ->
        if (index > 0) RowDivider()
        PlainExerciseRow(
            machine = item.machine,
            supporting = item.summary?.let { summaryLabel(item.machine, it) },
            onClick = { actions.onOpenMachine(item.machine.id) },
        )
    }
}

/** A tappable row: picture, bold name and one muted line. */
@Composable
private fun PlainExerciseRow(
    machine: com.boratreino.core.model.Machine,
    supporting: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowThumb(machine)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Rest day: the mascot, the next training and a tonal "Train anyway". No exercise list. */
private fun LazyListScope.restDay(state: TodayUiState, routines: RoutineUi, actions: TodayActions) {
    item(key = "rest") {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Mascot(state.mascotSex, size = 140.dp)
            Text(
                stringResource(R.string.today_rest_day),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            val upcoming = routines.upcoming
            if (upcoming != null) {
                val day = if (upcoming.tomorrow) {
                    stringResource(R.string.today_tomorrow)
                } else {
                    upcoming.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                }
                Text(
                    stringResource(R.string.today_next_training, day, upcoming.routineName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    item(key = "train_anyway") {
        val chip = routines.chips.firstOrNull { it.suggested } ?: routines.chips.firstOrNull()
        BoraButton(
            text = stringResource(R.string.today_train_anyway),
            onClick = { actions.onChooseRoutine(if (chip != null) RoutineChoice.Chosen(chip.id) else RoutineChoice.Free) },
            modifier = Modifier.fillMaxWidth(),
            style = BoraButtonStyle.Tonal,
        )
    }
    energyLine(state.energy)
}

/** Today with an active routine: the hero, the exercise list, the quiet actions and the energy line. */
private fun LazyListScope.routineToday(state: TodayUiState, routines: RoutineUi, actions: TodayActions) {
    val editing = state.editing
    item(key = "hero") { RoutineHero(state, routines, actions) }
    item(key = "list") {
        BoraCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_exercises_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (routines.activeId != null && !editing) {
                    QuietTextButton(
                        text = stringResource(R.string.today_edit_ficha),
                        onClick = actions.onStartEditing,
                        leadingIcon = CoreUiR.drawable.ic_edit,
                    )
                }
            }
            routines.machines.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                RoutineExerciseRow(row, index, routines.machines.lastIndex, editing, actions)
            }
        }
    }
    if (routines.alsoThisSession.isNotEmpty() && !editing) {
        item(key = "also") { SectionTitle(stringResource(R.string.today_also_this_session)) }
        item(key = "also_rows") {
            BoraCard(Modifier.fillMaxWidth()) { SessionRows(routines.alsoThisSession, actions) }
        }
    }
    if (!editing) item(key = "quiet") { QuietActions(state.repeatOffer, actions) }
    energyLine(state.energy)
}

/** The yellow hero of a routine: date, name, progress, the switcher and the one filled button (or the edit-mode controls). */
@Composable
private fun RoutineHero(state: TodayUiState, routines: RoutineUi, actions: TodayActions) {
    val resources = LocalContext.current.resources
    val total = routines.machines.size
    val doneCount = routines.machines.count { it.done }
    val editing = state.editing
    val nextName = routines.machines.firstOrNull { it.machine.id == state.nextExerciseId }?.machine?.name
    HeroCard(
        date = dateText(state.today),
        title = routines.activeName.orEmpty(),
        subtitle = if (total > 0) resources.getQuantityString(R.plurals.today_machines_progress, total, doneCount, total) else null,
        ringFraction = if (total > 0) doneCount.toFloat() / total else null,
    ) {
        RoutineSwitcher(routines, actions.onChooseRoutine)
        if (editing) {
            Text(
                stringResource(R.string.today_edit_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = Sunny.Ink,
            )
            BoraButton(
                text = stringResource(R.string.today_done),
                onClick = actions.onFinishEditing,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = CoreUiR.drawable.ic_check,
            )
            BoraButton(
                text = stringResource(R.string.today_edit_sets_reps),
                onClick = actions.onEditRoutine,
                modifier = Modifier.fillMaxWidth(),
                style = BoraButtonStyle.Text,
                leadingIcon = CoreUiR.drawable.ic_edit,
            )
        } else {
            val nextId = state.nextExerciseId
            val action = when {
                state.heroPrimary == HeroAction.START && nextId != null && nextName != null -> HeroAction.START
                state.heroPrimary == HeroAction.CONTINUE && nextId != null && nextName != null -> HeroAction.CONTINUE
                state.heroPrimary == HeroAction.FINISH -> HeroAction.FINISH
                else -> HeroAction.ADD
            }
            when (action) {
                HeroAction.START -> BoraButton(
                    stringResource(R.string.today_hero_start, nextName.orEmpty()),
                    { actions.onOpenMachine(nextId!!) },
                    Modifier.fillMaxWidth(),
                )
                HeroAction.CONTINUE -> BoraButton(
                    stringResource(R.string.today_hero_continue, nextName.orEmpty()),
                    { actions.onOpenMachine(nextId!!) },
                    Modifier.fillMaxWidth(),
                )
                HeroAction.FINISH -> BoraButton(
                    stringResource(R.string.today_hero_finish),
                    actions.onDone,
                    Modifier.fillMaxWidth(),
                    leadingIcon = CoreUiR.drawable.ic_check,
                )
                HeroAction.ADD -> BoraButton(
                    stringResource(R.string.today_add_machine),
                    actions.onAddMachine,
                    Modifier.fillMaxWidth(),
                    leadingIcon = CoreUiR.drawable.ic_add,
                )
            }
            if (state.session != null && action != HeroAction.FINISH) {
                BoraButton(
                    text = stringResource(R.string.today_hero_finish),
                    onClick = actions.onDone,
                    modifier = Modifier.fillMaxWidth(),
                    style = BoraButtonStyle.Text,
                )
            }
        }
    }
}

/** One row of the routine card: picture, name, target and set dots; long press opens the row menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoutineExerciseRow(
    row: RoutineMachineUi,
    index: Int,
    lastIndex: Int,
    editing: Boolean,
    actions: TodayActions,
) {
    var menuOpen by rememberSaveable(row.machine.id) { mutableStateOf(false) }
    val current = row.highlighted && !editing
    val supporting = if (row.machine.kind == MachineKind.CARDIO) {
        row.cardioSummary?.let { summaryLabel(row.machine, it) }
    } else {
        targetText(row.target)
    }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (current) Sunny.BrandSoft else androidx.compose.ui.graphics.Color.Transparent)
                .combinedClickable(
                    enabled = !editing,
                    role = Role.Button,
                    onClick = { actions.onOpenMachine(row.machine.id) },
                    onLongClick = { menuOpen = true },
                )
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val dim = if (row.done && !editing) 0.55f else 1f
            Row(Modifier.weight(1f).alpha(dim), verticalAlignment = Alignment.CenterVertically) {
                RowThumb(row.machine)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.machine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (supporting != null) {
                        Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            when {
                editing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    MoveButtons(
                        canMoveUp = index > 0,
                        canMoveDown = index < lastIndex,
                        onUp = { actions.onMoveMachine(row.machine.id, true) },
                        onDown = { actions.onMoveMachine(row.machine.id, false) },
                    )
                    IconButton(onClick = { actions.onRemoveMachine(row.machine.id) }) {
                        Icon(
                            painterResource(CoreUiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.today_remove_machine, row.machine.name),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                row.done -> DoneCheck(stringResource(R.string.today_machine_done))
                row.machine.kind != MachineKind.CARDIO -> SetDots(row.setCount, row.target.targetSets)
            }
        }
        DropdownMenu(expanded = menuOpen && !editing, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.today_menu_edit)) },
                onClick = { menuOpen = false; actions.onEditMachine(row.machine.id) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.today_menu_skip)) },
                onClick = { menuOpen = false; actions.onMenuSkip(row.machine.id) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.today_menu_remove)) },
                onClick = { menuOpen = false; actions.onRemoveMachine(row.machine.id) },
            )
        }
    }
}

/** Under the list: "Repeat last time" (opens the two repeat options) and "Add exercise", both quiet text buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuietActions(offer: RepeatOfferUi?, actions: TodayActions) {
    val resources = LocalContext.current.resources
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
    ) {
        if (offer != null) {
            var open by rememberSaveable { mutableStateOf(false) }
            Box {
                QuietTextButton(
                    text = stringResource(R.string.today_repeat_menu),
                    onClick = { open = true },
                    trailingIcon = CoreUiR.drawable.ic_expand_more,
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(stringResource(R.string.today_repeat_same), fontWeight = FontWeight.Bold)
                                Text(
                                    resources.machinesAndSets(offer.machineCount, offer.setCount),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = { open = false; actions.onRepeatSame() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.today_repeat_suggested), fontWeight = FontWeight.Bold) },
                        onClick = { open = false; actions.onRepeatSuggested() },
                    )
                }
            }
        }
        QuietTextButton(
            text = stringResource(R.string.today_add_machine),
            onClick = actions.onAddMachine,
            leadingIcon = CoreUiR.drawable.ic_add,
        )
    }
}

/** One quiet grey line with the day's estimated energy need; nothing without an estimate. Never part of the logging path. */
private fun LazyListScope.energyLine(energy: DayEnergyUi?) {
    if (energy == null) return
    item(key = "energy") {
        val amount = NumberFormat.getIntegerInstance().format(energy.kcal)
        Text(
            if (energy.kind == DayKind.REST) stringResource(R.string.today_energy_rest, amount)
            else stringResource(R.string.today_energy_day, amount),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Skip Pull-ups today?": skip for this training, take it out of the ficha, or cancel. */
@Composable
private fun SkipMachineDialog(dialog: SkipDialog, actions: TodayActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissSkip,
        title = { Text(stringResource(R.string.today_skip_title, dialog.machine.name)) },
        text = {
            OutlinedButton(
                onClick = actions.onRemoveFromRoutine,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.today_remove_from, dialog.routineName)) }
        },
        confirmButton = {
            TextButton(onClick = actions.onSkipToday) { Text(stringResource(R.string.today_skip_today)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onDismissSkip) { Text(stringResource(CoreUiR.string.cancel)) }
        },
    )
}

/** Draws the picture and returns the chooser that shares it. Throws when anything fails; the caller shows the failure. */
private fun shareChooser(context: android.content.Context, summary: SummaryUi): android.content.Intent {
    val texts = summaryTexts(context.resources, summary.summary)
    val file = renderSummaryPng(context, summary, texts)
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return android.content.Intent.createChooser(send, null)
}
