package com.boratreino.ui.updates

import androidx.compose.runtime.Composable
import com.boratreino.feature.update.UpdateBanner
import com.boratreino.feature.update.UpdatesCard

/** The GitHub build updates itself: the banner at the top and the card in Settings are real. */
@Composable
fun UpdateBannerSlot() {
    UpdateBanner()
}

val updatesCardSlot: (@Composable () -> Unit)? = { UpdatesCard() }
