package com.boratreino.feature.setup

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.DayOfWeek

/** The three steps of the setup, drawn from their state: what is shown and what each control reports. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SetupContentTest {
    @get:Rule
    val compose = createComposeRule()

    /** What the screen reported, in the order it happened. */
    private class Reports {
        var next = 0
        var back = 0
        var skip = 0
        val starters = mutableListOf<String>()
        val exercises = mutableListOf<Long>()
        val queries = mutableListOf<String>()
        val names = mutableListOf<String>()
        val days = mutableListOf<DayOfWeek>()

        fun callbacks() = SetupCallbacks(
            onQuery = { queries += it },
            onToggleStarter = { starters += it.name },
            onToggleExercise = { exercises += it },
            onRoutineName = { names += it },
            onToggleDay = { days += it },
            onNext = { next++ },
            onBack = { back++ },
            onSkip = { skip++ },
        )
    }

    private fun setStep(state: SetupUiState, reports: Reports = Reports()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SetupContent(state = state, callbacks = reports.callbacks(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    // Step 1: the gym

    @Test
    fun `the first step shows Step 1 of 3 and the gym question`() {
        setStep(SetupSamples.gym)

        compose.onNodeWithText("Step 1 of 3").assertExists()
        compose.onNodeWithText("What does your gym have?").assertExists()
    }

    @Test
    fun `the first step has Skip and Next and no Back`() {
        setStep(SetupSamples.gym)

        compose.onNodeWithText("Skip").assertExists()
        compose.onNodeWithText("Next").assertExists()
        compose.onNodeWithText("Back").assertDoesNotExist()
    }

    @Test
    fun `the first step lists the starters by name`() {
        setStep(SetupSamples.gym)

        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Leg extension").assertExists()
        compose.onNodeWithText("Chest press").assertExists()
        compose.onNodeWithText("Lat pulldown").assertExists()
    }

    @Test
    fun `the counter says how many exercises are selected`() {
        setStep(SetupSamples.gym)

        compose.onNodeWithText("2 exercises selected").assertExists()
    }

    @Test
    fun `the counter uses the singular for one exercise`() {
        setStep(SetupSamples.gym.copy(selected = setOf("Leg press")))

        compose.onNodeWithText("1 exercise selected").assertExists()
    }

    @Test
    fun `the counter says zero exercises when nothing is ticked`() {
        setStep(SetupSamples.gymEmpty)

        compose.onNodeWithText("0 exercises selected").assertExists()
    }

    @Test
    fun `tapping a starter reports it`() {
        val reports = Reports()
        setStep(SetupSamples.gymEmpty, reports)

        compose.onNodeWithText("Leg extension").performClick()

        assertEquals(listOf("Leg extension"), reports.starters)
    }

    @Test
    fun `typing in the search box reports the text`() {
        val reports = Reports()
        setStep(SetupSamples.gymEmpty, reports)

        compose.onNode(hasSetTextAction()).performTextReplacement("Leg")

        assertEquals("Leg", reports.queries.last())
    }

    @Test
    fun `Next on the first step reports Next`() {
        val reports = Reports()
        setStep(SetupSamples.gym, reports)

        compose.onNodeWithText("Next").performClick()

        assertEquals(1, reports.next)
        assertEquals(0, reports.back)
    }

    @Test
    fun `Skip on the first step reports Skip`() {
        val reports = Reports()
        setStep(SetupSamples.gym, reports)

        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, reports.skip)
        assertEquals(0, reports.next)
    }

    // Step 2: the first routine

    @Test
    fun `the second step shows Step 2 of 3 and the routine question`() {
        setStep(SetupSamples.routine)

        compose.onNodeWithText("Step 2 of 3").assertExists()
        compose.onNodeWithText("Your first routine").assertExists()
    }

    @Test
    fun `the second step has Back, Next and Skip`() {
        setStep(SetupSamples.routine)

        compose.onNodeWithText("Back").assertExists()
        compose.onNodeWithText("Next").assertExists()
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `the second step lists the chosen exercises`() {
        setStep(SetupSamples.routine)

        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Chest press").assertExists()
    }

    @Test
    fun `the routine name field shows the default name`() {
        setStep(SetupSamples.routine)

        compose.onNode(hasSetTextAction()).assertTextEquals("Routine A")
    }

    @Test
    fun `the routine name field shows the name typed so far`() {
        setStep(SetupSamples.routine.copy(routineName = "Treino Costas"))

        compose.onNode(hasSetTextAction()).assertTextEquals("Treino Costas")
    }

    @Test
    fun `typing a routine name reports it`() {
        val reports = Reports()
        setStep(SetupSamples.routine, reports)

        compose.onNode(hasSetTextAction()).performTextReplacement("Treino Costas")

        assertEquals("Treino Costas", reports.names.last())
    }

    @Test
    fun `tapping a chosen exercise reports its machine id`() {
        val reports = Reports()
        setStep(SetupSamples.routine, reports)

        compose.onNodeWithText("Chest press").performClick()

        assertEquals(listOf(102L), reports.exercises)
    }

    @Test
    fun `with nothing chosen the second step explains and has no name field`() {
        setStep(SetupSamples.routineNone)

        compose.onNodeWithText("You did not pick any exercises", substring = true).assertExists()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.onNodeWithText("Next").assertExists()
    }

    @Test
    fun `Back on the second step reports Back`() {
        val reports = Reports()
        setStep(SetupSamples.routine, reports)

        compose.onNodeWithText("Back").performClick()

        assertEquals(1, reports.back)
        assertEquals(0, reports.next)
    }

    // Step 3: the days

    @Test
    fun `the last step shows Step 3 of 3 and Finish instead of Next`() {
        setStep(SetupSamples.days)

        compose.onNodeWithText("Step 3 of 3").assertExists()
        compose.onNodeWithText("When do you train?").assertExists()
        compose.onNodeWithText("Finish").assertExists()
        compose.onNodeWithText("Next").assertDoesNotExist()
    }

    @Test
    fun `the last step has Back and Skip`() {
        setStep(SetupSamples.days)

        compose.onNodeWithText("Back").assertExists()
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `the day chips run Monday to Sunday`() {
        setStep(SetupSamples.days)

        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { label ->
            compose.onNodeWithText(label).assertExists()
        }
        compose.onAllNodesWithText("Mon").assertCountEquals(1)
    }

    @Test
    fun `tapping a day chip reports the day`() {
        val reports = Reports()
        setStep(SetupSamples.days, reports)

        compose.onNodeWithText("Wed").performClick()

        assertEquals(listOf(DayOfWeek.WEDNESDAY), reports.days)
    }

    @Test
    fun `Finish reports Next, the same as the other steps`() {
        val reports = Reports()
        setStep(SetupSamples.days, reports)

        compose.onNodeWithText("Finish").performClick()

        assertEquals(1, reports.next)
    }

    @Test
    fun `Skip on the last step reports Skip`() {
        val reports = Reports()
        setStep(SetupSamples.days, reports)

        compose.onNodeWithText("Skip").performClick()

        assertEquals(1, reports.skip)
        assertEquals(0, reports.next)
    }
}
