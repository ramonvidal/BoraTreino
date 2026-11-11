package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The app's button: clicks, the disabled state, the label, and the three heights. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class BoraButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun `a click calls onClick once`() {
        var clicks = 0
        setContent { BoraButton(text = "Save", onClick = { clicks++ }, modifier = Modifier.fillMaxWidth()) }

        compose.onNodeWithText("Save").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `a disabled button does not respond to clicks`() {
        var clicks = 0
        setContent { BoraButton(text = "Save", onClick = { clicks++ }, enabled = false) }

        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Save").performClick()

        assertEquals(0, clicks)
    }

    @Test
    fun `an enabled button is enabled`() {
        setContent { BoraButton(text = "Save", onClick = {}) }

        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun `the label is shown in every style`() {
        setContent {
            BoraButton(text = "Filled label", onClick = {}, style = BoraButtonStyle.Filled)
            BoraButton(text = "Tonal label", onClick = {}, style = BoraButtonStyle.Tonal)
            BoraButton(text = "Text label", onClick = {}, style = BoraButtonStyle.Text)
        }

        compose.onNodeWithText("Filled label").assertExists()
        compose.onNodeWithText("Tonal label").assertExists()
        compose.onNodeWithText("Text label").assertExists()
    }

    @Test
    fun `a filled button is at least 56 dp tall`() {
        setContent { BoraButton(text = "Filled", onClick = {}, style = BoraButtonStyle.Filled) }

        compose.onNodeWithText("Filled").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `a tonal button is at least 56 dp tall`() {
        setContent { BoraButton(text = "Tonal", onClick = {}, style = BoraButtonStyle.Tonal) }

        compose.onNodeWithText("Tonal").assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun `a text button is at least 44 dp tall`() {
        setContent { BoraButton(text = "Text", onClick = {}, style = BoraButtonStyle.Text) }

        compose.onNodeWithText("Text").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun `the three styles are captured together`() {
        setContent {
            BoraButton(text = "Save workout", onClick = {}, modifier = Modifier.fillMaxWidth())
            BoraButton(text = "Add exercise", onClick = {}, modifier = Modifier.fillMaxWidth(), style = BoraButtonStyle.Tonal)
            BoraButton(text = "Skip", onClick = {}, style = BoraButtonStyle.Text)
            BoraButton(text = "Disabled", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
        }

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bora-button-styles.png")
    }
}
