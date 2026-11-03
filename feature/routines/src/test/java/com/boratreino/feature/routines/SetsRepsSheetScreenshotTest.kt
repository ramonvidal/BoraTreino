package com.boratreino.feature.routines

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.RoutineItem
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val sheetEntry = RoutineEditorItem(
    machineName = "Leg press",
    item = RoutineItem(routineId = 2L, machineId = 1L, position = 0, targetSets = 3, repsMin = 8, repsMax = 12),
)

/** What is inside the sets / reps bottom sheet (the stateless content, not the sheet itself); saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h900dp")
class SetsRepsSheetScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setSheet(callbacks: RoutineEditorCallbacks = RoutineEditorCallbacks(), dark: Boolean = false, fontScale: Float = 1f) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxWidth()) {
                        SetsRepsSheetContent(entry = sheetEntry, index = 4, callbacks = callbacks)
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun `sheet shows the machine, the target and the three steppers`() {
        setSheet()
        shoot("sets-reps-sheet")

        compose.onNodeWithText("Leg press").assertExists()
        compose.onNodeWithText("3 × 8–12").assertExists()
        compose.onNodeWithText("Sets").assertExists()
        compose.onNodeWithText("Reps from").assertExists()
        compose.onNodeWithText("Reps to").assertExists()
        compose.onNodeWithText("Close").assertExists()
    }

    @Test
    fun `sheet dark`() {
        setSheet(dark = true)
        shoot("sets-reps-sheet-dark")
    }

    @Test
    fun `sheet large font`() {
        setSheet(fontScale = 1.6f)
        shoot("sets-reps-sheet-large-font")
        compose.onNodeWithText("Close").assertExists()
    }

    @Test
    fun `steppers and Close call back with the row index`() {
        val calls = mutableListOf<String>()
        setSheet(
            RoutineEditorCallbacks(
                onSetsStep = { i, up -> calls += "sets $i $up" },
                onRepsMinStep = { i, up -> calls += "min $i $up" },
                onRepsMaxStep = { i, up -> calls += "max $i $up" },
                onCloseTarget = { calls += "close" },
            ),
        )

        compose.onNodeWithContentDescription("Increase Sets").performClick()
        compose.onNodeWithContentDescription("Decrease Sets").performClick()
        compose.onNodeWithContentDescription("Increase Reps from").performClick()
        compose.onNodeWithContentDescription("Decrease Reps to").performClick()
        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("sets 4 true", "sets 4 false", "min 4 true", "max 4 false", "close"), calls)
    }
}

/** The sheet content in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h900dp")
class SetsRepsSheetPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sheet pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxWidth()) {
                    SetsRepsSheetContent(entry = sheetEntry, index = 0, callbacks = RoutineEditorCallbacks())
                }
            }
        }
        compose.onNodeWithText("Fechar").assertExists()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/sets-reps-sheet-pt.png")
    }
}
