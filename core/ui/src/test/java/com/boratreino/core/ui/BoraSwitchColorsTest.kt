package com.boratreino.core.ui

import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Smoke test: a Material switch drawn with [BoraSwitchColors] still shows its state and reports taps. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class BoraSwitchColorsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a checked switch is on and an unchecked one is off`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Switch(
                        checked = true,
                        onCheckedChange = {},
                        colors = BoraSwitchColors(),
                        modifier = Modifier.testTag("on"),
                    )
                    Switch(
                        checked = false,
                        onCheckedChange = {},
                        colors = BoraSwitchColors(),
                        modifier = Modifier.testTag("off"),
                    )
                }
            }
        }

        compose.onNodeWithTag("on").assertIsOn()
        compose.onNodeWithTag("off").assertIsOff()
    }

    @Test
    fun `tapping an unchecked switch asks for checked`() {
        var requested: Boolean? = null
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Switch(
                        checked = false,
                        onCheckedChange = { requested = it },
                        colors = BoraSwitchColors(),
                        modifier = Modifier.testTag("switch"),
                    )
                }
            }
        }

        compose.onNodeWithTag("switch").performClick()

        assertEquals(true, requested)
    }
}
