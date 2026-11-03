package com.boratreino.core.notify

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** The optional short beep when a rest ends. Never throws. */
internal object RestBeep {
    private const val VOLUME = 80
    private const val DURATION_MS = 400
    private const val RELEASE_AFTER_MS = 600L

    fun play() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, DURATION_MS)
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    tone.release()
                } catch (e: Throwable) {
                    // Already released.
                }
            }, RELEASE_AFTER_MS)
        } catch (e: Throwable) {
            // No audio available: the buzz still happened.
        }
    }
}
