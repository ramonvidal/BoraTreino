package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Fichas page of the Routines tab: cards with their muscle group pills, the 3-dot menu, empty state; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesPageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setTab(
        state: RoutinesUiState,
        callbacks: RoutinesCallbacks = RoutinesCallbacks(),
        page: RoutinesPage? = RoutinesPage.ROUTINES,
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        RoutinesContent(state = state, callbacks = callbacks, modifier = Modifier.fillMaxSize(), initialPage = page)
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun `routines page with three routines`() {
        setTab(GymSamples.populated)
        shoot("routines-page")

        compose.onNodeWithText("Treino A").assertExists()
        compose.onNodeWithText("Treino C").assertExists()
        compose.onAllNodesWithText("4 exercises").assertCountEquals(2)
        compose.onNodeWithText("3 exercises").assertExists()
        compose.onNodeWithText("Legs").assertExists()
        compose.onAllNodesWithText("Back").assertCountEquals(2)
        compose.onNodeWithText("Arms").assertExists()
        compose.onNodeWithText("Chest").assertExists()
        compose.onNodeWithText("Shoulders").assertExists()
        compose.onNodeWithText("New routine").assertExists()
    }

    @Test
    fun `the schedule is not drawn on the Fichas page any more`() {
        setTab(GymSamples.populated)

        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
        compose.onNodeWithText("In turn").assertDoesNotExist()
        compose.onNodeWithText("On weekdays").assertDoesNotExist()
    }

    @Test
    fun `routines page empty state`() {
        setTab(GymSamples.populated.copy(routines = emptyList()))
        shoot("routines-page-empty")

        compose.onNodeWithText("No routines yet").assertExists()
        compose.onNodeWithText("New routine").assertExists()
        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
    }

    @Test
    fun `routines page dark`() {
        setTab(GymSamples.populated, dark = true)
        shoot("routines-page-dark")
    }

    @Test
    fun `routines page empty dark`() {
        setTab(GymSamples.populated.copy(routines = emptyList()), dark = true)
        shoot("routines-page-empty-dark")
    }

    @Test
    fun `routines page large font`() {
        setTab(GymSamples.populated, fontScale = 1.6f)
        shoot("routines-page-large-font")
        compose.onNodeWithText("Treino A").assertExists()
    }

    @Test
    fun `routines page with routines on specific days`() {
        val onDays = GymSamples.routines.map { row ->
            row.copy(routine = row.routine.copy(weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)))
        }
        setTab(GymSamples.populated.copy(routines = onDays))
        shoot("routines-page-specific-days")
        compose.onNodeWithText("Treino A").assertExists()
    }

    @Test
    fun `routine card menu is expanded`() {
        setTab(GymSamples.populated)
        compose.onNodeWithContentDescription("More options for Treino A").performClick()

        compose.onNodeWithText("Edit").assertExists()
        compose.onNodeWithText("Move up").assertExists()
        compose.onNodeWithText("Move down").assertExists()
        compose.onNodeWithText("Duplicate").assertExists()
        compose.onNodeWithText("Delete").assertExists()
        shoot("routines-page-menu")
    }

    @Test
    fun `the first routine cannot move up`() {
        setTab(GymSamples.populated)
        compose.onNodeWithContentDescription("More options for Treino A").performClick()

        compose.onNodeWithText("Move up").assertIsNotEnabled()
    }

    @Test
    fun `menu items call the callbacks with the routine id`() {
        val calls = mutableListOf<String>()
        setTab(
            GymSamples.populated,
            RoutinesCallbacks(
                onOpenRoutine = { calls += "open $it" },
                onMoveRoutine = { id, up -> calls += "move $id $up" },
                onDuplicateRoutine = { calls += "duplicate $it" },
                onDeleteRoutine = { calls += "delete $it" },
            ),
        )

        compose.onNodeWithContentDescription("More options for Treino B").performClick()
        compose.onNodeWithText("Duplicate").performClick()
        compose.onNodeWithContentDescription("More options for Treino B").performClick()
        compose.onNodeWithText("Move down").performClick()
        compose.onNodeWithContentDescription("More options for Treino B").performClick()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("duplicate 2", "move 2 false", "delete 2"), calls)
    }

    @Test
    fun `tapping a card opens the routine and the big button creates one`() {
        val calls = mutableListOf<String>()
        setTab(
            GymSamples.populated,
            RoutinesCallbacks(onOpenRoutine = { calls += "open $it" }, onNewRoutine = { calls += "new" }),
        )

        compose.onNodeWithText("Treino C").performClick()
        compose.onNodeWithText("New routine").performClick()

        assertEquals(listOf("open 3", "new"), calls)
    }

    @Test
    fun `the delete question names its routine and confirms`() {
        val calls = mutableListOf<String>()
        setTab(
            GymSamples.populated.copy(routineDelete = GymSamples.routines[0].routine),
            RoutinesCallbacks(onConfirmDeleteRoutine = { calls += "confirm" }, onDismissDeleteRoutine = { calls += "dismiss" }),
        )

        compose.onNodeWithText("Delete this routine?").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routines-page-delete.png")
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    fun `the switch starts on Routines when the gym has machines`() {
        setTab(GymSamples.populated, page = null)

        compose.onNodeWithText("New routine").assertExists()
        compose.onNodeWithText("Exercises \u00b7 8").assertDoesNotExist()
    }

    @Test
    fun `the switch starts on Gym on a fresh install`() {
        setTab(GymSamples.emptyGym, page = null)

        compose.onNodeWithText("Exercises \u00b7 0").assertExists()
        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
    }

    @Test
    fun `tapping Gym shows the gym page`() {
        setTab(GymSamples.populated, page = null)

        compose.onNodeWithText("Gym").performClick()

        compose.onNodeWithText("Exercises \u00b7 8").assertExists()
        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
    }

    @Test
    fun `nothing is drawn under the switch until the database answered`() {
        setTab(GymSamples.populated.copy(loaded = false), page = null)

        compose.onNodeWithText("New routine").assertDoesNotExist()
        compose.onNodeWithText("Exercises \u00b7 8").assertDoesNotExist()
        compose.onNodeWithText("Gym").assertExists()
    }
}

/** The Routines page in Baianês, to check the translated text fits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutinesPagePtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setTab(state: RoutinesUiState, page: RoutinesPage) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(state = state, callbacks = RoutinesCallbacks(), modifier = Modifier.fillMaxSize(), initialPage = page)
                }
            }
        }
    }

    @Test
    fun `routines page pt`() {
        setTab(GymSamples.populated.copy(portuguese = true), RoutinesPage.ROUTINES)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-page-pt.png")
        compose.onNodeWithText("Qual ficha vem agora").assertDoesNotExist()
        compose.onNodeWithText("Nova ficha").assertExists()
    }

    @Test
    fun `routines page empty pt`() {
        setTab(GymSamples.populated.copy(routines = emptyList(), portuguese = true), RoutinesPage.ROUTINES)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-page-empty-pt.png")
        compose.onNodeWithText("Nenhuma ficha ainda").assertExists()
    }

    @Test
    fun `routine card menu pt`() {
        setTab(GymSamples.populated.copy(portuguese = true), RoutinesPage.ROUTINES)
        compose.onNodeWithContentDescription("Mais opções de Treino A").performClick()
        compose.onNodeWithText("Duplicar").assertExists()
        compose.onNodeWithText("Excluir").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-page-menu-pt.png")
    }
}
