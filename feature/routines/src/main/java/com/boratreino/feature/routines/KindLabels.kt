package com.boratreino.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.boratreino.core.model.MachineKind

/** The kind's name as the editor's chips show it. */
@Composable
internal fun MachineKind.editorLabel(): String = stringResource(
    when (this) {
        MachineKind.WEIGHT_REPS -> R.string.editor_kind_weights
        MachineKind.BODYWEIGHT_REPS -> R.string.editor_kind_bodyweight
        MachineKind.CARDIO -> R.string.editor_kind_cardio
        MachineKind.WEIGHT_TIME -> R.string.editor_kind_weight_time
        MachineKind.WEIGHT_DISTANCE -> R.string.editor_kind_weight_distance
        MachineKind.TIME -> R.string.editor_kind_time
    },
)
