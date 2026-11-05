package com.boratreino.feature.progress

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.boratreino.core.ui.theme.BoraTreinoTheme

/** The app theme (light or dark, at a font scale) around a full-screen Stats page. */
@Composable
internal fun StatsScreenHost(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
    BoraTreinoTheme(darkTheme = dark) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            Surface(Modifier.fillMaxSize()) { content() }
        }
    }
}

/** The app theme around one card, with the page's margin, so a card can be shot or tested alone. */
@Composable
internal fun StatsCardHost(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
    StatsScreenHost(dark, fontScale) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }
}
