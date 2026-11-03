package com.boratreino.feature.routines

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MachineKind
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Exercise pictures in a list: starters with a picture, a starter without one, and a custom machine; light and dark. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class ExerciseThumbsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun machine(id: Long, name: String, group: MuscleGroup) = Machine(
        id = id,
        name = name,
        muscleGroup = group,
        kind = MachineKind.WEIGHT_REPS,
        stepKg = 2.5,
        setupNote = null,
        restSeconds = null,
        archived = false,
    )

    private val machines = listOf(
        machine(1L, "Leg press", MuscleGroup.LEGS),
        machine(2L, "Barbell bench press", MuscleGroup.CHEST),
        machine(3L, "Lat pulldown", MuscleGroup.BACK),
        machine(4L, "Dumbbell curl", MuscleGroup.ARMS),
        machine(5L, "My own machine", MuscleGroup.CORE),
    )

    @Composable
    private fun ThumbRows(list: List<Machine>) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            list.forEach { machine ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MachineThumb(machine = machine, size = 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(machine.name)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    private fun shoot(name: String, dark: Boolean) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) { ThumbRows(machines) }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `exercise thumbs light`() {
        shoot("exercise-thumbs", dark = false)
        compose.onNodeWithText("Leg press").assertIsDisplayed()
        compose.onNodeWithText("My own machine").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("No illustration")).assertCountEquals(2)
    }

    @Test
    fun `exercise thumbs dark`() {
        shoot("exercise-thumbs-dark", dark = true)
        compose.onNodeWithText("Barbell bench press").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("No illustration")).assertCountEquals(2)
    }
}
