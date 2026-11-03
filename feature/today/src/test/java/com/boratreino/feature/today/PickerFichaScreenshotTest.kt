package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The picker asking whether a chosen machine joins the active ficha; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PickerFichaScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val legCurl = Machine(
        id = 21L,
        name = "Leg curl",
        muscleGroup = MuscleGroup.LEGS,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    @Test
    fun `picker asks whether to add the machine to the ficha`() {
        val picker = PickerUiState(
            loaded = true,
            recent = listOf(TodaySamples.latPulldown, TodaySamples.legPress),
            groups = listOf(MachineGroup(MuscleGroup.LEGS, listOf(TodaySamples.legExtension))),
            starterGroups = TodaySamples.startersNotAdded(),
            fichaPrompt = FichaPrompt(legCurl, "Treino B"),
        )
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    PickerContent(state = picker, actions = PickerActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }

        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/picker-ficha-prompt.png")
    }
}
