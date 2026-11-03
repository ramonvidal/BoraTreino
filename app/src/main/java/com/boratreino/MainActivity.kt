package com.boratreino

import android.os.Bundle
import androidx.activity.compose.setContent
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
        enableEdgeToEdge()
        setContent {
            BoraTreinoTheme {
                RootScreen()
            }
        }
    }
}
