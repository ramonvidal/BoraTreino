package com.boratreino.feature.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Finding
import com.boratreino.core.model.StallDirection
import com.boratreino.core.ui.BoraCard
import com.boratreino.core.ui.R as CoreUiR

/** What a tap on a finding does. */
internal sealed interface FindingTarget {
    /** Scroll the Overview to the "frequency" item. */
    data object Frequency : FindingTarget

    data class Analysis(val card: AnalysisCard) : FindingTarget

    data class Machine(val id: Long) : FindingTarget
}

internal fun findingTarget(finding: Finding): FindingTarget = when (finding) {
    is Finding.FrequencyDrop -> FindingTarget.Frequency
    is Finding.LowVolume -> FindingTarget.Analysis(AnalysisCard.HARD_SETS)
    is Finding.Stalled -> FindingTarget.Machine(finding.machine.id)
    is Finding.NewRecord -> FindingTarget.Analysis(AnalysisCard.RECORDS)
}

/** One "What matters now" card: an icon tile, a bold title, a muted hint and a chevron. */
@Composable
internal fun FindingCard(finding: Finding, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (icon, tile, tint) = when (finding) {
        is Finding.FrequencyDrop -> Triple(CoreUiR.drawable.ic_calendar, colors.primaryContainer, colors.onPrimaryContainer)
        is Finding.LowVolume -> Triple(CoreUiR.drawable.ic_arrow_downward, colors.primaryContainer, colors.onPrimaryContainer)
        is Finding.Stalled -> when (finding.direction) {
            StallDirection.FLAT -> Triple(CoreUiR.drawable.ic_arrow_forward, colors.surfaceVariant, colors.onSurfaceVariant)
            StallDirection.FALLING -> Triple(CoreUiR.drawable.ic_arrow_downward, colors.surfaceVariant, colors.error)
        }
        is Finding.NewRecord -> Triple(CoreUiR.drawable.ic_emoji_events, colors.secondaryContainer, colors.onSecondaryContainer)
    }
    val title = findingTitle(finding)
    val hint = findingHint(finding)
    BoraCard(modifier = modifier.fillMaxWidth().heightIn(min = 72.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, tile, tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (hint != null) {
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
            }
            Icon(
                painterResource(CoreUiR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = colors.onSurface,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun IconTile(icon: Int, container: Color, tint: Color) {
    Box(
        Modifier.size(48.dp).background(container, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}
