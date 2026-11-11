package com.boratreino.feature.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/**
 * The whole screen with its view model and fakes: Skip leaves without writing anything, Finish writes the
 * training days and leaves. Written against the screen's controls only.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SetupScreenWiringTest {
    @get:Rule
    val compose = createComposeRule()

    private val machines = FakeSetupMachines()
    private val routines = FakeSetupRoutines()
    private var done = 0

    private fun setScreen() {
        val viewModel = SetupViewModel(machines, routines, FakeBodySettings())
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetupScreen(onDone = { done++ }, viewModel = viewModel)
                }
            }
        }
    }

    @Test
    fun `Skip on the first step leaves and writes nothing, even with exercises ticked`() {
        setScreen()

        compose.onNodeWithText("Leg press").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, done)
        assertTrue(machines.addCalls.isEmpty())
        assertTrue(routines.current().isEmpty())
        assertEquals(0, routines.createCount)
        assertTrue(routines.trainingDayWrites.isEmpty())
    }

    @Test
    fun `Skip on the second step keeps the gym written by Next but creates no routine`() {
        setScreen()

        compose.onNodeWithText("Leg press").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, done)
        assertEquals(listOf(listOf("Leg press")), machines.addCalls)
        assertEquals(0, routines.createCount)
        assertTrue(routines.trainingDayWrites.isEmpty())
    }

    @Test
    fun `Skip on the last step writes no training days`() {
        setScreen()

        compose.onNodeWithText("Leg press").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Wed").performClick()
        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, done)
        assertTrue(routines.trainingDayWrites.isEmpty())
        assertEquals(1, routines.createCount)
    }

    @Test
    fun `Finish writes the picked training days and leaves once`() {
        setScreen()

        compose.onNodeWithText("Leg press").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Next").performClick()
        compose.onNodeWithText("Wed").performClick()
        compose.onNodeWithText("Finish").performClick()

        assertEquals(listOf(setOf(DayOfWeek.WEDNESDAY)), routines.trainingDayWrites)
        assertEquals(1, done)
    }
}
