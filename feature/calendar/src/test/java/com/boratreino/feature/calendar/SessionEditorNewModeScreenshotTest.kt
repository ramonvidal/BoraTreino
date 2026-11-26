package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Session
import com.boratreino.core.model.SessionDetail
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

/** The session editor for a session not written yet: the day, no sets. Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorNewModeScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val newState = CalendarSamples.editorState.copy(
        detail = SessionDetail(
            session = Session(0L, Instant.parse("2026-11-02T18:00:00Z"), null, null, null),
            routine = null,
            machines = emptyList(),
        ),
        date = LocalDate.of(2026, 11, 2),
    )

    private fun setContent(dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = newState, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    @Test
    fun `new session shows the day and no sets`() {
        setContent()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-new.png")
    }

    @Test
    fun `new session shows the day and no sets dark`() {
        setContent(dark = true)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/session-editor-new-dark.png")
    }
}
