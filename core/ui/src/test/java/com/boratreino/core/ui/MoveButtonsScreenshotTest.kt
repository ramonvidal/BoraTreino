package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

/** The up / down arrows used on reorderable rows; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h400dp")
class MoveButtonsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Composable
    private fun AllStates() {
        MoveButtons(canMoveUp = true, canMoveDown = true, onUp = {}, onDown = {})
        MoveButtons(canMoveUp = false, canMoveDown = true, onUp = {}, onDown = {})
        MoveButtons(canMoveUp = true, canMoveDown = false, onUp = {}, onDown = {})
        MoveButtons(canMoveUp = false, canMoveDown = false, onUp = {}, onDown = {})
    }

    @Test
    fun `all four states light`() {
        setContent { AllStates() }
        shoot("move-buttons")
    }

    @Test
    fun `all four states dark`() {
        setContent(dark = true) { AllStates() }
        shoot("move-buttons-dark")
    }

    @Test
    fun `all four states large font`() {
        setContent(fontScale = 1.6f) { AllStates() }
        shoot("move-buttons-large-font")
    }

    @Test
    fun `both arrows enabled and they call back`() {
        var ups = 0
        var downs = 0
        setContent { MoveButtons(canMoveUp = true, canMoveDown = true, onUp = { ups++ }, onDown = { downs++ }) }
        compose.onNodeWithContentDescription("Move up").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Move down").assertIsEnabled().performClick()
        assertEquals(1, ups)
        assertEquals(1, downs)
        shoot("move-buttons-enabled")
    }

    @Test
    fun `first row has the up arrow disabled`() {
        setContent { MoveButtons(canMoveUp = false, canMoveDown = true, onUp = {}, onDown = {}) }
        compose.onNodeWithContentDescription("Move up").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Move down").assertIsEnabled()
        shoot("move-buttons-disabled-up")
    }

    @Test
    fun `last row has the down arrow disabled`() {
        setContent { MoveButtons(canMoveUp = true, canMoveDown = false, onUp = {}, onDown = {}) }
        compose.onNodeWithContentDescription("Move up").assertIsEnabled()
        compose.onNodeWithContentDescription("Move down").assertIsNotEnabled()
        shoot("move-buttons-disabled-down")
    }

    @Test
    fun `a disabled arrow does not call back`() {
        var ups = 0
        setContent { MoveButtons(canMoveUp = false, canMoveDown = true, onUp = { ups++ }, onDown = {}) }
        compose.onNodeWithContentDescription("Move up").performClick()
        assertEquals(0, ups)
    }
}

/** The same buttons in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h400dp")
class MoveButtonsPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `arrows pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoveButtons(canMoveUp = true, canMoveDown = true, onUp = {}, onDown = {})
                        MoveButtons(canMoveUp = false, canMoveDown = true, onUp = {}, onDown = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/move-buttons-pt.png")
    }
}
