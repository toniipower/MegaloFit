package com.megalofit.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.megalofit.data.FitRepository
import com.megalofit.ui.components.GridTile
import com.megalofit.ui.components.ScreenHeader
import com.megalofit.ui.components.TextInputDialog
import com.megalofit.ui.components.rememberRepository
import com.megalofit.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutinesViewModel(private val repo: FitRepository) : ViewModel() {
    val routines = repo.routineSummaries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.createRoutine(name)) }
    }
}

@Composable
fun RoutinesScreen(onOpenRoutine: (Long) -> Unit) {
    val repo = rememberRepository()
    val vm = viewModel { RoutinesViewModel(repo) }
    val routines by vm.routines.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "MegaloFit", subtitle = "Entrena a lo grande")
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "new") {
                GridTile(onClick = { showCreate = true }, outlined = true) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Text("Nueva rutina", style = MaterialTheme.typography.titleMedium)
                }
            }
            itemsIndexed(routines, key = { _, r -> r.routine.id }) { index, summary ->
                GridTile(onClick = { onOpenRoutine(summary.routine.id) }) {
                    Text("%02d".format(index + 1), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(summary.routine.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(
                            plural(summary.exerciseCount, "ejercicio", "ejercicios"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextInputDialog(
            title = "Nueva rutina",
            placeholder = "Ej. Pierna — día A",
            confirmLabel = "Crear",
            onDismiss = { showCreate = false },
            onConfirm = { name ->
                showCreate = false
                vm.create(name, onOpenRoutine)
            },
        )
    }
}
