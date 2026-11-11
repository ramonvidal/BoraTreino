package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Sex
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Sunny components together on one screen: chips, the three buttons, a card and the two mascots. Records only. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class SunnyComponentsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sunny components`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Chips")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BoraChip(label = "Legs", selected = true, onClick = {})
                            BoraChip(label = "Back", selected = false, onClick = {})
                            BoraChip(label = "Arms", selected = false, onClick = {}, large = true)
                        }

                        Text("Buttons")
                        BoraButton(text = "Finish training", onClick = {}, modifier = Modifier.fillMaxWidth())
                        BoraButton(
                            text = "Add exercise",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth(),
                            style = BoraButtonStyle.Tonal,
                        )
                        BoraButton(text = "Train anyway", onClick = {}, style = BoraButtonStyle.Text)

                        Text("Card")
                        BoraCard(modifier = Modifier.fillMaxWidth()) {
                            Text("Next up: Leg press")
                            Text("2 of 4 exercises done")
                        }

                        Text("Mascots")
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Mascot(sex = Sex.MALE, size = 140.dp)
                            Mascot(sex = Sex.FEMALE, size = 140.dp)
                        }
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/sunny-components.png")
    }
}
