package com.boratreino.core.scan

import android.net.Uri
import com.boratreino.core.model.BikeReading

/** Reads the values off a photo of the bike console. Only fills a draft; it never logs anything. */
interface BikeScreenScanner {
    /**
     * The values found in the picture at [image] (a content or file URI), or `null` when the picture
     * couldn't be opened or read. A picture with no recognisable values gives an empty reading
     * ([BikeReading.isEmpty]), not `null`.
     */
    suspend fun read(image: Uri): BikeReading?
}
