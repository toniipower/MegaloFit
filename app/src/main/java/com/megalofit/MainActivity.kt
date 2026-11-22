package com.megalofit

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.megalofit.ui.MegaloFitRoot
import com.megalofit.ui.theme.MegaloFitTheme

class MainActivity : ComponentActivity() {

    /** Se incrementa cada vez que llega un intent desde la notificación del temporizador. */
    private var openWorkoutSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleIntent(intent)
        setContent {
            MegaloFitTheme {
                MegaloFitRoot(openWorkoutSignal)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_WORKOUT, false) == true) openWorkoutSignal++
    }

    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }
}
