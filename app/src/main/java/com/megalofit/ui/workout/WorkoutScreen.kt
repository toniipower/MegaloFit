package com.megalofit.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.megalofit.timer.PhaseType
import com.megalofit.timer.TimerState
import com.megalofit.timer.WorkoutTimer
import com.megalofit.timer.WorkoutTimerService
import com.megalofit.util.ceilSeconds
import com.megalofit.util.formatClock
import com.megalofit.util.formatElapsed

@Composable
fun WorkoutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val s by WorkoutTimer.engine.state.collectAsStateWithLifecycle()
    var confirmStop by remember { mutableStateOf(false) }

    // Pantalla encendida mientras miras el temporizador.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    when {
        !s.active -> NoWorkout(onClose)
        s.finished -> Finished(s) {
            WorkoutTimerService.dismiss(context)
            onClose()
        }
        else -> ActiveWorkout(
            s = s,
            onMinimize = onClose,
            onStop = { confirmStop = true },
            onToggle = { WorkoutTimerService.send(context, WorkoutTimerService.ACTION_TOGGLE) },
            onNext = { WorkoutTimerService.send(context, WorkoutTimerService.ACTION_NEXT) },
            onPrevious = { WorkoutTimerService.send(context, WorkoutTimerService.ACTION_PREVIOUS) },
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("¿Terminar entrenamiento?") },
            text = { Text("Se detendrá el temporizador.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmStop = false
                        WorkoutTimerService.send(context, WorkoutTimerService.ACTION_STOP)
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Terminar") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Seguir") } },
        )
    }
}

@Composable
private fun ActiveWorkout(
    s: TimerState,
    onMinimize: () -> Unit,
    onStop: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val step = s.current ?: return
    val scheme = MaterialTheme.colorScheme
    val isRest = step.type == PhaseType.REST
    val accent = if (isRest) Color.White else scheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMinimize) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimizar") }
            Column(Modifier.weight(1f)) {
                Text(s.routineName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "Paso ${s.index + 1}/${s.steps.size} · ${formatElapsed(s.elapsedMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onStop) { Icon(Icons.Default.Close, contentDescription = "Terminar") }
        }
        LinearProgressIndicator(
            progress = { (s.index + 1f) / s.steps.size },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            color = scheme.primary,
            trackColor = scheme.surfaceContainer,
        )

        Spacer(Modifier.weight(1f))

        Text(
            when {
                !s.running -> "EN PAUSA"
                isRest -> "DESCANSO"
                else -> "SERIE ${step.setNumber} DE ${step.totalSets}"
            },
            style = MaterialTheme.typography.labelLarge,
            color = accent,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (isRest) step.nextLabel.orEmpty() else step.exerciseName,
            style = if (isRest) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            color = if (isRest) scheme.onSurfaceVariant else scheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(280.dp)) {
            if (step.isTimed) {
                CircularProgressIndicator(
                    progress = { (s.remainingMs / step.durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxSize(),
                    color = accent,
                    strokeWidth = 10.dp,
                    trackColor = scheme.surfaceContainer,
                    strokeCap = StrokeCap.Round,
                )
                Text(formatClock(ceilSeconds(s.remainingMs)), style = MaterialTheme.typography.displayLarge.copy(fontSize = 76.sp))
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${step.reps}", style = MaterialTheme.typography.displayLarge.copy(fontSize = 110.sp), color = accent)
                    Text("REPETICIONES", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (!step.isTimed) {
            Button(
                onClick = onNext,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) { Text("HECHO", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Paso anterior")
            }
            FilledIconButton(
                onClick = onToggle,
                modifier = Modifier.size(88.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = scheme.primary, contentColor = scheme.onPrimary),
            ) {
                Icon(
                    if (s.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (s.running) "Pausa" else "Reanudar",
                    modifier = Modifier.size(40.dp),
                )
            }
            FilledTonalIconButton(onClick = onNext, modifier = Modifier.size(64.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Saltar paso")
            }
        }
    }
}

@Composable
private fun Finished(s: TimerState, onClose: () -> Unit) {
    val sets = s.steps.count { it.type == PhaseType.WORK }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("¡HECHO!", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(s.routineName, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text(
            "${formatElapsed(s.elapsedMs)} · $sets series",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Button(onClick = onClose, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("CERRAR", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun NoWorkout(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No hay ningún entrenamiento en curso", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClose) { Text("Volver") }
    }
}
