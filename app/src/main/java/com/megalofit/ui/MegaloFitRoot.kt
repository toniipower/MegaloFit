package com.megalofit.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.megalofit.MegaloFitApp
import com.megalofit.timer.PhaseType
import com.megalofit.timer.TimerState
import com.megalofit.timer.WorkoutTimer
import com.megalofit.ui.catalog.CatalogScreen
import com.megalofit.ui.components.rememberRepository
import com.megalofit.ui.routines.RoutineEditorScreen
import com.megalofit.ui.routines.RoutinesScreen
import com.megalofit.ui.theme.Ink
import com.megalofit.ui.workout.WorkoutScreen
import com.megalofit.util.ceilSeconds
import com.megalofit.util.formatClock
import kotlinx.coroutines.launch

private object Routes {
    const val ROUTINES = "routines"
    const val CATALOG = "catalog"
    const val ROUTINE = "routine/{id}"
    const val PICK = "pick/{id}"
    const val WORKOUT = "workout"
    fun routine(id: Long) = "routine/$id"
    fun pick(id: Long) = "pick/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.ROUTINES, "Rutinas", Icons.Default.GridView),
    Tab(Routes.CATALOG, "Catálogo", Icons.Default.FitnessCenter),
)

@Composable
fun MegaloFitRoot(openWorkoutSignal: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val timer by WorkoutTimer.engine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val repo = rememberRepository()

    // Al tocar la notificación del temporizador se abre directamente la pantalla de entrenamiento.
    LaunchedEffect(openWorkoutSignal) {
        if (openWorkoutSignal > 0 && WorkoutTimer.engine.state.value.active) {
            nav.navigate(Routes.WORKOUT) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (timer.active && route != Routes.WORKOUT) {
                ActiveWorkoutBanner(timer) { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } }
            }
        },
        bottomBar = {
            if (route == null || tabs.any { it.route == route }) BottomBar(route, nav)
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.ROUTINES, modifier = Modifier.padding(padding)) {
            composable(Routes.ROUTINES) {
                RoutinesScreen(onOpenRoutine = { nav.navigate(Routes.routine(it)) })
            }
            composable(Routes.CATALOG) {
                CatalogScreen()
            }
            composable(Routes.ROUTINE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: return@composable
                RoutineEditorScreen(
                    routineId = id,
                    onBack = { nav.popBackStack() },
                    onAddExercises = { nav.navigate(Routes.pick(id)) },
                    onOpenWorkout = { nav.navigate(Routes.WORKOUT) { launchSingleTop = true } },
                )
            }
            composable(Routes.PICK, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments?.getLong("id") ?: return@composable
                CatalogScreen(
                    pickMode = true,
                    onBack = { nav.popBackStack() },
                    onPicked = { ids ->
                        (context.applicationContext as MegaloFitApp).appScope.launch { repo.addExercisesToRoutine(id, ids) }
                        nav.popBackStack()
                    },
                )
            }
            composable(Routes.WORKOUT) {
                WorkoutScreen(onClose = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(route: String?, nav: NavHostController) {
    val scheme = MaterialTheme.colorScheme
    NavigationBar(containerColor = Ink, tonalElevation = 0.dp) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = scheme.onPrimary,
                    selectedTextColor = scheme.primary,
                    indicatorColor = scheme.primary,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/** Franja lima arriba cuando hay un entrenamiento en marcha y estás en otra pantalla. */
@Composable
private fun ActiveWorkoutBanner(s: TimerState, onClick: () -> Unit) {
    val step = s.current
    val label = when {
        s.finished -> "Rutina completada · toca para ver"
        step == null -> "Preparando…"
        else -> {
            val name = if (step.type == PhaseType.REST) "Descanso" else step.exerciseName
            val value = when {
                !s.running -> "en pausa"
                step.isTimed -> formatClock(ceilSeconds(s.remainingMs))
                else -> "${step.reps} reps"
            }
            "$name · $value"
        }
    }
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text("EN CURSO  ", style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
