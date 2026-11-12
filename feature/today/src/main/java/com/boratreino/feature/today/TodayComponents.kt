package com.boratreino.feature.today

import android.content.res.Resources
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Machine
import com.boratreino.core.ui.BoraButton
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.theme.Sunny
import com.boratreino.core.ui.R as CoreUiR

/** "3 exercises · 9 sets", with the right singular forms. */
internal fun Resources.machinesAndSets(machines: Int, sets: Int, records: Int = 0): String {
    val base = getString(
        R.string.today_machines_and_sets,
        getQuantityString(R.plurals.today_machines, machines, machines),
        getQuantityString(R.plurals.today_sets, sets, sets),
    )
    return if (records > 0) {
        getString(R.string.today_summary_with_records, base, getQuantityString(R.plurals.today_records, records, records))
    } else {
        base
    }
}

/** A tappable white card with a title and an optional grey line, at least 64 dp tall (the picker's rows). */
@Composable
internal fun MachineRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    BoraCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/** A small grey heading above a group of rows. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 12.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The row picture for a stored machine (the shared 48 dp tile). */
@Composable
internal fun RowThumb(machine: Machine) {
    MachineThumb(machine, size = 48.dp)
}

/** The screen's main action when there is no routine: a filled button with a plus. */
@Composable
internal fun AddButton(text: String, onClick: () -> Unit) {
    BoraButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = CoreUiR.drawable.ic_add,
    )
}

/** A quiet text button (labelLarge, ink, at least 40 dp tall) with an optional icon before or after the label. */
@Composable
internal fun QuietTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: Int? = null,
    trailingIcon: Int? = null,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 40.dp)) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, Modifier.size(18.dp), tint = Sunny.Ink)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Sunny.Ink)
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(painterResource(trailingIcon), contentDescription = null, Modifier.size(18.dp), tint = Sunny.Ink)
        }
    }
}

/** Set dots: one per target set (filled green when done); more than [MAX_DOTS] targets show "n/m" instead. */
@Composable
internal fun SetDots(done: Int, target: Int, modifier: Modifier = Modifier) {
    if (target > MAX_DOTS) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.today_progress, done, target),
            modifier = modifier,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(target) { index ->
            val dot by animateColorAsState(if (index < done) Sunny.Ok else Sunny.Outline, tween(150), label = "setDot")
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        }
    }
}

private const val MAX_DOTS = 6

/** The green round check of a finished exercise. */
@Composable
internal fun DoneCheck(contentDescription: String?) {
    Box(
        Modifier.size(26.dp).clip(CircleShape).background(Sunny.Ok),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(CoreUiR.drawable.ic_check),
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = androidx.compose.ui.graphics.Color.White,
        )
    }
}

/** A thin line between two rows of the same card. */
@Composable
internal fun RowDivider() {
    HorizontalDivider(color = Sunny.Line, thickness = 1.dp)
}

/** The hero's progress ring: ink arc on a translucent ink track, the percentage inside. */
@Composable
internal fun ProgressRing(fraction: Float, modifier: Modifier = Modifier) {
    val ink = Sunny.Ink
    val target = fraction.coerceIn(0f, 1f)
    val clamped by animateFloatAsState(target, tween(400), label = "ring")
    Box(modifier.size(74.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(74.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(ink.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (clamped > 0f) {
                drawArc(ink, -90f, 360f * clamped, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(
            "${(target * 100).toInt()}%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ink,
        )
    }
}
