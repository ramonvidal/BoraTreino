package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
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
 * The exercise picker with filters and the "How to do it" dialog, in English and Portuguese. Saved to
 * build/outputs/roborazzi.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesLibraryScreenshotTest {
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
            "Lower slowly, rotating back to palms forward.",
        ),
    )

    private fun pickerRow(key: String, name: String, libraryId: String?, added: Boolean = false) = PickerRow(
        key = key,
        name = name,
        group = MuscleGroup.ARMS,
        starter = if (key.startsWith("s:")) StarterMachines.all.single { it.name == name } else null,
        libraryId = libraryId,
        equipment = LibraryEquipment.DUMBBELL,
        added = added,
    )

    /** The Routines tab on the gym page, the picker open, filtered to dumbbell arm exercises. */
    private val filtered = GymSamples.populated.copy(
        startersExpanded = true,
        pickerAvailable = true,
        scheduleMode = ScheduleMode.IN_TURN,
        filter = LibraryFilter(group = MuscleGroup.ARMS, equipment = LibraryEquipment.DUMBBELL),
        pickerGroups = listOf(
            PickerGroup(
                MuscleGroup.ARMS,
                listOf(
                    pickerRow("s:Dumbbell curl", "Dumbbell curl", "Dumbbell_Alternate_Bicep_Curl"),
                    pickerRow("l:Zottman_Curl", "Zottman Curl", "Zottman_Curl"),
                    pickerRow("l:Concentration_Curls", "Concentration Curls", "Concentration_Curls", added = true),
                ),
            ),
        ),
    )

    private fun setTab(state: RoutinesUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = state,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.GYM,
                    )
                }
            }
        }
    }

    @Test
    fun `the picker with filters shows the chosen chips, the count, the clear button and the added mark`() {
        setTab(filtered)

        compose.onNodeWithText("Clear filters").assertExists()
        compose.onNodeWithText("3 exercises").assertExists()
        compose.onNodeWithText("Added").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-filtered.png")
    }

    @Test
    fun `without filters there is no clear button`() {
        setTab(filtered.copy(filter = LibraryFilter()))

        compose.onNodeWithText("Clear filters").assertDoesNotExist()
    }

    @Test
    fun `a single exercise is counted in the singular`() {
        setTab(filtered.copy(pickerGroups = listOf(PickerGroup(MuscleGroup.ARMS, listOf(pickerRow("l:Zottman_Curl", "Zottman Curl", "Zottman_Curl"))))))

        compose.onNodeWithText("1 exercise").assertExists()
    }

    @Test
    fun `how to do it shows the steps in English, with the add button`() {
        compose.setContent {
            BoraTreinoTheme {
                ExerciseDetailDialog(
                    detail = LibraryDetail(exercise = zottman),
                    portuguese = false,
                    onAdd = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("How to do it").assertExists()
        compose.onNodeWithText("1.").assertExists()
        compose.onNodeWithText("Add").assertExists()
        compose.onNodeWithText("Instructions are in English").assertDoesNotExist()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routines-library-detail.png")
    }

    @Test
    fun `how to do it of an exercise already in the gym says so instead of offering to add it`() {
        compose.setContent {
            BoraTreinoTheme {
                ExerciseDetailDialog(
                    detail = LibraryDetail(exercise = zottman, added = true),
                    portuguese = false,
                    onAdd = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Added").assertExists()
        compose.onNodeWithText("Add").assertDoesNotExist()
    }
}
