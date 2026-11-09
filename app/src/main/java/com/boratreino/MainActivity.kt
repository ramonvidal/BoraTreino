package com.boratreino

import android.os.Bundle
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.boratreino.core.ui.theme.BoraTreinoTheme
import com.boratreino.ui.root.RootScreen
import dagger.hilt.android.AndroidEntryPoint

/** An AppCompat activity so the language picked in Settings applies straight away. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The app is always light, so the bars always get dark icons, even when the phone is in dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            BoraTreinoTheme {
                RootScreen()
            }
        }
    }
}
