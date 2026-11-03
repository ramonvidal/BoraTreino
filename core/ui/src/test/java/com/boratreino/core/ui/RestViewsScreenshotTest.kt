package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The compact rest banner and the big rest card; saved to build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h900dp")
class RestViewsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                    Surface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Composable
    private fun Banner(remaining: Int, total: Int) = RestBanner(remaining, total, onExtend = {}, onSkip = {})

    @Composable
    private fun Card(remaining: Int, total: Int) = RestCard(remaining, total, onExtend = {}, onSkip = {})

    @Test
    fun `banner just started`() {
        shoot("rest-banner") { Banner(90, 90) }
        compose.onNodeWithText("Rest 1:30").assertExists()
        compose.onNodeWithText("+30 s").assertExists()
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `banner halfway`() {
        shoot("rest-banner-mid") { Banner(72, 120) }
        compose.onNodeWithText("Rest 1:12").assertExists()
    }

    @Test
    fun `banner almost over`() {
        shoot("rest-banner-almost-over") { Banner(3, 90) }
        compose.onNodeWithText("Rest 0:03").assertExists()
    }

    @Test
    fun `banner dark`() = shoot("rest-banner-dark", dark = true) { Banner(72, 120) }

    @Test
    fun `banner large font`() {
        shoot("rest-banner-large-font", fontScale = 1.6f) { Banner(72, 120) }
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `card just started`() {
        shoot("rest-card") { Card(90, 90) }
        compose.onNodeWithText("Rest").assertExists()
        compose.onNodeWithText("1:30").assertExists()
        compose.onNodeWithText("+30 s").assertExists()
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `card halfway`() {
        shoot("rest-card-mid") { Card(45, 90) }
        compose.onNodeWithText("0:45").assertExists()
    }

    @Test
    fun `card almost over`() {
        shoot("rest-card-almost-over") { Card(2, 90) }
        compose.onNodeWithText("0:02").assertExists()
    }

    @Test
    fun `card dark`() = shoot("rest-card-dark", dark = true) { Card(45, 90) }

    @Test
    fun `card large font`() {
        shoot("rest-card-large-font", fontScale = 1.6f) { Card(45, 90) }
        compose.onNodeWithText("Skip").assertExists()
    }

    @Test
    fun `card with extended duration keeps the bar within range`() {
        // Remaining above the duration must not break the bar (progress is clamped).
        shoot("rest-card-overfull") { Card(120, 90) }
        compose.onNodeWithText("2:00").assertExists()
    }
}

/** The same two views in Baianês, to check the translated text fits. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "pt-rBR-w411dp-h900dp")
class RestViewsPtScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun `banner pt`() {
        shoot("rest-banner-pt") { RestBanner(72, 120, onExtend = {}, onSkip = {}) }
        compose.onNodeWithText("Descanso 1:12").assertExists()
        compose.onNodeWithText("Pular").assertExists()
    }

    @Test
    fun `card pt`() {
        shoot("rest-card-pt") { RestCard(45, 90, onExtend = {}, onSkip = {}) }
        compose.onNodeWithText("Pular").assertExists()
    }
}
