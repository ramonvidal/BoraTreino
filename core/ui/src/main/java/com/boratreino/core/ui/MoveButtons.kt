package com.boratreino.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Up and down arrows for reordering a row; an arrow that can't move is disabled and dimmed. */
@Composable
fun MoveButtons(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        IconButton(onClick = onUp, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_upward),
                contentDescription = stringResource(R.string.move_up),
                tint = LocalContentColor.current,
                modifier = Modifier.alpha(if (canMoveUp) 1f else 0.3f),
            )
        }
        IconButton(onClick = onDown, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_downward),
                contentDescription = stringResource(R.string.move_down),
                tint = LocalContentColor.current,
                modifier = Modifier.alpha(if (canMoveDown) 1f else 0.3f),
            )
        }
    }
}
