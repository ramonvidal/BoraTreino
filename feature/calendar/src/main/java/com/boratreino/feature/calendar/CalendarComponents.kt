package com.boratreino.feature.calendar

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.theme.Sunny
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.boratreino.core.ui.R as CoreUiR

/** "6 exercises", with the right singular form. */
internal fun Resources.machinesText(count: Int): String = getQuantityString(R.plurals.calendar_machines, count, count)

/** "18 sets", with the right singular form. */
internal fun Resources.setsText(count: Int): String = getQuantityString(R.plurals.calendar_sets, count, count)

/** "Mon 2 Nov". */
internal fun dayTitleFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())

/** A dashed circular outline of [color], drawn inside the bounds of the element (a planned day). */
internal fun Modifier.dashedCircle(color: Color, width: Dp = 1.5.dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val dash = floatArrayOf(DASH_ON * stroke, DASH_OFF * stroke)
    drawCircle(
        color = color,
        radius = (size.minDimension - stroke) / 2f,
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(dash)),
    )
}

private const val DASH_ON = 2.5f
private const val DASH_OFF = 2f

/** How a day is marked: [Trained] filled green, [Planned] a dashed outline, [Rest] a light fill (or nothing). */
internal enum class DayMarkKind { Trained, Planned, Rest }

/**
 * The round mark shared by the week strip and the month grid: [Trained] = filled success green with white [content],
 * [Planned] = dashed Sunny outline, [Rest] = [restFill]. [ring] draws today's ink ring, [selected] the yellow
 * circle (behind a planned or rest mark, around a trained one).
 */
@Composable
internal fun DayMark(
    kind: DayMarkKind,
    size: Dp,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    selected: Boolean = false,
    restFill: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = when {
        kind == DayMarkKind.Trained -> scheme.secondary
        selected -> scheme.primaryContainer
        kind == DayMarkKind.Rest -> restFill
        else -> Color.Transparent
    }
    var mark = modifier.size(size).background(fill, CircleShape)
    if (kind == DayMarkKind.Planned) mark = mark.dashedCircle(Sunny.Outline)
    if (selected && kind == DayMarkKind.Trained) mark = mark.drawBehind {
        drawCircle(color = scheme.primaryContainer, style = Stroke(width = 3.dp.toPx()))
    }
    if (isToday) mark = mark.drawBehind {
        drawCircle(color = scheme.primary, radius = (this.size.minDimension - 2.dp.toPx()) / 2f, style = Stroke(width = 2.dp.toPx()))
    }
    Box(mark, contentAlignment = Alignment.Center) { content() }
}

/** The text of a trained mark is white, of the others ink. */
@Composable
internal fun markContentColor(kind: DayMarkKind): Color =
    if (kind == DayMarkKind.Trained) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface

/** A tick for a trained day without a routine letter. */
@Composable
internal fun CheckMark(size: Dp = 18.dp) {
    Icon(
        painterResource(CoreUiR.drawable.ic_check),
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = MaterialTheme.colorScheme.onSecondary,
    )
}

/** The two-line text of a grid cell: the day number over the routine letter. */
@Composable
internal fun CellText(number: String, letter: String?, color: Color, bold: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            number,
            style = if (letter == null) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelSmall,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
            color = color,
            textAlign = TextAlign.Center,
            lineHeight = if (letter == null) TextUnit.Unspecified else 12.sp,
            maxLines = 1,
        )
        if (letter != null) {
            Text(
                letter,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                maxLines = 1,
            )
        }
    }
}

/** A text button in the error colour with the look of a [BoraButtonStyle.Text] button (BoraButton has no colour). */
@Composable
internal fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: Int? = null) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        if (leadingIcon != null) {
            Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}
