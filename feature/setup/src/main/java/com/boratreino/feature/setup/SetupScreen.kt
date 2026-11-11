package com.boratreino.feature.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraButtonStyle
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.BoraChip
import com.boratreino.core.ui.Mascot
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.label
import com.boratreino.core.ui.shortLabel
import java.time.DayOfWeek
import com.boratreino.core.ui.R as CoreUiR

@Composable
internal fun SetupScreen(onDone: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val defaultName = stringResource(R.string.setup_routine_default_name)
    SetupContent(
        state = state,
        callbacks = SetupCallbacks(
            onQuery = viewModel::setQuery,
            onToggleStarter = viewModel::toggleStarter,
            onToggleExercise = viewModel::toggleExercise,
            onRoutineName = viewModel::setRoutineName,
            onToggleDay = viewModel::toggleDay,
            onNext = { viewModel.next(defaultName, onDone) },
            onBack = viewModel::back,
            onSkip = onDone,
        ),
    )
}

/** One of the three steps, drawn from its state alone: step indicator and Skip on top, Back and Next at the bottom. */
@Composable
internal fun SetupContent(state: SetupUiState, callbacks: SetupCallbacks, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { StepHeader(state.step, callbacks.onSkip) },
        bottomBar = { StepButtons(state, callbacks) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxWidth()) {
            when (state.step) {
                0 -> GymStep(state, callbacks)
                1 -> RoutineStep(state, callbacks)
                else -> DaysStep(state, callbacks)
            }
        }
    }
}

@Composable
private fun StepHeader(step: Int, onSkip: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.setup_step_of, step + 1, SETUP_STEPS),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(SETUP_STEPS) { index ->
                    val color = if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(Modifier.size(width = 24.dp, height = 6.dp).clip(CircleShape).background(color))
                }
            }
        }
        BoraButton(text = stringResource(R.string.setup_skip), onClick = onSkip, style = BoraButtonStyle.Text)
    }
}

@Composable
private fun StepButtons(state: SetupUiState, callbacks: SetupCallbacks) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.step > 0) {
            BoraButton(
                text = stringResource(R.string.setup_back),
                onClick = callbacks.onBack,
                style = BoraButtonStyle.Tonal,
                enabled = !state.busy,
                modifier = Modifier.weight(1f),
            )
        }
        BoraButton(
            text = stringResource(if (state.isLastStep) R.string.setup_finish else R.string.setup_next),
            onClick = callbacks.onNext,
            enabled = !state.busy,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
private fun StepTitle(title: String, hint: String) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Step 1: the starter exercises by muscle group, one tap each, with a search box and a counter. */
@Composable
private fun GymStep(state: SetupUiState, callbacks: SetupCallbacks) {
    val count = LocalContext.current.resources
        .getQuantityString(R.plurals.setup_selected_count, state.selected.size, state.selected.size)
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Mascot(state.mascotSex, size = 72.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.setup_gym_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item(key = "hint") {
            Text(
                stringResource(R.string.setup_gym_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = callbacks.onQuery,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.setup_search)) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
        item(key = "count") {
            Text(
                count,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        items(state.groups, key = { it.muscleGroup.name }) { group ->
            GroupCard(group, state, callbacks)
        }
    }
}

@Composable
private fun GroupCard(group: SetupGroup, state: SetupUiState, callbacks: SetupCallbacks) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
            group.muscleGroup.label(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        BoraCard(Modifier.fillMaxWidth()) {
            group.starters.forEach { starter ->
                ExerciseRow(
                    name = starter.displayName(state.portuguese),
                    thumbName = starter.name,
                    muscleGroup = starter.muscleGroup,
                    checked = starter.name in state.selected,
                    onClick = { callbacks.onToggleStarter(starter) },
                )
            }
        }
    }
}

/** A checkable row: thumbnail, name and the yellow check circle. The whole row is the tap target. */
@Composable
private fun ExerciseRow(name: String, thumbName: String, muscleGroup: MuscleGroup, checked: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MachineThumb(name = thumbName, muscleGroup = muscleGroup, size = 40.dp)
        Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        CheckCircle(checked)
    }
}

/** Step 2: the exercises chosen in step 1 (all ticked) and the routine's name. */
@Composable
private fun RoutineStep(state: SetupUiState, callbacks: SetupCallbacks) {
    val defaultName = stringResource(R.string.setup_routine_default_name)
    Column(Modifier.fillMaxWidth()) {
        if (state.chosen.isEmpty()) {
            StepTitle(stringResource(R.string.setup_routine_title), stringResource(R.string.setup_routine_none))
            return@Column
        }
        StepTitle(stringResource(R.string.setup_routine_title), stringResource(R.string.setup_routine_hint))
        OutlinedTextField(
            value = state.routineName ?: defaultName,
            onValueChange = callbacks.onRoutineName,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            label = { Text(stringResource(R.string.setup_routine_name)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            item(key = "card") {
                BoraCard(Modifier.fillMaxWidth()) {
                    state.chosen.forEach { exercise ->
                        ExerciseRow(
                            name = exercise.name,
                            thumbName = exercise.starter.name,
                            muscleGroup = exercise.starter.muscleGroup,
                            checked = exercise.machineId !in state.unticked,
                            onClick = { callbacks.onToggleExercise(exercise.machineId) },
                        )
                    }
                }
            }
        }
    }
}

/** Step 3: Monday to Sunday chips. */
@Composable
private fun DaysStep(state: SetupUiState, callbacks: SetupCallbacks) {
    // Computed outside the chip row so no composable is called from a non-inline lambda.
    val labels = DayOfWeek.values().map { it to it.shortLabel() }
    Column(Modifier.fillMaxWidth()) {
        StepTitle(stringResource(R.string.setup_days_title), stringResource(R.string.setup_days_hint))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            labels.forEach { (day, label) ->
                BoraChip(
                    label = label,
                    selected = day in state.days,
                    onClick = { callbacks.onToggleDay(day) },
                )
            }
        }
    }
}

/** The selection mark: a yellow circle with a check when selected, an empty outlined circle otherwise. */
@Composable
private fun CheckCircle(checked: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(24.dp).clip(CircleShape)
            .then(if (checked) Modifier.background(scheme.primaryContainer) else Modifier.border(2.dp, scheme.outline, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painterResource(CoreUiR.drawable.ic_check),
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
