package com.boratreino.feature.update

import com.boratreino.core.model.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateUiStateTest {
    private val update = AppUpdate(
        buildNumber = 41,
        versionName = "0.41",
        downloadUrl = "https://github.com/ramonvidal/BoraTreino/releases/download/v0.41/BoraTreino.apk",
        sizeBytes = 12_345_678L,
    )

    @Test
    fun `no update to offer draws nothing`() {
        assertEquals(UpdateUiState(), UpdateState.None.toUiState())
        assertEquals(UpdateBannerKind.IDLE, UpdateState.None.toUiState().kind)
    }

    @Test
    fun `an available update is offered with its version`() {
        val ui = UpdateState.Available(update).toUiState()

        assertEquals(UpdateBannerKind.AVAILABLE, ui.kind)
        assertEquals("0.41", ui.versionName)
    }

    @Test
    fun `a download shows its progress with its version`() {
        val ui = UpdateState.Downloading(update, 0.6f).toUiState()

        assertEquals(UpdateBannerKind.DOWNLOADING, ui.kind)
        assertEquals("0.41", ui.versionName)
        assertEquals(0.6f, ui.progress, 0.0001f)
    }

    @Test
    fun `an update waiting for the install permission asks for it`() {
        assertEquals(UpdateBannerKind.NEEDS_PERMISSION, UpdateState.NeedsPermission(update).toUiState().kind)
    }

    @Test
    fun `a failed download says so`() {
        assertEquals(UpdateBannerKind.FAILED, UpdateState.Failed(update).toUiState().kind)
    }
}
