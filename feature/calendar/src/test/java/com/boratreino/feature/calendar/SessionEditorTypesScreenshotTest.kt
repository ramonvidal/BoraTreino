package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.model.SessionMachine
import com.boratreino.core.model.SetType
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Leg press sets of Monday: Warm-up, Normal, Drop, Failure. */
private val legSets = CalendarSamples.mondaySession.machines[0].sets.let { original ->
    // Original: three normal sets. Result: Warm-up, Normal, Drop and a new Failure set.
    listOf(
        original[0].copy(type = SetType.WARMUP),
        original[1],
        original[2].copy(type = SetType.DROP),
        original[2].copy(id = original[2].id + 1000, position = 3, type = SetType.FAILURE),
    )
}

private val typedDetail = CalendarSamples.mondaySession.let {
    SessionDetail(it.session, it.routine, listOf(SessionMachine(it.machines[0].machine, legSets)) + it.machines.drop(1))
}

private val typedState = CalendarSamples.editorState.copy(detail = typedDetail)

/** The session editor with labelled sets and the edit sheet's type chips; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorTypesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(
        state: SessionEditorUiState,
        actions: SessionEditorActions = SessionEditorActions(),
        dark: Boolean = false,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    private fun editing(set: SetEdit) = typedState.copy(editor = set)

    private fun edit(index: Int) = SetEdit(
        set = legSets[index],
        machine = CalendarSamples.legPress,
        number = index + 1,
        weightKg = legSets[index].weightKg ?: 0.0,
        reps = legSets[index].reps ?: 10,
        type = legSets[index].type,
    )

    @Test
    fun `sets show their type labels`() {
        setContent(typedState)

        compose.onNodeWithText("Warm-up").assertExists()
        compose.onNodeWithText("Drop set").assertExists()
        compose.onNodeWithText("Failure").assertExists()
        compose.onAllNodesWithText("Normal").assertCountEquals(0)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-types.png")
    }

    @Test
    fun `sets show their type labels dark`() {
        setContent(typedState, dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-types-dark.png")
    }

    @Test
    fun `sets show their type labels large font`() {
        setContent(typedState, fontScale = 1.8f)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-types-large-font.png")
    }

    @Test
    fun `edit sheet shows the four chips`() {
        setContent(editing(edit(2)))

        compose.onNodeWithText("Type").assertExists()
        compose.onNodeWithText("Normal").assertExists()
        // Each of these also labels a row behind the sheet.
        compose.onAllNodesWithText("Warm-up").assertCountEquals(2)
        compose.onAllNodesWithText("Drop set").assertCountEquals(2)
        compose.onAllNodesWithText("Failure").assertCountEquals(2)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-types-sheet.png")
    }

    @Test
    fun `edit sheet dark`() {
        setContent(editing(edit(0)), dark = true)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-types-sheet-dark.png")
    }

    @Test
    fun `edit sheet large font`() {
        setContent(editing(edit(3)), fontScale = 1.8f)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-types-sheet-large-font.png")
    }

    @Test
    fun `tapping a chip reports the type`() {
        val picked = mutableListOf<SetType>()
        val plain = CalendarSamples.editorState.copy(
            editor = SetEdit(
                CalendarSamples.mondaySession.machines[0].sets[0],
                CalendarSamples.legPress,
                1,
                120.0,
                12,
            ),
        )
        setContent(plain, SessionEditorActions(onEditorType = { picked += it }))

        compose.onNodeWithText("Warm-up").performClick()
        compose.onNodeWithText("Failure").performClick()

        assertEquals(listOf(SetType.WARMUP, SetType.FAILURE), picked)
    }

    @Test
    fun `the day card counts working sets and machines only`() {
        // Leg press: warm-up + 3 working; seated row: 3 normal; chest press: only a warm-up.
        val chest = CalendarSamples.mondaySession.machines[2]
        val onlyWarmup = SessionMachine(chest.machine, chest.sets.take(1).map { it.copy(type = SetType.WARMUP) })
        val detail = SessionDetail(
            typedDetail.session,
            typedDetail.routine,
            listOf(typedDetail.machines[0], typedDetail.machines[1], onlyWarmup),
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    CalendarContent(
                        state = CalendarSamples.monthWithTraining.copy(sessions = listOf(detail)),
                        actions = CalendarActions(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.onNodeWithText("Routine B · 2 machines · 6 sets").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-types-day-card.png")
    }
}

/** The same screens in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SessionEditorTypesPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `labelled sets pt`() {
        setContent(typedState)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-types-pt.png")
    }

    @Test
    fun `edit sheet pt`() {
        val editor = SetEdit(legSets[2], CalendarSamples.legPress, 3, 120.0, 10, type = SetType.DROP)
        setContent(typedState.copy(editor = editor))
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/session-editor-types-sheet-pt.png")
    }
}
