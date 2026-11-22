package com.boratreino.feature.progress

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.CardioBlock
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** The Habit page's Cardio card; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class CardioCardScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val blocks = listOf(
        CardioBlock(LocalDate.of(2026, 10, 5), 90, 22_000, 2, 11.0, 23.4),
        CardioBlock(LocalDate.of(2026, 10, 12), 135, 40_000, 2, 12.0, 24.1),
        CardioBlock(LocalDate.of(2026, 10, 19), 240, 52_400, 3, 13.0, 24.8),
        CardioBlock(LocalDate.of(2026, 10, 26), 60, 0, 1, null, null),
    )

    private fun shoot(name: String, dark: Boolean = false) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize()) {
                    CardioCard(StatsSamples.full.copy(cardioBlocks = blocks))
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `cardio card`() = shoot("stats-cardio")

    @Test
    fun `cardio card dark`() = shoot("stats-cardio-dark", dark = true)
}
