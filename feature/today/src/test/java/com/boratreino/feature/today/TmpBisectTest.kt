package com.boratreino.feature.today

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.boratreino.core.model.MuscleGroup
import com.boratreino.core.ui.ExerciseCredit
import com.boratreino.core.ui.MachineThumb
import com.boratreino.core.ui.theme.BoraTreinoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h1600dp")
class TmpBisectTest {
    @get:Rule
    val compose = createComposeRule()

    private fun dlg(body: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            BoraTreinoTheme(darkTheme = false) {
                AlertDialog(onDismissRequest = {}, title = { Text("T") }, text = { body() },
                    confirmButton = { TextButton(onClick = {}) { Text("Close") } })
            }
        }
    }

    @Test fun a_plain() = dlg { Text("hello") }
    @Test fun b_credit() = dlg { ExerciseCredit() }
    @Test fun c_thumbWithPicture() = dlg { MachineThumb("Leg press", MuscleGroup.LEGS, size = 130.dp) }
    @Test fun d_thumbPlaceholder() = dlg { MachineThumb("Zzz unknown", MuscleGroup.LEGS, size = 130.dp) }
}
