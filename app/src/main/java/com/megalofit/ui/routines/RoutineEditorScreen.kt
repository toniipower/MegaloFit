package com.megalofit.ui.routines

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.megalofit.data.FitRepository
import com.megalofit.data.RoutineItem
import com.megalofit.data.RoutineItemWithExercise
import com.megalofit.timer.WorkoutPlan
import com.megalofit.timer.WorkoutTimer
import com.megalofit.timer.WorkoutTimerService
import com.megalofit.ui.components.GridTile
import com.megalofit.ui.components.ScreenHeader
import com.megalofit.ui.components.Stepper
import com.megalofit.ui.components.TextInputDialog
import com.megalofit.ui.components.rememberRepository
import com.megalofit.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineEditorViewModel(private val repo: FitRepository, private val routineId: Long) : ViewModel() {
    val routine = repo.observeRoutine(routineId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val items = repo.observeItems(routineId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun rename(name: String) {
        viewModelScope.launch { routine.value?.let { repo.updateRoutine(it.copy(name = name)) } }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            routine.value?.let { repo.deleteRoutine(it) }
            onDone()
        }
    }

    fun update(item: RoutineItem) {
        viewModelScope.launch { repo.updateItem(item) }
    }

    fun remove(item: RoutineItem) {
        viewModelScope.launch { repo.deleteItem(item) }
    }

    fun move(item: RoutineItem, delta: Int) {
        viewModelScope.launch { repo.moveItem(routineId, item.id, delta) }
    }
}

@Composable
fun RoutineEditorScreen(
    routineId: Long,
    onBack: () -> Unit,
    onAddExercises: () -> Unit,
    onOpenWorkout: () -> Unit,
) {
    val repo = rememberRepository()
    val vm = viewModel(key = "routine-$routineId") { RoutineEditorViewModel(repo, routineId) }
    val routine by vm.routine.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val timer by WorkoutTimer.engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by remember { mutableStateOf<RoutineItemWithExercise?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val thisWorkoutRunning = timer.active && !timer.finished && timer.routineId == routineId

    val startWorkout = {
        WorkoutTimerService.start(context, routineId)
        onOpenWorkout()
    }
    // Android 13+: la notificación del temporizador necesita permiso. Si lo deniegan, el
    // temporizador funciona igual, solo que sin notificación visible.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { startWorkout() }
    val onStartClick = {
        when {
            thisWorkoutRunning -> onOpenWorkout()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> startWorkout()
        }
    }

    val minutes = (WorkoutPlan.estimateSeconds(items) + 59) / 60
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = routine?.name.orEmpty(),
            subtitle = if (items.isEmpty()) "Añade ejercicios para empezar" else "${plural(items.size, "ejercicio", "ejercicios")} · ≈ $minutes min",
            onBack = onBack,
        ) {
            IconButton(onClick = { renaming = true }) { Icon(Icons.Default.Edit, contentDescription = "Renombrar") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = "Eliminar rutina") }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(items, key = { _, row -> row.item.id }) { index, row ->
                GridTile(onClick = { editing = row }) {
                    Text("%02d".format(index + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(row.exercise.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(row.item.prescription(), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Descanso ${row.item.restSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(key = "add") {
                GridTile(onClick = onAddExercises, outlined = true) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("Añadir ejercicios", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        Button(
            onClick = onStartClick,
            enabled = items.isNotEmpty(),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(64.dp),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (thisWorkoutRunning) "VOLVER AL ENTRENAMIENTO" else "EMPEZAR", style = MaterialTheme.typography.titleMedium)
        }
    }

    editing?.let { row ->
        val index = items.indexOfFirst { it.item.id == row.item.id }
        ItemEditorDialog(
            row = row,
            canMoveBack = index > 0,
            canMoveForward = index in 0 until items.lastIndex,
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
            onMove = { delta -> vm.move(row.item, delta); editing = null },
            onRemove = { vm.remove(row.item); editing = null },
        )
    }

    if (renaming) {
        TextInputDialog(
            title = "Renombrar rutina",
            initial = routine?.name.orEmpty(),
            confirmLabel = "Guardar",
            onDismiss = { renaming = false },
            onConfirm = { vm.rename(it); renaming = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("¿Eliminar rutina?") },
            text = { Text("Se borrará \"${routine?.name.orEmpty()}\". Los ejercicios del catálogo no se tocan.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; vm.delete(onBack) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ItemEditorDialog(
    row: RoutineItemWithExercise,
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onDismiss: () -> Unit,
    onSave: (RoutineItem) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    var item by remember(row.item.id) { mutableStateOf(row.item) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(row.exercise.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !item.isTimed, onClick = { item = item.copy(workSeconds = 0) }, label = { Text("Repeticiones") })
                    FilterChip(
                        selected = item.isTimed,
                        onClick = { if (!item.isTimed) item = item.copy(workSeconds = 30) },
                        label = { Text("Tiempo") },
                    )
                }
                Stepper(
                    label = "Series",
                    value = "${item.sets}",
                    onMinus = { item = item.copy(sets = (item.sets - 1).coerceAtLeast(1)) },
                    onPlus = { item = item.copy(sets = (item.sets + 1).coerceAtMost(20)) },
                )
                if (item.isTimed) {
                    Stepper(
                        label = "Trabajo",
                        value = "${item.workSeconds}s",
                        onMinus = { item = item.copy(workSeconds = (item.workSeconds - 5).coerceAtLeast(5)) },
                        onPlus = { item = item.copy(workSeconds = (item.workSeconds + 5).coerceAtMost(600)) },
                    )
                } else {
                    Stepper(
                        label = "Reps",
                        value = "${item.reps}",
                        onMinus = { item = item.copy(reps = (item.reps - 1).coerceAtLeast(1)) },
                        onPlus = { item = item.copy(reps = (item.reps + 1).coerceAtMost(100)) },
                    )
                }
                Stepper(
                    label = "Descanso",
                    value = "${item.restSeconds}s",
                    onMinus = { item = item.copy(restSeconds = (item.restSeconds - 15).coerceAtLeast(0)) },
                    onPlus = { item = item.copy(restSeconds = (item.restSeconds + 15).coerceAtMost(600)) },
                )
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMove(-1) }, enabled = canMoveBack) { Text("◀ Antes") }
                    TextButton(onClick = { onMove(1) }, enabled = canMoveForward) { Text("Después ▶") }
                }
                TextButton(
                    onClick = onRemove,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Quitar de la rutina")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(item) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
