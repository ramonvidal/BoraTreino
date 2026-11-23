package com.boratreino.core.scan

import android.net.Uri
import com.boratreino.core.model.BikeReading
import com.boratreino.core.model.OcrLine

/** Reads the values off a photo of the bike console. Only fills a draft; it never logs anything. */
interface BikeScreenScanner {
    /**
     * The values found in the picture at [image] (a content or file URI), or `null` when the picture
     * couldn't be opened or read. A picture with no recognisable values gives an empty reading
     * ([BikeReading.isEmpty]), not `null`.
     */
    suspend fun read(image: Uri): BikeReading?

    /** Like [read], but also hands back what the text recognition returned, to see why a read failed. */
    suspend fun scan(image: Uri): BikeScanResult = BikeScanResult(read(image), emptyList(), null)
}

/** [reading] as in [BikeScreenScanner.read]; [lines] the raw recognised lines; [error] the failure, if any. */
data class BikeScanResult(val reading: BikeReading?, val lines: List<OcrLine>, val error: String?)
