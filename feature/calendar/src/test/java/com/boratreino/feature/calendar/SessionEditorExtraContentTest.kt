package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Routine
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.ui.theme.BoraTreinoTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The session editor's "Extra workout" switch: shown only when the session has a routine, and a tap reports the
 * opposite of its state. The switch's hint is "Does not change the rotation.".
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h2400dp")
class SessionEditorExtraContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val routineA = Routine(1L, 1L, "Treino A", "A", 0, emptySet())
    private val routineB = Routine(2L, 1L, "Treino B", "B", 1, emptySet())
    private val hint = "Does not change the rotation."

    private fun state(routine: Routine?, extra: Boolean) = SessionEditorUiState(
        loaded = true,
        detail = SessionDetail(
            session = Session(
                id = 20L,
                startedAt = Instant.parse("2026-11-02T18:05:00Z"),
                endedAt = Instant.parse("2026-11-02T19:10:00Z"),
                routineId = routine?.id,
                note = null,
                isExtra = extra,
            ),
            routine = routine,
            machines = emptyList(),
        ),
        date = LocalDate.of(2026, 11, 2),
        routines = listOf(routineA, routineB),
    )

    private fun show(state: SessionEditorUiState, actions: SessionEditorActions = SessionEditorActions()) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = actions, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `the switch is hidden when the session has no routine`() {
        show(state(routine = null, extra = false))

        compose.onNodeWithText("Extra workout").assertDoesNotExist()
        compose.onNodeWithText(hint).assertDoesNotExist()
    }

    @Test
    fun `the switch is shown with its hint when the session has a routine`() {
        show(state(routine = routineB, extra = false))

        compose.onNodeWithText("Extra workout").assertIsDisplayed()
        compose.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun `tapping the switch of a workout that is not extra reports true`() {
        var reported: Boolean? = null
        show(state(routine = routineB, extra = false), SessionEditorActions(onExtraChange = { reported = it }))

        compose.onNodeWithText("Extra workout").performClick()

        assertEquals(true, reported)
    }

    @Test
    fun `tapping the switch of an extra workout reports false`() {
        var reported: Boolean? = null
        show(state(routine = routineB, extra = true), SessionEditorActions(onExtraChange = { reported = it }))

        compose.onNodeWithText("Extra workout").performClick()

        assertEquals(false, reported)
    }
}
