package com.boratreino.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.boratreino.core.ui.theme.Sunny
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The Sunny palette: the exact tokens, how the light colour scheme maps them, and the contrast of the key pairs. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class SunnyThemeTest {
    @get:Rule
    val compose = createComposeRule()

    /** The light colour scheme the app shows, read from the theme while composing. */
    private fun lightScheme(): ColorScheme {
        var scheme: ColorScheme? = null
        compose.setContent {
            BoraTreinoTheme {
                scheme = MaterialTheme.colorScheme
            }
        }
        compose.waitForIdle()
        return checkNotNull(scheme)
    }

    // The tokens

    @Test
    fun `the Sunny tokens hold the owner's hex values`() {
        assertEquals(Color(0xFFFEC204), Sunny.Brand)
        assertEquals(Color(0xFFFFF1B8), Sunny.BrandSoft)
        assertEquals(Color(0xFF1F1B16), Sunny.Ink)
        assertEquals(Color(0xFFFAF7F0), Sunny.Page)
        assertEquals(Color(0xFFFFFFFF), Sunny.Card)
        assertEquals(Color(0xFFF3EEE3), Sunny.Tile)
        assertEquals(Color(0xFFEEE8DC), Sunny.Line)
        assertEquals(Color(0xFFD9D1C1), Sunny.Outline)
        assertEquals(Color(0xFF6B645A), Sunny.Muted)
        assertEquals(Color(0xFF2E6B52), Sunny.Ok)
        assertEquals(Color(0xFFDDEBE4), Sunny.OkSoft)
        assertEquals(Color(0xFFE6397F), Sunny.Pink)
        assertEquals(Color(0xFFFDE3EE), Sunny.PinkSoft)
        assertEquals(Color(0xFFB42318), Sunny.Error)
    }

    // The light colour scheme

    @Test
    fun `primary is Ink and onPrimary is white`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Ink, scheme.primary)
        assertEquals(Color.White, scheme.onPrimary)
    }

    @Test
    fun `primaryContainer is the Brand yellow with Ink text on it`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Brand, scheme.primaryContainer)
        assertEquals(Sunny.Ink, scheme.onPrimaryContainer)
    }

    @Test
    fun `secondary is the Ok green with white text and secondaryContainer is the soft green`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Ok, scheme.secondary)
        assertEquals(Color.White, scheme.onSecondary)
        assertEquals(Sunny.OkSoft, scheme.secondaryContainer)
        assertEquals(Color(0xFF173D2E), scheme.onSecondaryContainer)
    }

    @Test
    fun `tertiary is the Pink accent with its soft container`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Pink, scheme.tertiary)
        assertEquals(Color.White, scheme.onTertiary)
        assertEquals(Sunny.PinkSoft, scheme.tertiaryContainer)
        assertEquals(Color(0xFF7A1240), scheme.onTertiaryContainer)
    }

    @Test
    fun `background and surface are the warm page colour`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Page, scheme.background)
        assertEquals(Sunny.Page, scheme.surface)
        assertEquals(Sunny.Ink, scheme.onBackground)
        assertEquals(Sunny.Ink, scheme.onSurface)
    }

    @Test
    fun `surfaceVariant is the tile colour and onSurfaceVariant the muted grey`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Tile, scheme.surfaceVariant)
        assertEquals(Sunny.Muted, scheme.onSurfaceVariant)
    }

    @Test
    fun `the container surfaces are white except the highest which is the tile colour`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Card, scheme.surfaceContainerLowest)
        assertEquals(Sunny.Card, scheme.surfaceContainerLow)
        assertEquals(Sunny.Card, scheme.surfaceContainer)
        assertEquals(Sunny.Card, scheme.surfaceContainerHigh)
        assertEquals(Sunny.Tile, scheme.surfaceContainerHighest)
    }

    @Test
    fun `outline is Outline and outlineVariant is Line`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Outline, scheme.outline)
        assertEquals(Sunny.Line, scheme.outlineVariant)
    }

    @Test
    fun `error stays the red of errors only`() {
        val scheme = lightScheme()

        assertEquals(Color(0xFFB42318), scheme.error)
    }

    @Test
    fun `the inverse roles are Ink, white and Brand`() {
        val scheme = lightScheme()

        assertEquals(Sunny.Ink, scheme.inverseSurface)
        assertEquals(Color.White, scheme.inverseOnSurface)
        assertEquals(Sunny.Brand, scheme.inversePrimary)
    }

    // Contrast

    /** WCAG relative luminance of an sRGB colour. */
    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }

    /** WCAG contrast ratio between two colours, from 1 to 21. */
    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    @Test
    fun `Ink on Brand has a contrast of at least 4_5 to 1`() {
        assertTrue(contrast(Sunny.Ink, Sunny.Brand) >= 4.5)
    }

    @Test
    fun `white on Ink has a contrast of at least 4_5 to 1`() {
        assertTrue(contrast(Color.White, Sunny.Ink) >= 4.5)
    }

    @Test
    fun `white on Ok green has a contrast of at least 4_5 to 1`() {
        assertTrue(contrast(Color.White, Sunny.Ok) >= 4.5)
    }
}
