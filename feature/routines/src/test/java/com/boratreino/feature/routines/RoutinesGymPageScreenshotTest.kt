package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Gym page of the Routines tab; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class RoutinesGymPageScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setTab(state: RoutinesUiState, page: RoutinesPage?, dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxSize()) {
                        RoutinesContent(state = state, callbacks = RoutinesCallbacks(), modifier = Modifier.fillMaxSize(), initialPage = page)
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun `gym page with machines`() {
        setTab(GymSamples.populated, RoutinesPage.GYM)
        shoot("routines-gym-page")

        compose.onNodeWithText("Exercises \u00b7 8").assertExists()
        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("Add exercises").assertExists()
        compose.onNodeWithText("New exercise").assertExists()
    }

    @Test
    fun `gym page is the first install default`() {
        // No page chosen and no machines: the Gym page opens by itself with the starter list open.
        setTab(GymSamples.emptyGym, page = null)
        shoot("routines-gym-page-empty")

        compose.onNodeWithText("Exercises \u00b7 0").assertExists()
        compose.onNodeWithText("Which routine is next").assertDoesNotExist()
    }

    @Test
    fun `gym page dark`() {
        setTab(GymSamples.populated, RoutinesPage.GYM, dark = true)
        shoot("routines-gym-page-dark")
    }

    @Test
    fun `gym page large font`() {
        setTab(GymSamples.populated, RoutinesPage.GYM, fontScale = 1.6f)
        shoot("routines-gym-page-large-font")
        compose.onNodeWithText("Exercises \u00b7 8").assertExists()
    }
}

/** The Gym page in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h1600dp")
class RoutinesGymPagePtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `gym page pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = GymSamples.populated.copy(portuguese = true),
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                        initialPage = RoutinesPage.GYM,
                    )
                }
            }
        }
        compose.onNodeWithText("Exercícios · 8").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-gym-page-pt.png")
    }

    @Test
    fun `gym page empty pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxSize()) {
                    RoutinesContent(
                        state = GymSamples.emptyGym.copy(portuguese = true),
                        callbacks = RoutinesCallbacks(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        compose.onNodeWithText("Exercícios · 0").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/routines-gym-page-empty-pt.png")
    }
}
