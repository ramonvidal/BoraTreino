package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The one selectable chip: selection state, clicks, disabled state and touch height. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class BoraChipTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface {
                        Column(Modifier.padding(16.dp)) { content() }
                    }
                }
            }
        }
    }

    /** The clickable chip itself (the merged node), found by its label. */
    private fun chip(label: String) = compose.onNode(hasClickAction() and hasText(label))

    @Test
    fun `a selected chip is selected and an unselected one is not`() {
        setContent {
            BoraChip(label = "Work", selected = true, onClick = {})
            BoraChip(label = "Rest", selected = false, onClick = {})
        }

        chip("Work").assertIsSelected()
        chip("Rest").assertIsNotSelected()
    }

    @Test
    fun `the chip is described by its label`() {
        setContent { BoraChip(label = "Legs", selected = false, onClick = {}) }

        compose.onNodeWithContentDescription("Legs").assertExists()
    }

    @Test
    fun `a click calls onClick once`() {
        var clicks = 0
        setContent { BoraChip(label = "A", selected = false, onClick = { clicks++ }) }

        chip("A").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a click on a selected chip still calls onClick`() {
        var clicks = 0
        setContent { BoraChip(label = "A", selected = true, onClick = { clicks++ }) }

        chip("A").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled chip does not respond to clicks`() {
        var clicks = 0
        setContent { BoraChip(label = "A", selected = false, onClick = { clicks++ }, enabled = false) }

        chip("A").assertIsNotEnabled()
        chip("A").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `an enabled chip is enabled`() {
        setContent { BoraChip(label = "A", selected = false, onClick = {}) }

        chip("A").assertIsEnabled()
    }

    @Test
    fun `a chip is at least 40 dp tall`() {
        setContent { BoraChip(label = "A", selected = false, onClick = {}) }

        chip("A").assertHeightIsAtLeast(40.dp)
    }

    @Test
    fun `a large chip is at least 48 dp tall`() {
        setContent { BoraChip(label = "A", selected = false, onClick = {}, large = true) }

        chip("A").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the chip states look right in light and dark`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BoraChip(label = "Selected", selected = true, onClick = {})
                        BoraChip(label = "Unselected", selected = false, onClick = {})
                        BoraChip(label = "Disabled", selected = false, onClick = {}, enabled = false)
                        BoraChip(label = "Large selected", selected = true, onClick = {}, large = true)
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/core-bora-chip-states.png")
    }

    @Test
    fun `the chip states dark`() {
        compose.setContent {
            BoraTreinoTheme(darkTheme = true) {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        BoraChip(label = "Selected", selected = true, onClick = {})
                        BoraChip(label = "Unselected", selected = false, onClick = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/core-bora-chip-states-dark.png")
    }
}
