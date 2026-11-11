package com.boratreino.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boratreino.core.ui.R

/*
 * The app has its own "Sunny" look, chosen by the owner and taken from the launcher icon: a warm
 * off-white page, plain white cards, a yellow brand colour (selected things, hero cards), charcoal ink
 * for text and the main button, green only for success (done, records, positive change) and pink
 * sparingly. Red only means an error. The colours are no longer shared with ChoreMe / PodOuvir.
 * Font: Figtree (SIL Open Font License, bundled in res/font; license in assets/licenses).
 */

/** The Sunny palette. Yellow never carries white text: use [Ink] on [Brand]. */
object Sunny {
    val Brand = Color(0xFFFEC204)
    val BrandSoft = Color(0xFFFFF1B8)
    /** Very dark yellow: the strongest step of the body map and muscle cards. */
    val BrandDeep = Color(0xFF8A5A00)
    val Ink = Color(0xFF1F1B16)
    val Page = Color(0xFFFAF7F0)
    val Card = Color(0xFFFFFFFF)
    val Tile = Color(0xFFF3EEE3)
    val Line = Color(0xFFEEE8DC)
    val Outline = Color(0xFFD9D1C1)
    val Muted = Color(0xFF6B645A)
    val Ok = Color(0xFF2E6B52)
    val OkSoft = Color(0xFFDDEBE4)
    val Pink = Color(0xFFE6397F)
    val PinkSoft = Color(0xFFFDE3EE)
    val Error = Color(0xFFB42318)
}

private val LightColors = lightColorScheme(
    primary = Sunny.Ink,
    onPrimary = Color.White,
    primaryContainer = Sunny.Brand,
    onPrimaryContainer = Sunny.Ink,
    secondary = Sunny.Ok,
    onSecondary = Color.White,
    secondaryContainer = Sunny.OkSoft,
    onSecondaryContainer = Color(0xFF173D2E),
    tertiary = Sunny.Pink,
    onTertiary = Color.White,
    tertiaryContainer = Sunny.PinkSoft,
    onTertiaryContainer = Color(0xFF7A1240),
    background = Sunny.Page,
    onBackground = Sunny.Ink,
    surface = Sunny.Page,
    onSurface = Sunny.Ink,
    surfaceVariant = Sunny.Tile,
    onSurfaceVariant = Sunny.Muted,
    surfaceContainerLowest = Sunny.Card,
    surfaceContainerLow = Sunny.Card,
    surfaceContainer = Sunny.Card,
    surfaceContainerHigh = Sunny.Card,
    surfaceContainerHighest = Sunny.Tile,
    outline = Sunny.Outline,
    outlineVariant = Sunny.Line,
    error = Sunny.Error,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = Color(0xFF7A1A12),
    inverseSurface = Sunny.Ink,
    inverseOnSurface = Color.White,
    inversePrimary = Sunny.Brand,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBAE),
    onPrimary = Color(0xFF0B3524),
    primaryContainer = Color(0xFF1F4A38),
    onPrimaryContainer = Color(0xFFCDEBDC),
    secondary = Color(0xFFC6C6C2),
    onSecondary = Color(0xFF2A2A28),
    secondaryContainer = Color(0xFF2E2E2C),
    onSecondaryContainer = Color(0xFFE6E6E2),
    tertiary = Color(0xFFC6C6C2),
    onTertiary = Color(0xFF2A2A28),
    tertiaryContainer = Color(0xFF2E2E2C),
    onTertiaryContainer = Color(0xFFE6E6E2),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE8E8E5),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E5),
    surfaceVariant = Color(0xFF2A2A28),
    onSurfaceVariant = Color(0xFFB0B0AB),
    surfaceContainerLowest = Color(0xFF1E1E1D),
    surfaceContainerLow = Color(0xFF1E1E1D),
    surfaceContainer = Color(0xFF1E1E1D),
    surfaceContainerHigh = Color(0xFF242423),
    surfaceContainerHighest = Color(0xFF2A2A28),
    outline = Color(0xFF6E6E6A),
    outlineVariant = Color(0xFF353533),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A15),
    onErrorContainer = Color(0xFFFFDAD5),
    inverseSurface = Color(0xFFE8E8E5),
    inverseOnSurface = Color(0xFF1C1C1C),
    inversePrimary = Sunny.Ok,
)

val Figtree = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = Figtree,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.3f).sp,
)

private val PlainTypography = Typography(
    displayLarge = style(32, FontWeight.Bold),
    displayMedium = style(32, FontWeight.Bold),
    displaySmall = style(28, FontWeight.Bold),
    headlineLarge = style(28, FontWeight.Bold),
    headlineMedium = style(24, FontWeight.Bold),
    headlineSmall = style(20, FontWeight.Bold),
    titleLarge = style(20, FontWeight.SemiBold),
    titleMedium = style(16, FontWeight.SemiBold),
    titleSmall = style(14, FontWeight.SemiBold),
    bodyLarge = style(16, FontWeight.Normal),
    bodyMedium = style(15, FontWeight.Normal),
    bodySmall = style(13, FontWeight.Normal),
    labelLarge = style(14, FontWeight.SemiBold),
    labelMedium = style(13, FontWeight.SemiBold),
    labelSmall = style(12, FontWeight.Medium),
)

private val PlainShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The app has its own look, so the phone's wallpaper colours (dynamic color) are not used. It also stays in the light look
 * whatever the phone's dark mode says (the owner's choice); [darkTheme] is only turned on by tests and previews.
 */
@Composable
fun BoraTreinoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, typography = PlainTypography, shapes = PlainShapes, content = content)
}
