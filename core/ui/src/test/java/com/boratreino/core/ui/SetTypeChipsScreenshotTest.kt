package com.boratreino.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.SetType
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The four set-type chips and their labels; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class SetTypeChipsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun setContent(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface {
                        Column(Modifier.padding(16.dp)) { content() }
                    }
                }
            }
        }
    }

    private fun shoot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Composable
    private fun AllSelections() {
        SetType.entries.forEach { type ->
            SetTypeChips(selected = type, onSelect = {}, modifier = Modifier.padding(bottom = 12.dp))
        }
    }

    @Test
    fun `normal selected`() {
        setContent { SetTypeChips(selected = SetType.NORMAL, onSelect = {}) }
        shoot("set-type-chips-normal")
    }

    @Test
    fun `warm-up selected`() {
        setContent { SetTypeChips(selected = SetType.WARMUP, onSelect = {}) }
        shoot("set-type-chips-warmup")
    }

    @Test
    fun `drop selected`() {
        setContent { SetTypeChips(selected = SetType.DROP, onSelect = {}) }
        shoot("set-type-chips-drop")
    }

    @Test
    fun `failure selected`() {
        setContent { SetTypeChips(selected = SetType.FAILURE, onSelect = {}) }
        shoot("set-type-chips-failure")
    }

    @Test
    fun `every selection light`() {
        setContent { AllSelections() }
        shoot("set-type-chips-all")
    }

    @Test
    fun `every selection dark`() {
        setContent(dark = true) { AllSelections() }
        shoot("set-type-chips-all-dark")
    }

    @Test
    fun `every selection with a large font`() {
        setContent(fontScale = 2f) { AllSelections() }
        shoot("set-type-chips-large-font")
    }

    @Test
    fun `the labels are the English names`() {
        val labels = mutableMapOf<SetType, String>()
        setContent {
            SetType.entries.forEach { labels[it] = setTypeLabel(it) }
        }
        assertEquals(
            mapOf(
                SetType.NORMAL to "Normal",
                SetType.WARMUP to "Warm-up",
                SetType.DROP to "Drop set",
                SetType.FAILURE to "Failure",
            ),
            labels,
        )
    }

    @Test
    fun `all four chips are shown`() {
        setContent { SetTypeChips(selected = SetType.NORMAL, onSelect = {}) }
        compose.onNodeWithText("Normal").assertExists()
        compose.onNodeWithText("Warm-up").assertExists()
        compose.onNodeWithText("Drop set").assertExists()
        compose.onNodeWithText("Failure").assertExists()
    }

    @Test
    fun `tapping a chip selects its type`() {
        val picked = mutableListOf<SetType>()
        setContent { SetTypeChips(selected = SetType.NORMAL, onSelect = { picked += it }) }

        compose.onNodeWithText("Drop set").performClick()
        compose.onNodeWithText("Warm-up").performClick()
        compose.onNodeWithText("Failure").performClick()

        assertEquals(listOf(SetType.DROP, SetType.WARMUP, SetType.FAILURE), picked)
    }
}

/** The same chips in Baianês. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h600dp")
class SetTypeChipsPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `chips pt`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Column(Modifier.padding(16.dp)) {
                        SetTypeChips(selected = SetType.WARMUP, onSelect = {}, modifier = Modifier.padding(bottom = 12.dp))
                        SetTypeChips(selected = SetType.FAILURE, onSelect = {})
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/set-type-chips-pt.png")
    }
}
