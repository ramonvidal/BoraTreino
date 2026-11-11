package com.boratreino.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The exercise thumbnail: a picture when there is one, the muscle-group tile when there is not, and an optional description. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h600dp")
class ThumbTileTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a tile with a known picture renders at the size asked`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    ThumbTile(
                        imageSlug = "leg-press",
                        muscleGroup = MuscleGroup.LEGS,
                        modifier = Modifier.testTag("thumb"),
                        size = 64.dp,
                    )
                }
            }
        }

        compose.onNodeWithTag("thumb").assertWidthIsEqualTo(64.dp)
        compose.onNodeWithTag("thumb").assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `a tile with no picture renders for its muscle group`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    ThumbTile(
                        imageSlug = null,
                        muscleGroup = MuscleGroup.CHEST,
                        modifier = Modifier.testTag("thumb"),
                        size = 48.dp,
                    )
                }
            }
        }

        compose.onNodeWithTag("thumb").assertWidthIsEqualTo(48.dp)
        compose.onNodeWithTag("thumb").assertHeightIsEqualTo(48.dp)
    }

    @Test
    fun `the default size is 48 dp`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    ThumbTile(imageSlug = null, muscleGroup = MuscleGroup.BACK, modifier = Modifier.testTag("thumb"))
                }
            }
        }

        compose.onNodeWithTag("thumb").assertWidthIsEqualTo(48.dp)
    }

    @Test
    fun `the given content description is what a reader hears`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    ThumbTile(
                        imageSlug = "leg-press",
                        muscleGroup = MuscleGroup.LEGS,
                        size = 48.dp,
                        contentDescription = "Picture of leg press",
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Picture of leg press").assertExists()
    }

    @Test
    fun `a tile without a picture also takes a content description`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    ThumbTile(
                        imageSlug = null,
                        muscleGroup = MuscleGroup.ARMS,
                        size = 48.dp,
                        contentDescription = "Arms exercise",
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Arms exercise").assertExists()
    }

    @Test
    fun `every muscle group renders a tile with and without a picture`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MuscleGroup.entries.forEach { group ->
                            ThumbTile(imageSlug = null, muscleGroup = group, size = 40.dp)
                            ThumbTile(imageSlug = "bench-press", muscleGroup = group, size = 40.dp)
                        }
                    }
                }
            }
        }

        compose.onRoot().assertExists()
    }

    @Test
    fun `the tiles are captured with and without pictures`() {
        compose.setContent {
            BoraTreinoTheme {
                Surface {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThumbTile(imageSlug = "leg-press", muscleGroup = MuscleGroup.LEGS, size = 64.dp)
                        ThumbTile(imageSlug = null, muscleGroup = MuscleGroup.LEGS, size = 64.dp)
                        ThumbTile(imageSlug = null, muscleGroup = MuscleGroup.CARDIO, size = 64.dp)
                    }
                }
            }
        }

        compose.onRoot().captureRoboImage("build/outputs/roborazzi/thumb-tiles.png")
    }
}
