package com.boratreino.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.ui.label
import com.boratreino.core.ui.R as CoreUiR

/** The exercise a "How to do it" dialog is showing. [starterName] is set when the row came from the starter list. */
internal data class LibraryDetail(
    val exercise: LibraryExercise,
    val starterName: String? = null,
    val added: Boolean = false,
)

@Composable
internal fun LibraryEquipment.label(): String = stringResource(
    when (this) {
        LibraryEquipment.BARBELL -> R.string.library_equipment_barbell
        LibraryEquipment.DUMBBELL -> R.string.library_equipment_dumbbell
        LibraryEquipment.BODY_ONLY -> R.string.library_equipment_body_only
        LibraryEquipment.CABLE -> R.string.library_equipment_cable
        LibraryEquipment.MACHINE -> R.string.library_equipment_machine
        LibraryEquipment.KETTLEBELLS -> R.string.library_equipment_kettlebells
        LibraryEquipment.BANDS -> R.string.library_equipment_bands
        LibraryEquipment.EZ_BAR -> R.string.library_equipment_ez_bar
        LibraryEquipment.MEDICINE_BALL -> R.string.library_equipment_medicine_ball
        LibraryEquipment.EXERCISE_BALL -> R.string.library_equipment_exercise_ball
        LibraryEquipment.FOAM_ROLL -> R.string.library_equipment_foam_roll
        else -> R.string.library_equipment_other
    },
)

@Composable
internal fun LibraryLevel.label(): String = stringResource(
    when (this) {
        LibraryLevel.BEGINNER -> R.string.library_level_beginner
        LibraryLevel.INTERMEDIATE -> R.string.library_level_intermediate
        else -> R.string.library_level_expert
    },
)

/** The translated name of a dataset muscle ("lower back"); an unknown one is shown capitalised as it is. */
@Composable
internal fun libraryMuscleLabel(muscle: String): String {
    val id = when (muscle.trim().lowercase()) {
        "abdominals" -> R.string.library_muscle_abdominals
        "abductors" -> R.string.library_muscle_abductors
        "adductors" -> R.string.library_muscle_adductors
        "biceps" -> R.string.library_muscle_biceps
        "calves" -> R.string.library_muscle_calves
        "chest" -> R.string.library_muscle_chest
        "forearms" -> R.string.library_muscle_forearms
        "glutes" -> R.string.library_muscle_glutes
        "hamstrings" -> R.string.library_muscle_hamstrings
        "lats" -> R.string.library_muscle_lats
        "lower back" -> R.string.library_muscle_lower_back
        "middle back" -> R.string.library_muscle_middle_back
        "neck" -> R.string.library_muscle_neck
        "quadriceps" -> R.string.library_muscle_quadriceps
        "shoulders" -> R.string.library_muscle_shoulders
        "traps" -> R.string.library_muscle_traps
        "triceps" -> R.string.library_muscle_triceps
        else -> null
    }
    return if (id != null) stringResource(id) else muscle.replaceFirstChar { it.uppercase() }
}

/** The numbered steps, in English. */
@Composable
internal fun NumberedSteps(steps: List<String>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A small grey line saying the steps are English; only shown when the app is in Portuguese. */
@Composable
private fun EnglishNote(portuguese: Boolean) {
    if (portuguese) {
        Text(
            stringResource(R.string.library_steps_english),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "How to do it" for one library exercise, with a button that adds just that exercise. */
@Composable
internal fun ExerciseDetailDialog(
    detail: LibraryDetail?,
    portuguese: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (detail == null) return
    val exercise = detail.exercise
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.displayName(portuguese)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine(stringResource(R.string.library_detail_group), exercise.muscleGroup.label())
                if (exercise.primaryMuscles.isNotEmpty()) {
                    DetailLine(
                        stringResource(R.string.library_detail_primary),
                        exercise.primaryMuscles.map { libraryMuscleLabel(it) }.joinToString(", "),
                    )
                }
                if (exercise.secondaryMuscles.isNotEmpty()) {
                    DetailLine(
                        stringResource(R.string.library_detail_secondary),
                        exercise.secondaryMuscles.map { libraryMuscleLabel(it) }.joinToString(", "),
                    )
                }
                DetailLine(stringResource(R.string.library_detail_equipment), exercise.equipment.label())
                DetailLine(stringResource(R.string.library_detail_level), exercise.level.label())
                if (exercise.steps.isNotEmpty()) {
                    Text(
                        stringResource(R.string.library_how_to),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    NumberedSteps(exercise.steps)
                    EnglishNote(portuguese)
                }
            }
        },
        confirmButton = {
            if (detail.added) {
                Text(
                    stringResource(R.string.library_added),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            } else {
                Button(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.library_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.library_close))
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The machine editor's "How to do it" card: folded until tapped. */
@Composable
internal fun HowToCard(steps: List<String>, portuguese: Boolean, modifier: Modifier = Modifier) {
    if (steps.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.library_how_to),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(
                        if (expanded) CoreUiR.drawable.ic_expand_less else CoreUiR.drawable.ic_expand_more,
                    ),
                    contentDescription = stringResource(if (expanded) R.string.routines_collapse else R.string.routines_expand),
                )
            }
            if (expanded) {
                NumberedSteps(steps)
                EnglishNote(portuguese)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 4.dp))
            }
        }
    }
}
