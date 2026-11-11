package com.boratreino.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boratreino.core.ui.theme.Sunny

/** The three button levels: one [Filled] per screen, [Tonal] (yellow tint) and [Text] for the rest. */
enum class BoraButtonStyle { Filled, Tonal, Text }

/**
 * The app's button. [BoraButtonStyle.Filled] = Ink with white text, 56 dp tall, 18 dp corners; [BoraButtonStyle.Tonal] =
 * BrandSoft with Ink text, same size; [BoraButtonStyle.Text] = no background, Ink text, 44 dp. The label is centred.
 * Disabled buttons are drawn at 38 % alpha.
 */
@Composable
fun BoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BoraButtonStyle = BoraButtonStyle.Filled,
    enabled: Boolean = true,
    @DrawableRes leadingIcon: Int? = null,
) {
    val label: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
    when (style) {
        BoraButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 44.dp),
            enabled = enabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Sunny.Ink,
                disabledContentColor = Sunny.Ink.copy(alpha = DISABLED_ALPHA),
            ),
            content = { label() },
        )
        else -> {
            val filled = style == BoraButtonStyle.Filled
            val container = if (filled) Sunny.Ink else Sunny.BrandSoft
            val content = if (filled) Color.White else Sunny.Ink
            Button(
                onClick = onClick,
                modifier = modifier.heightIn(min = 56.dp),
                enabled = enabled,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = container,
                    contentColor = content,
                    disabledContainerColor = container.copy(alpha = DISABLED_ALPHA),
                    disabledContentColor = content.copy(alpha = DISABLED_ALPHA),
                ),
                content = { label() },
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
