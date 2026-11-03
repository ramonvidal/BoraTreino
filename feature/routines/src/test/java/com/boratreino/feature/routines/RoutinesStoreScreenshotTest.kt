package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.boratreino.core.model.Routine
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Play listing screenshot in English: the Routines tab with the gym and the routines below (3). Saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w360dp-h720dp-xxhdpi")
class RoutinesStoreScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /** Light theme on the app background at normal text size, the way the listing shows it. */
    @Composable
    private fun StoreFrame(content: @Composable () -> Unit) {
        BoraTreinoTheme(darkTheme = false) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content()
                }
            }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent { StoreFrame(content) }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    /** The populated gym sample, with the routines named in English. */
    private val englishRoutines = listOf(
        RoutineRow(routine(1L, "Routine A", "A", 0), machineCount = 4),
        RoutineRow(routine(2L, "Routine B", "B", 1), machineCount = 4),
        RoutineRow(routine(3L, "Routine C", "C", 2), machineCount = 3),
    )

    private fun routine(id: Long, name: String, shortName: String, position: Int) =
        Routine(id = id, planId = 1L, name = name, shortName = shortName, position = position, weekdays = emptySet())

    @Test
    fun `store 3 routines`() {
        shoot("store-en-3-routines") {
            RoutinesContent(
                state = GymSamples.populated.copy(routines = englishRoutines),
                callbacks = RoutinesCallbacks(),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
