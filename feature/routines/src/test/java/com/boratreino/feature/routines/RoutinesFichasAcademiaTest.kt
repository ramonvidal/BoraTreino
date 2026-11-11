package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.Routine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the Fichas cards and the Academia page draw, and what their buttons report. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesFichasAcademiaTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setTab(
        state: RoutinesUiState,
        page: RoutinesPage,
        callbacks: RoutinesCallbacks = RoutinesCallbacks(),
    ) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize(), initialPage = page)
                }
            }
        }
    }

    private fun routine(id: Long, name: String) = Routine(id = id, planId = 1L, name = name, shortName = "$id", position = 0, weekdays = emptySet())

    // Fichas cards

    @Test
    fun `a Fichas card shows the muscle groups of its exercises as pills, and the exercise count`() {
        setTab(GymSamples.populated, RoutinesPage.ROUTINES)

        compose.onNodeWithText("Treino B").assertExists()
        // Treino B has Back and Arms; Treino C has Chest, Shoulders and Back.
        compose.onAllNodesWithText("Back").assertCountEquals(2)
        compose.onNodeWithText("Arms").assertExists()
        compose.onNodeWithText("Chest").assertExists()
        compose.onNodeWithText("Shoulders").assertExists()
        compose.onNodeWithText("Legs").assertExists()
        compose.onAllNodesWithText("4 exercises").assertCountEquals(2)
        compose.onNodeWithText("3 exercises").assertExists()
    }

    @Test
    fun `a Fichas card with one exercise says so in the singular`() {
        val one = RoutineRow(routine(7L, "Treino Z"), machineCount = 1, muscleGroups = listOf(MuscleGroup.CORE))
        setTab(GymSamples.populated.copy(routines = listOf(one)), RoutinesPage.ROUTINES)

        compose.onNodeWithText("1 exercise").assertExists()
        compose.onNodeWithText("Core").assertExists()
    }

    @Test
    fun `a Fichas card shows at most four muscle group pills`() {
        val five = RoutineRow(
            routine(8L, "Treino Full"),
            machineCount = 5,
            muscleGroups = listOf(MuscleGroup.LEGS, MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.SHOULDERS, MuscleGroup.ARMS),
        )
        setTab(GymSamples.populated.copy(routines = listOf(five)), RoutinesPage.ROUTINES)

        compose.onNodeWithText("Legs").assertExists()
        compose.onNodeWithText("Chest").assertExists()
        compose.onNodeWithText("Back").assertExists()
        compose.onNodeWithText("Shoulders").assertExists()
        compose.onNodeWithText("Arms").assertDoesNotExist()
    }

    @Test
    fun `a Fichas card with no exercises shows no pills`() {
        val empty = RoutineRow(routine(9L, "Treino Vazio"), machineCount = 0)
        setTab(GymSamples.populated.copy(routines = listOf(empty)), RoutinesPage.ROUTINES)

        compose.onNodeWithText("Treino Vazio").assertExists()
        compose.onNodeWithText("0 exercises").assertExists()
        compose.onNodeWithText("Legs").assertDoesNotExist()
    }

    // Academia page

    @Test
    fun `the Academia page has no delete buttons on its exercise rows`() {
        setTab(GymSamples.populated, RoutinesPage.GYM)

        compose.onNodeWithText("Leg press").assertExists()
        compose.onAllNodes(hasContentDescription("Delete", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun `the Academia page does not show the folded exercise picker any more`() {
        val starters = GymSamples.pickerGroups(GymSamples.startersNotAdded)
        setTab(GymSamples.populated.copy(pickerGroups = starters, startersExpanded = true), RoutinesPage.GYM)

        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Barbell bench press").assertDoesNotExist()
        compose.onNodeWithText("Add from the list").assertDoesNotExist()
    }

    @Test
    fun `Add exercises opens the library`() {
        var opened = 0
        setTab(GymSamples.populated, RoutinesPage.GYM, RoutinesCallbacks(onOpenLibrary = { opened++ }))

        compose.onNodeWithText("Add exercises").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `New exercise still opens a new custom exercise`() {
        var opened = 0
        setTab(GymSamples.populated, RoutinesPage.GYM, RoutinesCallbacks(onNewMachine = { opened++ }))

        compose.onNodeWithText("New exercise").performClick()

        assertEquals(1, opened)
    }

    @Test
    fun `tapping an exercise row opens its editor, not a delete question`() {
        val edited = mutableListOf<Long>()
        setTab(GymSamples.populated, RoutinesPage.GYM, RoutinesCallbacks(onEditMachine = { edited += it }))

        compose.onNodeWithText("Leg press").performClick()

        assertEquals(listOf(GymSamples.legPress.id), edited)
    }

    @Test
    fun `the Add exercises button is not offered without anything to pick from`() {
        setTab(GymSamples.populated.copy(pickerAvailable = false), RoutinesPage.GYM)

        compose.onNodeWithText("Add exercises").assertDoesNotExist()
        compose.onNodeWithText("New exercise").assertExists()
    }
}
