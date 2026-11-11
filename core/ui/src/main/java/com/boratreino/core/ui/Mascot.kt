package com.boratreino.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.Sex

/**
 * The app's character: the woman for [Sex.FEMALE], the man for [Sex.MALE] or null (the man until a sex is chosen).
 * A [size] x [size] square with 28 dp corners; decorative, so no content description.
 */
@Composable
fun Mascot(
    sex: Sex?,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
) {
    val picture = if (sex == Sex.FEMALE) R.drawable.mascot_woman else R.drawable.mascot_man
    Image(
        painter = painterResource(picture),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(RoundedCornerShape(28.dp)),
    )
}
