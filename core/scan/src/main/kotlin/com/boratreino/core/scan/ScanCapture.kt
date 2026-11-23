package com.boratreino.core.scan

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Where the camera writes the bike console photo (for `ActivityResultContracts.TakePicture`). */
object ScanCapture {
    private const val DIRECTORY = "scan"
    private const val FILE_NAME = "bike_screen.jpg"

    /**
     * A URI the camera app may write to, in this app's cache. Always the same file, so each new
     * photo replaces the last one and the app never keeps more than one.
     */
    fun newPhotoUri(context: Context): Uri {
        val directory = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(directory, FILE_NAME)
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Must match the provider's authority in this module's AndroidManifest.xml. */
    private fun authority(context: Context) = "${context.packageName}.scanfiles"
}

/** This module's own FileProvider class, so its manifest entry can't clash with other modules' providers. */
class ScanFileProvider : FileProvider()
