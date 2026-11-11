package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.Sex
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The mascot: it renders for each sex (and for none), it is a square of the size asked, and it is decorative. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class MascotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the mascot renders for a woman, a man and no sex chosen`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mascot(sex = Sex.FEMALE, modifier = Modifier.testTag("mascot-female"), size = 96.dp)
                        Mascot(sex = Sex.MALE, modifier = Modifier.testTag("mascot-male"), size = 96.dp)
                        Mascot(sex = null, modifier = Modifier.testTag("mascot-none"), size = 96.dp)
                    }
                }
            }
        }

        compose.onNodeWithTag("mascot-female").assertExists()
        compose.onNodeWithTag("mascot-male").assertExists()
        compose.onNodeWithTag("mascot-none").assertExists()
    }

    @Test
    fun `the mascot is the size asked for`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Mascot(sex = Sex.MALE, modifier = Modifier.testTag("mascot"), size = 96.dp)
                }
            }
        }

        compose.onNodeWithTag("mascot").assertWidthIsEqualTo(96.dp)
        compose.onNodeWithTag("mascot").assertHeightIsEqualTo(96.dp)
    }

    @Test
    fun `the default mascot is 140 dp square`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Mascot(sex = Sex.FEMALE, modifier = Modifier.testTag("mascot"))
                }
            }
        }

        compose.onNodeWithTag("mascot").assertWidthIsEqualTo(140.dp)
        compose.onNodeWithTag("mascot").assertHeightIsEqualTo(140.dp)
    }

    @Test
    fun `the mascot is decorative and has no content description`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Mascot(sex = Sex.FEMALE, modifier = Modifier.testTag("mascot"), size = 96.dp)
                }
            }
        }

        val description = compose.onNodeWithTag("mascot", useUnmergedTree = true)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.ContentDescription)
        assertNull(description)
    }

    @Test
    fun `the man and the woman side by side are captured`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Mascot(sex = Sex.MALE, size = 160.dp)
                        Mascot(sex = Sex.FEMALE, size = 160.dp)
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/mascot-man-and-woman.png")
    }
}
