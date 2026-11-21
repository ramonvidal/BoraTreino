package com.boratreino.feature.progress

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp

/**
 * Taps one figure of the body map card whose figures have the description [description], at the point ([gridX],
 * [gridY]) of the 100 x 220 grid. The row holds the front figure on the left and the back one on the right, with 16 dp
 * between them, and each figure is as wide as half of the row less that gap.
 */
internal fun ComposeTestRule.tapBodyMap(description: String, front: Boolean, gridX: Float, gridY: Float) {
    val row = onNodeWithContentDescription(description)
    val gap = with(density) { 16.dp.toPx() }
    val figure = (row.fetchSemanticsNode().size.width - gap) / 2f
    val scale = figure / BodyMapShapes.WIDTH
    val left = if (front) 0f else figure + gap
    row.performTouchInput { click(Offset(left + gridX * scale, gridY * scale)) }
}
