package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The ficha the samples show, with its id set so Today offers editing it. */
private fun TodayUiState.withActiveId(): TodayUiState = copy(routines = routines?.copy(activeId = TodaySamples.routineB.id))

private val notStarted = TodaySamples.routineBNotStarted.withActiveId()
private val inProgress = TodaySamples.routineBInProgress.withActiveId()

/** The training card in edit mode: move arrows and remove on each ficha row, Done and Edit sets and reps. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TodayEditScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false, actions: TodayActions = TodayActions()) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    // Normal mode is as it was, plus the pencil

    @Test
    fun `normal mode before a session offers the pencil and no move buttons`() {
        shoot("today-edit-normal", notStarted)

        compose.onNodeWithContentDescription("Edit this ficha").assertExists()
        compose.onAllNodesWithContentDescription("Move up").assertCountEquals(0)
        compose.onNodeWithText("Edit sets and reps").assertDoesNotExist()
    }

    @Test
    fun `normal mode during a session offers the pencil and the usual Done`() {
        shoot("today-edit-normal-session", inProgress)

        compose.onNodeWithContentDescription("Edit this ficha").assertExists()
        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Edit sets and reps").assertDoesNotExist()
    }

    @Test
    fun `Free has no pencil`() {
        shoot("today-edit-free", TodaySamples.restDay)

        compose.onNodeWithContentDescription("Edit this ficha").assertDoesNotExist()
    }

    @Test
    fun `tapping the pencil asks to start editing`() {
        var started = 0
        shoot("today-edit-pencil", notStarted, actions = TodayActions(onStartEditing = { started++ }))

        compose.onNodeWithContentDescription("Edit this ficha").performClick()

        assertEquals(1, started)
    }

    // Edit mode before a session

    @Test
    fun `edit mode before a session`() {
        shoot("today-edit", notStarted.copy(editing = true))

        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Edit sets and reps").assertExists()
        compose.onNodeWithContentDescription("Edit this ficha").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Move up").assertCountEquals(4)
        compose.onAllNodesWithContentDescription("Move down").assertCountEquals(4)
        compose.onNodeWithContentDescription("Remove Leg press from the ficha").assertExists()
    }

    @Test
    fun `edit mode before a session dark`() = shoot("today-edit-dark", notStarted.copy(editing = true), dark = true)

    // Edit mode during a session

    @Test
    fun `edit mode during a session`() {
        shoot("today-edit-session", inProgress.copy(editing = true))

        compose.onNodeWithText("Done").assertExists()
        compose.onNodeWithText("Edit sets and reps").assertExists()
        compose.onAllNodesWithContentDescription("Move up").assertCountEquals(4)
        // The extra rows below the ficha are not shown while editing.
        compose.onNodeWithText("Also this session").assertDoesNotExist()
    }

    @Test
    fun `edit mode during a session dark`() = shoot("today-edit-session-dark", inProgress.copy(editing = true), dark = true)

    // Actions

    @Test
    fun `the arrows and the cross report the machine`() {
        val moves = mutableListOf<Pair<Long, Boolean>>()
        val removed = mutableListOf<Long>()
        shoot(
            "today-edit-actions",
            notStarted.copy(editing = true),
            actions = TodayActions(
                onMoveMachine = { id, up -> moves += id to up },
                onRemoveMachine = { removed += it },
            ),
        )

        // The first row cannot go up (disabled), so its Move down is the first enabled arrow of that row.
        compose.onAllNodesWithContentDescription("Move down")[0].performClick()
        compose.onAllNodesWithContentDescription("Move up")[1].performClick()
        compose.onNodeWithContentDescription("Remove Leg extension from the ficha").performClick()

        assertEquals(listOf(TodaySamples.legPress.id to false, TodaySamples.legExtension.id to true), moves)
        assertEquals(listOf(TodaySamples.legExtension.id), removed)
    }

    @Test
    fun `in edit mode a row tap does not open the machine`() {
        val opened = mutableListOf<Long>()
        shoot("today-edit-row-tap", notStarted.copy(editing = true), actions = TodayActions(onOpenMachine = { opened += it }))

        compose.onNodeWithText("Leg press").performClick()

        assertTrue(opened.isEmpty())
    }

    @Test
    fun `in normal mode a row tap opens the machine`() {
        val opened = mutableListOf<Long>()
        shoot("today-edit-row-tap-normal", notStarted, actions = TodayActions(onOpenMachine = { opened += it }))

        compose.onNodeWithText("Leg press").performClick()

        assertEquals(listOf(TodaySamples.legPress.id), opened)
    }

    @Test
    fun `Done and Edit sets and reps report their actions`() {
        var done = 0
        var routine = 0
        shoot(
            "today-edit-buttons",
            notStarted.copy(editing = true),
            actions = TodayActions(onFinishEditing = { done++ }, onEditRoutine = { routine++ }),
        )

        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Edit sets and reps").performClick()

        assertEquals(1, done)
        assertEquals(1, routine)
    }

    @Test
    fun `during a session the edit mode Done does not finish the training`() {
        var finishedTraining = 0
        var doneEditing = 0
        shoot(
            "today-edit-session-done",
            inProgress.copy(editing = true),
            actions = TodayActions(onDone = { finishedTraining++ }, onFinishEditing = { doneEditing++ }),
        )

        compose.onNodeWithText("Done").performClick()

        assertEquals(0, finishedTraining)
        assertEquals(1, doneEditing)
    }
}

/** The same edit mode in Baianês, to check the translated texts fit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class TodayEditPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: TodayUiState, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    TodayContent(state = state, actions = TodayActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `edit mode before a session pt`() = shoot("today-edit-pt", notStarted.copy(editing = true))

    @Test
    fun `edit mode during a session pt`() = shoot("today-edit-session-pt", inProgress.copy(editing = true))

    @Test
    fun `edit mode during a session pt dark`() = shoot("today-edit-session-pt-dark", inProgress.copy(editing = true), dark = true)

    @Test
    fun `normal mode with the pencil pt`() = shoot("today-edit-normal-pt", notStarted)
}
