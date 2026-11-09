package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Long-pressing a machine row in the Today routine list opens its menu: Edit exercise, Skip today, Remove from
 * routine. A tap still opens the machine. Checked through what is shown and which action each item calls.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayRowMenuTest {
    @get:Rule
    val compose = createComposeRule()

    /** Routine B in progress: Leg press has its sets, Lat pulldown has none. */
    private val state: TodayUiState = TodaySamples.routineBInProgress
    private val latPulldown = TodaySamples.latPulldown

    private fun setToday(actions: TodayActions) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun row(name: String) = compose.onNode(hasClickAction() and hasText(name))

    @Test
    fun `long press on a routine row shows Edit exercise, Skip today and Remove from routine`() {
        setToday(TodayActions())

        row("Lat pulldown").performTouchInput { longClick() }

        compose.onNodeWithText("Edit exercise").assertIsDisplayed()
        compose.onNodeWithText("Skip today").assertIsDisplayed()
        compose.onNodeWithText("Remove from routine").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/today-row-menu.png")
    }

    @Test
    fun `Edit exercise asks to edit that machine`() {
        val edited = mutableListOf<Long>()
        setToday(TodayActions(onEditMachine = { edited += it }))
        row("Lat pulldown").performTouchInput { longClick() }

        compose.onNodeWithText("Edit exercise").performClick()

        assertEquals(listOf(latPulldown.id), edited)
    }

    @Test
    fun `Skip today asks to skip that machine from the menu`() {
        val skipped = mutableListOf<Long>()
        setToday(TodayActions(onMenuSkip = { skipped += it }))
        row("Lat pulldown").performTouchInput { longClick() }

        compose.onNodeWithText("Skip today").performClick()

        assertEquals(listOf(latPulldown.id), skipped)
    }

    @Test
    fun `Remove from routine asks to remove that machine from the routine`() {
        val removed = mutableListOf<Long>()
        setToday(TodayActions(onRemoveMachine = { removed += it }))
        row("Lat pulldown").performTouchInput { longClick() }

        compose.onNodeWithText("Remove from routine").performClick()

        assertEquals(listOf(latPulldown.id), removed)
    }

    @Test
    fun `a menu item closes the menu`() {
        setToday(TodayActions())
        row("Lat pulldown").performTouchInput { longClick() }

        compose.onNodeWithText("Skip today").performClick()

        compose.onNodeWithText("Edit exercise").assertDoesNotExist()
        compose.onNodeWithText("Remove from routine").assertDoesNotExist()
    }

    @Test
    fun `a tap on a routine row still opens that machine`() {
        val opened = mutableListOf<Long>()
        setToday(TodayActions(onOpenMachine = { opened += it }))

        row("Lat pulldown").performClick()

        assertEquals(listOf(latPulldown.id), opened)
        compose.onNodeWithText("Edit exercise").assertDoesNotExist()
    }
}
