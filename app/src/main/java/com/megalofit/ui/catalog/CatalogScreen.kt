package com.megalofit.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.megalofit.data.Exercise
import com.megalofit.data.FitRepository
import com.megalofit.ui.components.GridTile
import com.megalofit.ui.components.ScreenHeader
import com.megalofit.ui.components.rememberRepository
import com.megalofit.util.foldForSearch
import com.megalofit.util.plural
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CatalogViewModel(private val repo: FitRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val group = MutableStateFlow<String?>(null)

    private val all = repo.allExercises.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups = all.map { list -> list.map { it.muscleGroup }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filtered = combine(all, query, group) { list, q, g ->
        val needle = q.trim().foldForSearch()
        list.filter { e ->
            (g == null || e.muscleGroup == g) &&
                (needle.isEmpty() || e.name.foldForSearch().contains(needle) || e.equipment.foldForSearch().contains(needle))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addCustom(name: String, muscle: String, equipment: String, description: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(repo.addCustomExercise(name, muscle, equipment, description)) }
    }

    fun delete(exercise: Exercise) {
        viewModelScope.launch { repo.deleteExercise(exercise) }
    }
}

/**
 * Catálogo de ejercicios. Con [pickMode] = true sirve como selector múltiple para añadir
 * ejercicios a una rutina (el orden de selección es el orden en que se añaden).
 */
@Composable
fun CatalogScreen(
    pickMode: Boolean = false,
    onBack: (() -> Unit)? = null,
    onPicked: (List<Long>) -> Unit = {},
) {
    val repo = rememberRepository()
    val vm = viewModel { CatalogViewModel(repo) }
    val exercises by vm.filtered.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val group by vm.group.collectAsStateWithLifecycle()

    val selected = remember { mutableStateListOf<Long>() }
    var detail by remember { mutableStateOf<Exercise?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = if (pickMode) "Añadir ejercicios" else "Catálogo",
                subtitle = plural(exercises.size, "ejercicio", "ejercicios"),
                onBack = onBack,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Buscar ejercicio o material") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { FilterChip(selected = group == null, onClick = { vm.group.value = null }, label = { Text("Todos") }) }
                items(groups) { g ->
                    FilterChip(
                        selected = group == g,
                        onClick = { vm.group.value = if (group == g) null else g },
                        label = { Text(g) },
                    )
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(exercises, key = { it.id }) { e ->
                    val isSelected = e.id in selected
                    GridTile(
                        selected = isSelected,
                        onClick = {
                            if (pickMode) {
                                if (isSelected) selected.remove(e.id) else selected.add(e.id)
                            } else {
                                detail = e
                            }
                        },
                    ) {
                        val muted = if (isSelected) LocalContentColor.current.copy(alpha = 0.7f) else scheme.onSurfaceVariant
                        Text(
                            if (isSelected) "#${selected.indexOf(e.id) + 1}" else e.muscleGroup.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) LocalContentColor.current else scheme.primary,
                        )
                        Column {
                            Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                e.equipment + if (e.isCustom) " · propio" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (pickMode) {
            Button(
                onClick = { onPicked(selected.toList()) },
                enabled = selected.isNotEmpty(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                Text(if (selected.isEmpty()) "Toca para seleccionar" else "AÑADIR (${selected.size})", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = scheme.primary,
                contentColor = scheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "Nuevo ejercicio") }
        }
    }

    detail?.let { e ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = scheme.surfaceContainerHigh,
            title = { Text(e.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${e.muscleGroup} · ${e.equipment}", color = scheme.primary, style = MaterialTheme.typography.labelLarge)
                    if (e.description.isNotBlank()) Text(e.description)
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Cerrar") } },
            dismissButton = {
                if (e.isCustom) {
                    TextButton(
                        onClick = { vm.delete(e); detail = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    ) { Text("Eliminar") }
                }
            },
        )
    }

    if (showAdd) {
        AddExerciseDialog(
            groups = groups,
            onDismiss = { showAdd = false },
            onConfirm = { name, muscle, equipment, description, setError ->
                vm.addCustom(name, muscle, equipment, description) { ok ->
                    if (ok) showAdd = false else setError("Ya existe un ejercicio con ese nombre")
                }
            },
        )
    }
}

@Composable
private fun AddExerciseDialog(
    groups: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, muscle: String, equipment: String, description: String, setError: (String) -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("") }
    var equipment by rememberSaveable { mutableStateOf("Peso corporal") }
    var description by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Nuevo ejercicio") },
        text = {
            val currentError = error
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nombre") },
                    singleLine = true,
                    isError = currentError != null,
                    supportingText = if (currentError != null) {
                        { Text(currentError) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = muscle,
                    onValueChange = { muscle = it },
                    label = { Text("Grupo muscular") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(groups) { g -> SuggestionChip(onClick = { muscle = g }, label = { Text(g) }) }
                }
                OutlinedTextField(
                    value = equipment,
                    onValueChange = { equipment = it },
                    label = { Text("Material") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Notas (opcional)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, muscle, equipment, description) { error = it } },
                enabled = name.isNotBlank() && muscle.isNotBlank(),
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
