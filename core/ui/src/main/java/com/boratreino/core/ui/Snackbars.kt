package com.boratreino.core.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.withTimeoutOrNull

/** How long an Undo snackbar stays. */
private const val UNDO_MILLIS = 5_000L

/** Shows [message] with an Undo action for 5 s, replacing any snackbar showing; true when Undo was tapped. */
suspend fun SnackbarHostState.showUndo(message: String, undoLabel: String): Boolean {
    currentSnackbarData?.dismiss()
    val result = withTimeoutOrNull(UNDO_MILLIS) {
        showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Indefinite)
    }
    return result == SnackbarResult.ActionPerformed
}
