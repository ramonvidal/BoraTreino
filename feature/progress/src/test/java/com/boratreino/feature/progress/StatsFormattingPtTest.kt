package com.boratreino.feature.progress

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The volume text in Baianês: the decimal separator follows the locale. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR")
class StatsFormattingPtTest {
    @get:Rule
    val compose = createComposeRule()

    private fun volume(kg: Double): String {
        var text = ""
        compose.setContent { text = volumeText(kg) }
        compose.waitForIdle()
        return text
    }

    @Test
    fun `tonnes use a decimal comma`() {
        assertEquals("12,4 t", volume(12_400.0))
    }

    @Test
    fun `kilograms below a tonne`() {
        assertEquals("850 kg", volume(850.0))
    }
}
