package com.boratreino.feature.today

import com.boratreino.core.ui.cardioText
import com.boratreino.core.ui.formatKg
import com.boratreino.core.ui.weightLabel
import com.boratreino.core.ui.summaryLabel
import com.boratreino.core.ui.draftText
import com.boratreino.core.ui.showUndo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.LinearProgressIndicator
import com.boratreino.core.model.RestTimerRules
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.SetDraft
import com.boratreino.core.model.SetType
import com.boratreino.core.model.WorkoutSet
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.SetTypeChips
import com.boratreino.core.ui.setTypeLabel
import com.boratreino.core.ui.EmptyState
import com.boratreino.core.ui.ExercisePreviewDialog
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.model.StarterMachines
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.boratreino.core.ui.NumberPadDialog
import com.boratreino.core.ui.Stepper
import com.boratreino.core.ui.recordText
import com.boratreino.core.ui.suggestionText
import com.boratreino.core.ui.targetText
import com.boratreino.core.model.Suggestion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.boratreino.core.ui.R as CoreUiR

/** What the machine screen can ask for. */
internal data class MachineActions(
    val onBack: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onStepWeight: (up: Boolean) -> Unit = {},
    val onStepReps: (up: Boolean) -> Unit = {},
    val onOpenPad: (PadTarget) -> Unit = {},
    val onConfirmPad: (String) -> Unit = {},
    val onDismissPad: () -> Unit = {},
    val onLogSet: () -> Unit = {},
    val onSameAgain: () -> Unit = {},
    val onToggleWarmup: () -> Unit = {},
    val onOpenWarmups: () -> Unit = {},
    val onCloseWarmups: () -> Unit = {},
    val onLogWarmups: () -> Unit = {},
    val onEditorType: (SetType) -> Unit = {},
    val onOpenSet: (WorkoutSet) -> Unit = {},
    val onStepEditorWeight: (up: Boolean) -> Unit = {},
    val onStepEditorReps: (up: Boolean) -> Unit = {},
    val onSaveEditor: () -> Unit = {},
    val onDeleteEditor: () -> Unit = {},
    val onDeleteSet: (WorkoutSet) -> Unit = {},
    val onOpenNext: (machineId: Long) -> Unit = {},
    val onExtraSet: () -> Unit = {},
    val onDismissEditor: () -> Unit = {},
    val onSkipRest: () -> Unit = {},
    val onExtendRest: () -> Unit = {},
    val onStepMinutes: (up: Boolean) -> Unit = {},
    val onStepCalories: (up: Boolean) -> Unit = {},
    val onStepDistance: (up: Boolean) -> Unit = {},
    val onShowDistance: () -> Unit = {},
    val onLogCardio: () -> Unit = {},
    val onCardioSameAgain: () -> Unit = {},
    val onOpenCardioSet: (WorkoutSet) -> Unit = {},
    val onStepEditorMinutes: (up: Boolean) -> Unit = {},
    val onStepEditorCalories: (up: Boolean) -> Unit = {},
    val onStepEditorDistance: (up: Boolean) -> Unit = {},
    val onSaveCardioEditor: () -> Unit = {},
    val onDeleteCardioEditor: () -> Unit = {},
    val onDismissCardioEditor: () -> Unit = {},
)

@Composable
internal fun MachineScreen(
    onBack: () -> Unit,
    onEdit: (machineId: Long) -> Unit,
    onRoutineFinished: () -> Unit = onBack,
    onOpenMachine: (machineId: Long) -> Unit = {},
    viewModel: MachineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val removedText = stringResource(R.string.today_set_removed)
    val undoLabel = stringResource(CoreUiR.string.undo)
    val failedText = stringResource(CoreUiR.string.error_generic)
    val resources = LocalContext.current.resources

    LaunchedEffect(viewModel) {
        viewModel.restFinished.collect {
            // Rest is over: the same three ticks as a record, so it is felt in a pocket.
            repeat(3) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                delay(140)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Only sent once the repository write has finished.
                is MachineEvent.Logged -> launch {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (event.record != null) {
                        // A record buzzes three times, so it can be told from a normal set without looking.
                        repeat(2) {
                            delay(140)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                }
                is MachineEvent.SetRemoved -> launch {
                    if (snackbar.showUndo(removedText, undoLabel)) viewModel.restoreSet(event.set)
                }
                is MachineEvent.WarmupsLogged -> launch {
                    val count = event.outcome.setIds.size
                    val text = resources.getQuantityString(R.plurals.today_warmups_logged, count, count)
                    if (snackbar.showUndo(text, undoLabel)) viewModel.undoWarmups(event.outcome)
                }
                MachineEvent.RoutineFinished -> onRoutineFinished()
                MachineEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
            }
        }
    }

    MachineContent(
        state = state,
        actions = MachineActions(
            onBack = onBack,
            onEdit = { state.machine?.let { onEdit(it.id) } },
            onStepWeight = viewModel::stepWeight,
            onStepReps = viewModel::stepReps,
            onOpenPad = viewModel::openPad,
            onConfirmPad = viewModel::confirmPad,
            onDismissPad = viewModel::dismissPad,
            onLogSet = viewModel::logSet,
            onSameAgain = viewModel::logSameAgain,
            onToggleWarmup = viewModel::toggleWarmup,
            onOpenWarmups = viewModel::openWarmups,
            onCloseWarmups = viewModel::closeWarmups,
            onLogWarmups = viewModel::logWarmups,
            onEditorType = viewModel::setEditorType,
            onOpenSet = viewModel::openSet,
            onStepEditorWeight = viewModel::stepEditorWeight,
            onStepEditorReps = viewModel::stepEditorReps,
            onSaveEditor = viewModel::saveEditor,
            onDeleteEditor = viewModel::deleteEditor,
            onDeleteSet = viewModel::deleteSet,
            onOpenNext = onOpenMachine,
            onExtraSet = viewModel::showExtraSet,
            onDismissEditor = viewModel::closeEditor,
            onSkipRest = viewModel::skipRest,
            onExtendRest = viewModel::extendRest,
            onStepMinutes = viewModel::stepMinutes,
            onStepCalories = viewModel::stepCalories,
            onStepDistance = viewModel::stepDistance,
            onShowDistance = viewModel::showDistance,
            onLogCardio = viewModel::logCardio,
            onCardioSameAgain = viewModel::logCardioSameAgain,
            onOpenCardioSet = viewModel::openCardioSet,
            onStepEditorMinutes = viewModel::stepEditorMinutes,
            onStepEditorCalories = viewModel::stepEditorCalories,
            onStepEditorDistance = viewModel::stepEditorDistance,
            onSaveCardioEditor = viewModel::saveCardioEditor,
            onDeleteCardioEditor = viewModel::deleteCardioEditor,
            onDismissCardioEditor = viewModel::closeCardioEditor,
        ),
        snackbarHostState = snackbar,
    )
}

/** Drawn from its state alone, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MachineContent(
    state: MachineUiState,
    actions: MachineActions,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val machine = state.machine
    val isCardio = machine?.kind == MachineKind.CARDIO
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(machine?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(painterResource(CoreUiR.drawable.ic_back), stringResource(CoreUiR.string.back))
                    }
                },
                actions = {
                    if (machine != null) {
                        IconButton(onClick = actions.onEdit) {
                            Icon(painterResource(CoreUiR.drawable.ic_edit), stringResource(R.string.today_edit_machine))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (state.loaded && machine != null) {
                when {
                    state.exerciseComplete && !state.extraSet -> CompletePanel(state, actions)
                    isCardio -> CardioLogPanel(state, actions)
                    else -> LogPanel(machine, state, actions)
                }
            }
        },
    ) { innerPadding ->
        when {
            !state.loaded -> Unit
            machine == null -> EmptyState(
                title = stringResource(R.string.today_machine_not_found),
                modifier = Modifier.padding(innerPadding),
            )
            else -> {
                val scroll = rememberScrollState()
                var shownCount by remember { mutableIntStateOf(state.sets.size) }
                LaunchedEffect(state.sets.size) {
                    // A newly logged set: bring its row (and the next one) into view.
                    if (state.sets.size > shownCount) scroll.animateScrollTo(Int.MAX_VALUE)
                    shownCount = state.sets.size
                }
                Column(
                    Modifier.fillMaxSize().padding(innerPadding).verticalScroll(scroll).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InfoCard(machine, state)
                    state.restSeconds?.let { RestBar(it, state.restTotalSeconds, actions.onExtendRest, actions.onSkipRest) }
                    state.record?.let { RecordCard(recordText(it)) }
                    SetTable(machine, state, if (isCardio) actions.onOpenCardioSet else actions.onOpenSet, actions.onDeleteSet)
                }
            }
        }
    }

    val editor = state.editor
    if (machine != null && editor != null) {
        EditSetSheet(machine, editor, actions)
    }
    val cardioEditor = state.cardioEditor
    if (machine != null && cardioEditor != null) {
        EditCardioSheet(cardioEditor, actions)
    }
    if (machine != null && state.warmupDialog && state.warmupPlan.isNotEmpty()) {
        WarmupDialog(machine, state.warmupPlan, actions)
    }
    val pad = state.pad
    if (machine != null && pad != null) {
        val title = when (pad.target) {
            PadTarget.WEIGHT, PadTarget.EDIT_WEIGHT -> weightLabel(machine)
            PadTarget.REPS, PadTarget.EDIT_REPS -> stringResource(R.string.today_reps)
        }
        NumberPadDialog(
            title = title,
            initial = pad.initial,
            allowDecimal = pad.target == PadTarget.WEIGHT || pad.target == PadTarget.EDIT_WEIGHT,
            onConfirm = actions.onConfirmPad,
            onDismiss = actions.onDismissPad,
        )
    }
}

/** Setup note and "Last time" in one white card. */
@Composable
private fun InfoCard(machine: Machine, state: MachineUiState) {
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).withZone(ZoneId.systemDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val hasPicture = StarterMachines.imageFor(machine.name) != null
        var preview by remember { mutableStateOf(false) }
        MachineThumb(machine, size = 56.dp, onClick = if (hasPicture) ({ preview = true }) else null)
        if (preview) ExercisePreviewDialog(machine, onDismiss = { preview = false })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (!machine.setupNote.isNullOrBlank()) {
                Text(
                    machine.setupNote!!,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val last = state.lastTime
            val lastAt = state.lastTimeAt
            Text(
                text = when {
                    last == null -> stringResource(R.string.today_first_time)
                    lastAt == null -> stringResource(R.string.today_last_time, summaryLabel(machine, last))
                    else -> stringResource(
                        R.string.today_last_time_on,
                        summaryLabel(machine, last),
                        dateFormat.format(lastAt),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
            )
            state.target?.let {
                Text(
                    stringResource(R.string.today_target, targetText(it)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.suggestion?.let { suggestion ->
                Text(suggestionText(suggestion), style = MaterialTheme.typography.titleMedium)
                if (suggestion is Suggestion.Down) {
                    Text(
                        stringResource(R.string.today_missed_twice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        }
    }
}

/** Text for one logged set, by machine kind. */
@Composable
private fun setValueText(machine: Machine, set: WorkoutSet): String =
    if (machine.kind == MachineKind.CARDIO) {
        val d = CardioRules.toDraft(set)
        cardioText(d.minutes, d.calories, d.distanceM)
    } else {
        draftText(machine, set.weightKg ?: 0.0, set.reps ?: 0)
    }

/**
 * Set | Previous | Today. One row per set of this session (tap = edit), then one muted "next"
 * row showing the stepper values about to be logged. Rows added after the first composition
 * slide in and flash the accent tint.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SetTable(
    machine: Machine,
    state: MachineUiState,
    onOpenSet: (WorkoutSet) -> Unit,
    onDeleteSet: (WorkoutSet) -> Unit,
) {
    val sets = state.sets
    val isCardio = machine.kind == MachineKind.CARDIO
    val none = stringResource(R.string.machine_no_previous)
    val previous = state.lastTimeSets
    val previousTexts = (0..sets.size).map { i -> previous.getOrNull(i)?.let { setValueText(machine, it) } ?: none }
    val todayTexts = sets.map { setValueText(machine, it) }
    val nextText = if (isCardio) {
        cardioText(state.cardio.minutes, state.cardio.calories, state.cardio.distanceM)
    } else {
        draftText(machine, state.weightKg, state.reps)
    }
    val initialIds = remember { sets.map { it.id }.toSet() }
    val recordId = if (state.record != null) sets.lastOrNull()?.id else null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TableCell(stringResource(R.string.machine_col_set), Modifier.weight(0.4f), header = true)
                TableCell(stringResource(R.string.machine_col_previous), Modifier.weight(1.2f), header = true)
                TableCell(stringResource(R.string.machine_col_today), Modifier.weight(1.3f), header = true)
            }
            sets.forEachIndexed { index, set ->
                key(set.id) {
                    val fresh = set.id !in initialIds
                    val visible = remember { MutableTransitionState(!fresh) }
                    LaunchedEffect(Unit) { visible.targetState = true }
                    AnimatedVisibility(
                        visibleState = visible,
                        enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    ) {
                        val flash = remember { Animatable(if (fresh) 1f else 0f) }
                        LaunchedEffect(Unit) { if (fresh) flash.animateTo(0f, tween(600)) }
                        val tint = MaterialTheme.colorScheme.primary
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                        Row(
                            Modifier.fillMaxWidth()
                                .drawBehind { drawRect(tint.copy(alpha = 0.18f * flash.value)) }
                                .heightIn(min = 52.dp)
                                .combinedClickable(
                                    onClickLabel = stringResource(R.string.today_edit_set),
                                    onClick = { onOpenSet(set) },
                                    onLongClick = { menuOpen = true },
                                )
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TableCell("${index + 1}", Modifier.weight(0.4f))
                            TableCell(previousTexts[index], Modifier.weight(1.2f))
                            Row(Modifier.weight(1.3f), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f, fill = false)) {
                                    Text(
                                        todayTexts[index],
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (set.type != SetType.NORMAL) {
                                        Text(
                                            setTypeLabel(set.type),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Icon(
                                    painterResource(CoreUiR.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                if (set.id == recordId) {
                                    Icon(
                                        painterResource(CoreUiR.drawable.ic_emoji_events),
                                        contentDescription = stringResource(R.string.machine_record_mark),
                                        modifier = Modifier.padding(start = 4.dp).size(18.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.machine_menu_edit)) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSet(set)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.today_delete)) },
                                onClick = {
                                    menuOpen = false
                                    onDeleteSet(set)
                                },
                            )
                        }
                        }
                    }
                }
            }
            // The set about to be logged.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TableCell("${sets.size + 1}", Modifier.weight(0.4f))
                TableCell(previousTexts[sets.size], Modifier.weight(1.2f))
                Text(
                    nextText,
                    modifier = Modifier.weight(1.3f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun TableCell(text: String, modifier: Modifier, header: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One slim row while a rest runs: "Rest 1:12", a thin progress line, +30 s and Skip. */
@Composable
private fun RestBar(remainingSeconds: Int, totalSeconds: Int, onExtend: () -> Unit, onSkip: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.today_rest, RestTimerRules.format(remainingSeconds)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onExtend, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(stringResource(R.string.today_rest_extend, RestTimerRules.EXTEND_SECONDS))
                }
                TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 40.dp)) {
                    Text(stringResource(R.string.today_rest_skip))
                }
            }
            LinearProgressIndicator(
                progress = { RestTimerRules.progress(remainingSeconds, totalSeconds) },
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).heightIn(max = 4.dp),
                drawStopIndicator = {},
            )
        }
    }
}

/** The fixed bottom panel: weight and reps side by side, the warm-up chip, then Same again and Log set. */
@Composable
private fun LogPanel(machine: Machine, state: MachineUiState, actions: MachineActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigStepper(
                    label = weightLabel(machine),
                    value = formatKg(state.weightKg),
                    onMinus = { actions.onStepWeight(false) },
                    onPlus = { actions.onStepWeight(true) },
                    onValueClick = { actions.onOpenPad(PadTarget.WEIGHT) },
                    modifier = Modifier.weight(1f),
                )
                BigStepper(
                    label = stringResource(R.string.today_reps),
                    value = state.reps.toString(),
                    onMinus = { actions.onStepReps(false) },
                    onPlus = { actions.onStepReps(true) },
                    onValueClick = { actions.onOpenPad(PadTarget.REPS) },
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BoraChip(
                    label = setTypeLabel(SetType.WARMUP),
                    selected = state.warmupOn,
                    onClick = actions.onToggleWarmup,
                )
                if (state.warmupPlan.isNotEmpty()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = actions.onOpenWarmups) {
                        Text(stringResource(R.string.today_warmup_sets))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val same = state.sameAgain
                if (same != null) {
                    OutlinedButton(
                        onClick = actions.onSameAgain,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.today_same_again),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                draftText(machine, same.weightKg, same.reps),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                // The one green button on the screen, and the biggest target.
                Button(
                    onClick = actions.onLogSet,
                    modifier = Modifier.weight(if (same != null) 1.2f else 1f).heightIn(min = 64.dp),
                ) {
                    Text(
                        stringResource(R.string.today_log_set),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }
}

/** Replaces the Log panel when the exercise has all its programmed sets: what is next, one tap each. */
@Composable
private fun CompletePanel(state: MachineUiState, actions: MachineActions) {
    val programmed = state.programmedSets ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(CoreUiR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column {
                    Text(
                        stringResource(R.string.machine_complete_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.machine_complete_sets, minOf(state.doneSets, programmed), programmed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.nextExercises.isNotEmpty()) {
                Text(
                    stringResource(R.string.machine_next_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.nextExercises.forEach { next ->
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable { actions.onOpenNext(next.machine.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        MachineThumb(next.machine, size = 40.dp)
                        Text(
                            next.machine.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            targetText(next.target),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(onClick = actions.onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(
                    stringResource(R.string.machine_complete_back),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            TextButton(onClick = actions.onExtraSet, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.machine_complete_extra))
            }
        }
    }
}

/** The optional warm-up suggestion: one line per set, one tap to log them all. */
@Composable
private fun WarmupDialog(machine: Machine, plan: List<SetDraft>, actions: MachineActions) {
    AlertDialog(
        onDismissRequest = actions.onCloseWarmups,
        title = { Text(stringResource(R.string.today_warmup_sets)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plan.forEach { Text(draftText(machine, it.weightKg, it.reps), style = MaterialTheme.typography.titleMedium) }
            }
        },
        confirmButton = {
            TextButton(onClick = actions.onLogWarmups) { Text(stringResource(R.string.today_log_warmups)) }
        },
        dismissButton = {
            TextButton(onClick = actions.onCloseWarmups) { Text(stringResource(R.string.today_close)) }
        },
    )
}

/** A stepper value for cardio: "-" when 0 (not logged). */
private fun cardioValue(value: Int, format: (Int) -> String = { it.toString() }): String =
    if (value > 0) format(value) else "-"

private fun kmValue(distanceM: Int): String = cardioValue(distanceM) { formatKg(it / 1000.0) }

/** The minutes, calories and (when [showDistance]) distance steppers. Shared by the log panel and the editor. */
@Composable
private fun CardioSteppers(
    draft: CardioDraft,
    showDistance: Boolean,
    onShowDistance: () -> Unit,
    onMinutes: (up: Boolean) -> Unit,
    onCalories: (up: Boolean) -> Unit,
    onDistance: (up: Boolean) -> Unit,
) {
    Stepper(
        label = stringResource(R.string.today_minutes),
        value = cardioValue(draft.minutes),
        onMinus = { onMinutes(false) },
        onPlus = { onMinutes(true) },
        onValueClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
    Stepper(
        label = stringResource(R.string.today_calories),
        value = cardioValue(draft.calories),
        onMinus = { onCalories(false) },
        onPlus = { onCalories(true) },
        onValueClick = {},
        modifier = Modifier.fillMaxWidth(),
    )
    if (showDistance) {
        Stepper(
            label = stringResource(R.string.today_distance),
            value = kmValue(draft.distanceM),
            onMinus = { onDistance(false) },
            onPlus = { onDistance(true) },
            onValueClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        TextButton(onClick = onShowDistance, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.today_add_distance))
        }
    }
}

/** The cardio bottom half: minutes / calories / distance steppers, then Same again and Log. No rest timer. */
@Composable
private fun CardioLogPanel(state: MachineUiState, actions: MachineActions) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CardioSteppers(
                draft = state.cardio,
                showDistance = state.cardioDistanceShown,
                onShowDistance = actions.onShowDistance,
                onMinutes = actions.onStepMinutes,
                onCalories = actions.onStepCalories,
                onDistance = actions.onStepDistance,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val same = state.cardioSameAgain
                if (same != null) {
                    OutlinedButton(
                        onClick = actions.onCardioSameAgain,
                        modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.today_same_again),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                cardioText(same.minutes, same.calories, same.distanceM),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Button(
                    onClick = actions.onLogCardio,
                    enabled = CardioRules.canLog(state.cardio),
                    modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                ) {
                    Text(
                        stringResource(R.string.today_log),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

/** Fix or delete a logged cardio set: the same steppers, Save and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCardioSheet(editor: CardioEditor, actions: MachineActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismissCardioEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_set_number, editor.number), style = MaterialTheme.typography.titleLarge)
            var distanceShown by remember { mutableStateOf(editor.draft.distanceM > 0) }
            CardioSteppers(
                draft = editor.draft,
                showDistance = distanceShown,
                onShowDistance = { distanceShown = true },
                onMinutes = actions.onStepEditorMinutes,
                onCalories = actions.onStepEditorCalories,
                onDistance = actions.onStepEditorDistance,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = actions.onDeleteCardioEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.today_delete), Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = actions.onSaveCardioEditor,
                    enabled = CardioRules.canLog(editor.draft),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Text(stringResource(CoreUiR.string.save))
                }
            }
        }
    }
}

/** Fix or delete a logged set: the two steppers, Save and Delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSetSheet(machine: Machine, editor: SetEditor, actions: MachineActions) {
    ModalBottomSheet(
        onDismissRequest = actions.onDismissEditor,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.today_set_number, editor.number), style = MaterialTheme.typography.titleLarge)
            Stepper(
                label = weightLabel(machine),
                value = formatKg(editor.weightKg),
                onMinus = { actions.onStepEditorWeight(false) },
                onPlus = { actions.onStepEditorWeight(true) },
                onValueClick = { actions.onOpenPad(PadTarget.EDIT_WEIGHT) },
                modifier = Modifier.fillMaxWidth(),
            )
            Stepper(
                label = stringResource(R.string.today_reps),
                value = editor.reps.toString(),
                onMinus = { actions.onStepEditorReps(false) },
                onPlus = { actions.onStepEditorReps(true) },
                onValueClick = { actions.onOpenPad(PadTarget.EDIT_REPS) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(CoreUiR.string.set_type_title), style = MaterialTheme.typography.labelLarge)
            SetTypeChips(selected = editor.type, onSelect = actions.onEditorType)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = actions.onDeleteEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(painterResource(CoreUiR.drawable.ic_delete), contentDescription = null)
                    Text(stringResource(R.string.today_delete), Modifier.padding(start = 8.dp))
                }
                Button(
                    onClick = actions.onSaveEditor,
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                ) {
                    Text(stringResource(CoreUiR.string.save))
                }
            }
        }
    }
}

/** "New record: 125 kg" as a one-line chip, shown for a few seconds after the set is logged. */
@Composable
private fun RecordCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(CoreUiR.drawable.ic_emoji_events), contentDescription = null, modifier = Modifier.size(20.dp))
            Text(text, style = MaterialTheme.typography.titleSmall)
        }
    }
}
