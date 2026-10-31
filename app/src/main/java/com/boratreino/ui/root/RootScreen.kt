package com.boratreino.ui.root

import androidx.compose.runtime.Composable
import com.boratreino.ui.navigation.BoraTreinoNavHost

/** No accounts and no sign-in: the app opens straight onto its tabs. */
@Composable
fun RootScreen() {
    BoraTreinoNavHost()
}
