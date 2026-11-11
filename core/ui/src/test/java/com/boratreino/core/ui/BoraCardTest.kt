package com.boratreino.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
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

/** The card: it is clickable only when given an onClick, and it shows its content. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class BoraCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) { content() }
                }
            }
        }
    }

    @Test
    fun `a card with onClick has a click action`() {
        setContent {
            BoraCard(onClick = {}) { Text("Card body") }
        }

        compose.onNodeWithText("Card body").assertHasClickAction()
    }

    @Test
    fun `a card without onClick has no click action`() {
        setContent {
            BoraCard { Text("Card body") }
        }

        compose.onNodeWithText("Card body").assertHasNoClickAction()
    }

    @Test
    fun `a click on a card with onClick calls it once`() {
        var clicks = 0
        setContent {
            BoraCard(onClick = { clicks++ }) { Text("Card body") }
        }

        compose.onNodeWithText("Card body").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `the content is shown inside the card`() {
        setContent {
            BoraCard { Text("First line"); Text("Second line") }
        }

        compose.onNodeWithText("First line").assertExists()
        compose.onNodeWithText("Second line").assertExists()
    }

    @Test
    fun `a tinted card and a plain card are captured`() {
        setContent {
            BoraCard(modifier = Modifier) { Text("Plain card") }
            BoraCard(tint = Color(0xFFFEC204)) { Text("Tinted card") }
        }

        compose.onNodeWithText("Tinted card").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/bora-card.png")
    }
}
