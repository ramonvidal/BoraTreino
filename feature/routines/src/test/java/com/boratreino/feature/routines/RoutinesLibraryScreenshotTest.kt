package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.LibraryEquipment
import com.boratreino.core.model.LibraryExercise
import com.boratreino.core.model.LibraryFilter
import com.boratreino.core.model.LibraryLevel
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachines
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The full-screen exercise library ([ExerciseLibraryContent]) in its states, and the "How to do it" dialog.
 * Saved to build/outputs/roborazzi.
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

    /** The library filtered to dumbbell arm exercises, with one of them already in the gym. */
    private val filtered = GymSamples.populated.copy(
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

    private fun setLibrary(state: RoutinesUiState) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    ExerciseLibraryContent(
                        state = state,
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun `the library with filters shows the chosen chips, the count, the Clear button and the added mark`() {
        setLibrary(filtered)

        compose.onNodeWithText("Clear").assertExists()
        // The chip shows the chosen group, and the results list has its heading too.
        compose.onAllNodesWithText("Arms").assertCountEquals(2)
        compose.onNodeWithText("3 exercises").assertExists()
        compose.onNodeWithText("Added").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-filtered.png")
    }

    @Test
    fun `without filters there is no Clear button`() {
        setLibrary(filtered.copy(filter = LibraryFilter()))

        compose.onNodeWithText("Clear").assertDoesNotExist()
        compose.onNodeWithText("Muscle group").assertExists()
    }

    @Test
    fun `a single exercise is counted in the singular`() {
        setLibrary(filtered.copy(pickerGroups = listOf(PickerGroup(MuscleGroup.ARMS, listOf(pickerRow("l:Zottman_Curl", "Zottman Curl", "Zottman_Curl"))))))

        compose.onNodeWithText("1 exercise").assertExists()
    }

    @Test
    fun `an empty search says there are no matches`() {
        setLibrary(
            GymSamples.populated.copy(
                filter = LibraryFilter(query = "zzz"),
                pickerGroups = emptyList(),
            ),
        )

        compose.onNodeWithText("No matches.").assertExists()
        compose.onNodeWithText("0 exercises").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-nomatch.png")
    }

    @Test
    fun `with three exercises ticked the sticky button says Add 3`() {
        val selected = setOf("Barbell bench press", "Incline barbell bench press", "Decline bench press")
        setLibrary(
            GymSamples.populated.copy(
                filter = LibraryFilter(query = "sup"),
                pickerGroups = GymSamples.pickerGroups(StarterMachines.search("sup", GymSamples.startersNotAdded)),
                selectedStarters = selected,
            ),
        )

        compose.onNodeWithText("Add 3").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-selected.png")
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

    @Test
    fun `library rows with and without a picture are listed side by side`() {
        setLibrary(
            GymSamples.populated.copy(
                pickerGroups = listOf(
                    PickerGroup(
                        MuscleGroup.ARMS,
                        listOf(
                            pickerRow("l:Zottman_Curl", "Zottman Curl", "Zottman_Curl"),
                            pickerRow("l:Pull-Up", "Pull-Up", "Pull-Up"),
                        ),
                    ),
                ),
            ),
        )

        compose.onNodeWithText("Zottman Curl").assertExists()
        compose.onNodeWithText("Pull-Up").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-library-pictures.png")
    }

    @Test
    fun `how to do it with a picture shows both frames and the licence credit`() {
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

        compose.onNodeWithText("Start").assertExists()
        compose.onNodeWithText("End").assertExists()
        compose.onNodeWithText("Exercise illustrations", substring = true).assertExists()
        compose.onNodeWithText("CC BY-SA 4.0", substring = true).assertExists()
        compose.onAllNodes(hasContentDescription("Illustration of Zottman Curl")).assertCountEquals(2)
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/routines-library-detail-frames.png")
    }

    @Test
    fun `how to do it without a picture shows no frames and no credit`() {
        val pullUp = zottman.copy(id = "Pull-Up", name = "Pull-Up", namePt = "Barra fixa")
        compose.setContent {
            BoraTreinoTheme {
                ExerciseDetailDialog(
                    detail = LibraryDetail(exercise = pullUp),
                    portuguese = false,
                    onAdd = {},
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("How to do it").assertExists()
        compose.onNodeWithText("Start").assertDoesNotExist()
        compose.onNodeWithText("End").assertDoesNotExist()
        compose.onNodeWithText("Exercise illustrations", substring = true).assertDoesNotExist()
    }
}
