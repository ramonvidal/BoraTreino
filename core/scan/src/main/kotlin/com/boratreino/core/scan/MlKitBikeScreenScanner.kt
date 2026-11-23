package com.boratreino.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import com.boratreino.core.model.BikeReading
import com.boratreino.core.model.BikeScreenParser
import com.boratreino.core.model.OcrLine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit's bundled Latin text recognition (on the phone, offline), then [BikeScreenParser].
 * The photo is only read into memory; nothing is stored.
 */
@Singleton
internal class MlKitBikeScreenScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) : BikeScreenScanner {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun read(image: Uri): BikeReading? {
        val lines = try {
            val input = withContext(Dispatchers.IO) { loadImage(image) } ?: return null
            recognize(input).toOcrLines()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unreadable file, revoked permission, out of memory on decode, recognition failure.
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
        return BikeScreenParser.parse(lines)
    }

    /**
     * Opens the picture upright. Pictures with a long side up to [MAX_SIDE] go to ML Kit as they are;
     * larger ones (modern phone cameras) are decoded at a fraction of their size first, which is
     * plenty for the console's large digits and much faster.
     */
    private fun loadImage(uri: Uri): InputImage? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        if (longSide <= MAX_SIDE) return InputImage.fromFilePath(context, uri)

        var sampleSize = 1
        while (longSide / sampleSize > MAX_SIDE) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        return InputImage.fromBitmap(bitmap, rotationDegrees(uri))
    }

    /** The EXIF orientation of the picture, so the text is read upright. */
    private fun rotationDegrees(uri: Uri): Int {
        val orientation = context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 90
            ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 180
            ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 270
            else -> 0
        }
    }

    private suspend fun recognize(input: InputImage): Text = suspendCancellableCoroutine { continuation ->
        recognizer.process(input)
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resumeWithException(it) }
            .addOnCanceledListener { continuation.cancel() }
    }

    private fun Text.toOcrLines(): List<OcrLine> = textBlocks.flatMap { block ->
        block.lines.map { line ->
            val box = line.boundingBox
            OcrLine(
                text = line.text,
                left = box?.left ?: 0,
                top = box?.top ?: 0,
                right = box?.right ?: 0,
                bottom = box?.bottom ?: 0,
            )
        }
    }

    private companion object {
        /** Longest side, in pixels, the picture is read at. */
        const val MAX_SIDE = 2048
    }
}
