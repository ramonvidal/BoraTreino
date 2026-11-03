package com.boratreino.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.boratreino.core.model.ExerciseImages
import com.boratreino.core.model.Machine
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.model.StarterMachines

/** The pictures are square, this many pixels on a side. */
private const val SOURCE_PX = 360

/**
 * Exercise pictures from the app's assets, decoded synchronously (they are small WebP files) and kept
 * in an in-memory cache of about 60 bitmaps keyed by picture, frame and sample size.
 */
private object ExerciseBitmaps {
    private val cache = LruCache<String, Bitmap>(60)

    /** Frame 1 (start) or 2 (end) of [slug] at 1/[sampleSize] scale; null when missing or undecodable. */
    fun load(context: Context, slug: String, frame: Int, sampleSize: Int): ImageBitmap? {
        val key = "$slug-$frame@$sampleSize"
        cache.get(key)?.let { return it.asImageBitmap() }
        val decoded = decode(context, "$slug-$frame", sampleSize) ?: return null
        cache.put(key, decoded)
        return decoded.asImageBitmap()
    }

    /** The largest power of two that still leaves at least [targetPx] pixels. */
    fun sampleSizeFor(targetPx: Int): Int {
        var sample = 1
        while (SOURCE_PX / (sample * 2) >= targetPx) sample *= 2
        return sample
    }
}

private fun decode(context: Context, name: String, sampleSize: Int): Bitmap? =
    try {
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.assets.open("exercises/$name.webp").use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
        null
    }

/** A white square with one frame of an exercise picture; the placeholder icon when there is none. */
@Composable
private fun ExerciseTile(slug: String?, frame: Int, targetSize: Dp, description: String, modifier: Modifier) {
    val context = LocalContext.current
    val targetPx = with(LocalDensity.current) { targetSize.roundToPx() }
    val sampleSize = ExerciseBitmaps.sampleSizeFor(targetPx)
    val bitmap = remember(slug, frame, sampleSize) {
        if (slug == null) null else ExerciseBitmaps.load(context, slug, frame, sampleSize)
    }
    val shape = RoundedCornerShape(10.dp)
    if (bitmap != null) {
        Box(modifier.clip(shape).background(Color.White).semantics { contentDescription = description }) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.matchParentSize())
        }
    } else {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_today),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * A small tile with the picture of the machine named [name] (white background in both themes), or a
 * neutral tile when there is none. Same footprint either way. [onClick] only applies when a picture exists.
 */
@Composable
fun MachineThumb(
    name: String,
    muscleGroup: MuscleGroup,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    val slug = remember(name) { StarterMachines.imageFor(name) }
    val description = if (slug != null) stringResource(R.string.exercise_image_description, name) else stringResource(R.string.exercise_no_image)
    val clickable = if (slug != null && onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    ExerciseTile(slug, frame = 1, targetSize = size, description = description, modifier = modifier.size(size).then(clickable))
}

/** [MachineThumb] for a stored machine. */
@Composable
fun MachineThumb(
    machine: Machine,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    MachineThumb(machine.name, machine.muscleGroup, modifier, size, onClick)
}

/** A dialog with both frames (start and end) of the machine's picture and the licence credit. */
@Composable
fun ExercisePreviewDialog(machine: Machine, onDismiss: () -> Unit) {
    val slug = remember(machine.name) { StarterMachines.imageFor(machine.name) }
    val description = stringResource(R.string.exercise_image_description, machine.name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(machine.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Frame(slug, 1, stringResource(R.string.exercise_start), description, Modifier.weight(1f))
                    Frame(slug, 2, stringResource(R.string.exercise_end), description, Modifier.weight(1f))
                }
                ExerciseCredit(style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.exercise_close)) }
        },
    )
}

@Composable
private fun Frame(slug: String?, frame: Int, label: String, description: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ExerciseTile(slug, frame, 150.dp, description, Modifier.widthIn(max = 150.dp).fillMaxWidth().aspectRatio(1f))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Exercise illustrations: Everkinetic (Greg Priday), CC BY-SA 4.0", where the licence name opens
 * [ExerciseImages.LICENSE_URL].
 */
@Composable
fun ExerciseCredit(
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val uriHandler = LocalUriHandler.current
    val license = stringResource(R.string.exercise_license_name)
    val full = stringResource(R.string.exercise_credit, license)
    val linkColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        val start = full.lastIndexOf(license)
        if (start < 0) {
            append(full)
        } else {
            append(full.substring(0, start))
            withLink(
                LinkAnnotation.Clickable(
                    tag = "license",
                    styles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                    linkInteractionListener = { uriHandler.openUri(ExerciseImages.LICENSE_URL) },
                ),
            ) { append(license) }
            append(full.substring(start + license.length))
        }
    }
    Text(text, modifier = modifier, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), textAlign = TextAlign.Start)
}
