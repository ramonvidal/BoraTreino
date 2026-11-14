package com.boratreino.core.notify

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/** The optional short beep when a rest ends. Never throws (cancellation aside). */
internal object RestBeep {
    private const val VOLUME = 80
    private const val DURATION_MS = 400
    private const val RELEASE_AFTER_MS = 600L
    private const val WAIT_MARGIN_MS = 150L

    /** Starts the beep and returns at once; for callers whose process stays alive (the app in use). */
    fun play() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, DURATION_MS)
            Handler(Looper.getMainLooper()).postDelayed({ release(tone) }, RELEASE_AFTER_MS)
        } catch (e: Throwable) {
            // No audio available: the buzz still happened.
        }
    }

    /**
     * Plays the beep and returns only after it ended, so a receiver can keep its broadcast open (goAsync) until
     * the tone was heard: finishing earlier lets the system freeze the process and cut the tone.
     */
    suspend fun playAndWait() {
        var tone: ToneGenerator? = null
        try {
            tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, DURATION_MS)
            delay(DURATION_MS + WAIT_MARGIN_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // No audio available: the buzz still happened.
        } finally {
            tone?.let { release(it) }
        }
    }

    private fun release(tone: ToneGenerator) {
        try {
            tone.release()
        } catch (e: Throwable) {
            // Already released.
        }
    }
}
