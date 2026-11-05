package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Monday's session with Routine B and no sets yet. */
private val emptyWithRoutine = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let { SessionDetail(it.session, it.routine, emptyList()) },
)

/** The same empty session in Free (no routine). */
private val emptyFree = CalendarSamples.editorState.copy(
    detail = CalendarSamples.mondaySession.let { SessionDetail(it.session.copy(routineId = null), null, emptyList()) },
)

/** The empty session editor offering "Same as last time"; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorRepeatScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(state: SessionEditorUiState, dark: Boolean = false, actions: SessionEditorActions = SessionEditorActions()) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `an empty session with a routine offers both buttons`() {
        setContent(emptyWithRoutine)

        compose.onNodeWithText("Same as last time").assertExists()
        compose.onNodeWithText("Same, with suggested weights").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-repeat.png")
    }

    @Test
    fun `an empty session with a routine offers both buttons dark`() {
        setContent(emptyWithRoutine, dark = true)

        compose.onNodeWithText("Same as last time").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-repeat-dark.png")
    }

    @Test
    fun `an empty session without a routine has no buttons`() {
        setContent(emptyFree)

        compose.onNodeWithText("Same as last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-repeat-free.png")
    }

    @Test
    fun `a session that already has machines has no buttons`() {
        setContent(CalendarSamples.editorState)

        compose.onNodeWithText("Same as last time").assertDoesNotExist()
        compose.onNodeWithText("Same, with suggested weights").assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-repeat-filled.png")
    }

    @Test
    fun `the buttons report their own action`() {
        var same = 0
        var suggested = 0
        setContent(
            emptyWithRoutine,
            actions = SessionEditorActions(onRepeatSame = { same++ }, onRepeatSuggested = { suggested++ }),
        )

        compose.onNodeWithText("Same as last time").performClick()
        assertEquals(1 to 0, same to suggested)
        compose.onNodeWithText("Same, with suggested weights").performClick()
        assertEquals(1 to 1, same to suggested)
    }
}

/** The same screen in Baianês, to check the translated texts fit. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class SessionEditorRepeatPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `an empty session with a routine pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = emptyWithRoutine, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-repeat-pt.png")
    }
}
