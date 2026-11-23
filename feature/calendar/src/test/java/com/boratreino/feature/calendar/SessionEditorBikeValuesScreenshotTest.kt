package com.boratreino.feature.calendar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioDraft
import com.boratreino.core.model.CardioRules
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/** The session editor's cardio sheet with the Bike values section, collapsed and open; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SessionEditorBikeValuesScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val bike = Machine(34L, "Bike", MuscleGroup.CARDIO, MachineKind.CARDIO, 1.0, null, null, archived = false)

    private val set = CardioRules.toSet(
        id = 9030L,
        sessionId = CalendarSamples.mondaySession.session.id,
        machineId = bike.id,
        position = 0,
        draft = CardioDraft(45, 520, 18_400, 12),
        loggedAt = Instant.parse("2026-11-02T21:20:00Z"),
    )

    private fun editing(
        watts: Int? = null,
        heartRate: Int? = null,
        wattsPerKg: Double? = null,
        tss: Int? = null,
        intensityFactor: Double? = null,
        dark: Boolean = false,
        name: String,
    ) {
        val state = CalendarSamples.editorState.copy(
            machines = CalendarSamples.editorState.machines + bike,
            editor = SetEdit(
                set = set,
                machine = bike,
                number = 1,
                weightKg = 0.0,
                reps = 10,
                minutes = 45,
                calories = 520,
                distanceM = 18_400,
                intensity = 12,
                watts = watts,
                heartRate = heartRate,
                wattsPerKg = wattsPerKg,
                tss = tss,
                intensityFactor = intensityFactor,
            ),
        )
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    SessionEditorContent(state = state, actions = SessionEditorActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `session editor bike values collapsed`() = editing(name = "session-editor-bike-values-collapsed")

    @Test
    fun `session editor bike values open with every value`() = editing(
        watts = 160,
        heartRate = 142,
        wattsPerKg = 2.4,
        tss = 48,
        intensityFactor = 0.79,
        name = "session-editor-bike-values-open",
    )

    @Test
    fun `session editor bike values open in the dark`() = editing(
        watts = 160,
        dark = true,
        name = "session-editor-bike-values-open-dark",
    )
}
