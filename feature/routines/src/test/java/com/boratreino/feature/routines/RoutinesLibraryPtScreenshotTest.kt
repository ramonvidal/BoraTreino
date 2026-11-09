package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryFilter
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.ScheduleMode
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The exercise picker and "How to do it" in Portuguese (Baianes strings). The steps stay in English, with
 * the note saying so. Saved to build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutinesLibraryPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val zottman = LibraryExercise(
        id = "Zottman_Curl",
        name = "Zottman Curl",
        namePt = "Rosca Zottman",
        equipment = LibraryEquipment.DUMBBELL,
        level = LibraryLevel.BEGINNER,
        category = "strength",
        mechanic = "isolation",
        force = "pull",
        primaryMuscles = listOf("biceps"),
        secondaryMuscles = listOf("forearms"),
        steps = listOf(
            "Stand up straight with a dumbbell in each hand, palms facing forward.",
            "Curl the weights while rotating the wrists so the palms face down at the top.",
        ),
    )

    private fun pickerRow(key: String, name: String, libraryId: String?) = PickerRow(
        key = key,
        name = name,
        group = MuscleGroup.ARMS,
        starter = if (key.startsWith("s:")) StarterMachines.all.single { it.name == name } else null,
        libraryId = libraryId,
        equipment = LibraryEquipment.DUMBBELL,
    )

    private val filtered = GymSamples.populated.copy(
        startersExpanded = true,
        pickerAvailable = true,
        scheduleMode = ScheduleMode.IN_TURN,
        filter = LibraryFilter(group = MuscleGroup.ARMS, equipment = LibraryEquipment.DUMBBELL),
        pickerGroups = listOf(
            PickerGroup(
                MuscleGroup.ARMS,
                listOf(
                    pickerRow("l:Zottman_Curl", "Rosca Zottman", "Zottman_Curl"),
                    pickerRow("l:Concentration_Curls", "Rosca concentrada", "Concentration_Curls"),
                ),
            ),
        ),
    )

    @Test
    fun `the picker with filters in Portuguese shows the count in Portuguese`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = filtered,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.GYM,
                    )
                }
            }
        }

        compose.onNodeWithText("2 exercícios").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-filtered-pt.png")
    }

    @Test
    fun `how to do it in Portuguese shows the Portuguese name, the Portuguese heading and the note that the steps are English`() {
        compose.setContent {
            BoraTreinoTheme {
                ExerciseDetailDialog(
                    detail = LibraryDetail(exercise = zottman),
                    portuguese = true,
                    onAdd = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Rosca Zottman").assertExists()
        compose.onNodeWithText("Como fazer").assertExists()
        compose.onNodeWithText("As instruções estão em inglês").assertExists()
        compose.onNodeWithText("Adicionar").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routines-library-detail-pt.png")
    }
}
