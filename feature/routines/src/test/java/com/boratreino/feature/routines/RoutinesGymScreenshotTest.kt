package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Academia page of the Routines tab: the exercise list and the delete questions; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesGymScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

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

    private fun shoot(name: String, state: RoutinesUiState) {
        setTab(state)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `routines gym empty`() {
        shoot("routines-gym-empty", GymSamples.emptyGym)
        compose.onNodeWithText("Exercises \u00b7 0").assertExists()
    }

    @Test
    fun `routines gym populated`() = shoot("routines-gym", GymSamples.populated)

    @Test
    fun `delete confirm dialog names the routines it leaves`() {
        setTab(GymSamples.populated.copy(deleteDialog = DeleteDialog.Confirm(GymSamples.legCurl, listOf("Treino A", "Treino C"))))

        compose.onNodeWithText("It will also be removed from: Treino A, Treino C.").assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/delete-confirm.png")
    }

    @Test
    fun `delete archive dialog for a machine with 12 sets`() {
        setTab(GymSamples.populated.copy(deleteDialog = DeleteDialog.ArchiveInstead(GymSamples.legCurl, setCount = 12)))

        compose.onNodeWithText("Leg curl has 12 logged sets, so deleting it would erase that history.", substring = true).assertExists()
        compose.onNode(isDialog()).captureRoboImage("build/outputs/roborazzi/delete-archive.png")
    }
}
