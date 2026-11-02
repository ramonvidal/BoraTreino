package com.boratreino.feature.today

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The machine picker: recent, the user's other machines and the starter machines; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class PickerScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: PickerUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    PickerContent(state = state, actions = PickerActions(), modifier = Modifier.fillMaxSize())
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val loaded = PickerUiState(
        loaded = true,
        recent = listOf(
            TodaySamples.latPulldown,
            TodaySamples.legPress,
            TodaySamples.seatedRow,
            TodaySamples.pullUps,
        ),
        groups = listOf(
            MachineGroup(MuscleGroup.LEGS, listOf(TodaySamples.legExtension)),
            MachineGroup(MuscleGroup.CHEST, listOf(TodaySamples.chestPress)),
            MachineGroup(MuscleGroup.SHOULDERS, listOf(TodaySamples.shoulderPress)),
            MachineGroup(MuscleGroup.ARMS, listOf(TodaySamples.cableCurl)),
        ),
        starterGroups = TodaySamples.startersNotAdded(),
    )

    @Test
    fun `recent, groups and starters expanded`() = shoot("picker", loaded.copy(moreExpanded = true))

    @Test
    fun `more machines folded`() = shoot("picker-folded", loaded.copy(moreExpanded = false))
}
