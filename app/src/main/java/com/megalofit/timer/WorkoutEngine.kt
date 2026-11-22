package com.megalofit.timer

import android.os.SystemClock
import com.megalofit.data.RoutineItemWithExercise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PhaseType { WORK, REST }

data class WorkoutStep(
    val type: PhaseType,
    val exerciseName: String,
    val setNumber: Int,
    val totalSets: Int,
    val reps: Int,
    /** 0 = paso manual (por repeticiones): no hay cuenta atrás, avanzas tú. */
    val durationSec: Int,
    val nextLabel: String? = null,
) {
    val durationMs: Long get() = durationSec * 1000L
    val isTimed: Boolean get() = durationSec > 0
}

data class TimerState(
    val routineId: Long = -1,
    val routineName: String = "",
    val steps: List<WorkoutStep> = emptyList(),
    val index: Int = 0,
    val remainingMs: Long = 0,
    val elapsedMs: Long = 0,
    val running: Boolean = false,
    val active: Boolean = false,
    val finished: Boolean = false,
) {
    val current: WorkoutStep? get() = steps.getOrNull(index)
}

object WorkoutPlan {
    /** Estimación para pasos por repeticiones al calcular la duración de una rutina. */
    const val SECONDS_PER_REP = 3

    /** Convierte una rutina en la secuencia plana trabajo → descanso → trabajo… (sin descanso final). */
    fun build(items: List<RoutineItemWithExercise>): List<WorkoutStep> {
        val steps = mutableListOf<WorkoutStep>()
        items.forEachIndexed { i, row ->
            val item = row.item
            val sets = item.sets.coerceAtLeast(1)
            for (set in 1..sets) {
                steps += WorkoutStep(
                    type = PhaseType.WORK,
                    exerciseName = row.exercise.name,
                    setNumber = set,
                    totalSets = sets,
                    reps = item.reps,
                    durationSec = item.workSeconds.coerceAtLeast(0),
                )
                val isLastOfWorkout = i == items.lastIndex && set == sets
                if (!isLastOfWorkout && item.restSeconds > 0) {
                    val next = if (set < sets) {
                        "${row.exercise.name} · serie ${set + 1}/$sets"
                    } else {
                        val n = items[i + 1]
                        "${n.exercise.name} · serie 1/${n.item.sets.coerceAtLeast(1)}"
                    }
                    steps += WorkoutStep(
                        type = PhaseType.REST,
                        exerciseName = "Descanso",
                        setNumber = set,
                        totalSets = sets,
                        reps = 0,
                        durationSec = item.restSeconds,
                        nextLabel = "Siguiente: $next",
                    )
                }
            }
        }
        return steps
    }

    fun estimateSeconds(items: List<RoutineItemWithExercise>): Int =
        build(items).sumOf { if (it.isTimed) it.durationSec else it.reps * SECONDS_PER_REP }
}

/**
 * Motor del temporizador. No cuenta "ticks": guarda el instante (reloj monotónico
 * elapsedRealtime, que sigue avanzando con el móvil dormido) en el que acaba el paso actual
 * y calcula lo que queda cada vez que se consulta. Así, aunque el proceso se retrase o la CPU
 * duerma, el tiempo nunca se desfasa; si se han saltado pasos enteros, [tick] los recupera.
 */
class WorkoutEngine(private val clock: () -> Long = { SystemClock.elapsedRealtime() }) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private var stepEndAt = 0L
    private var runningSince = 0L
    private var activeAccum = 0L

    fun start(routineId: Long, routineName: String, steps: List<WorkoutStep>) {
        require(steps.isNotEmpty()) { "La rutina no tiene pasos" }
        val now = clock()
        activeAccum = 0L
        runningSince = now
        val first = steps.first().durationMs
        stepEndAt = now + first
        _state.value = TimerState(
            routineId = routineId,
            routineName = routineName,
            steps = steps,
            index = 0,
            remainingMs = first,
            running = true,
            active = true,
        )
    }

    /** Actualiza el estado. @return true si ha cambiado de paso automáticamente (para avisar). */
    fun tick(): Boolean {
        val s = _state.value
        if (!s.active || !s.running || s.finished) return false
        val now = clock()
        val elapsed = activeAccum + (now - runningSince)
        val step = s.current ?: return false

        if (!step.isTimed) {
            _state.value = s.copy(elapsedMs = elapsed)
            return false
        }

        val remaining = stepEndAt - now
        if (remaining > 0) {
            _state.value = s.copy(remainingMs = remaining, elapsedMs = elapsed)
            return false
        }

        // El paso ha terminado. Avanza, consumiendo el tiempo sobrante en pasos cronometrados.
        var overflow = -remaining
        var idx = s.index + 1
        while (idx < s.steps.size) {
            val d = s.steps[idx].durationMs
            if (d == 0L || overflow < d) break
            overflow -= d
            idx++
        }
        if (idx >= s.steps.size) {
            finish(now)
            return true
        }
        val d = s.steps[idx].durationMs
        val rem = if (d == 0L) 0L else d - overflow
        stepEndAt = now + rem
        _state.value = s.copy(index = idx, remainingMs = rem, elapsedMs = elapsed)
        return true
    }

    fun pause() {
        val s = _state.value
        if (!s.active || !s.running || s.finished) return
        val now = clock()
        activeAccum += now - runningSince
        val rem = if (s.current?.isTimed == true) (stepEndAt - now).coerceAtLeast(0) else 0L
        _state.value = s.copy(running = false, remainingMs = rem, elapsedMs = activeAccum)
    }

    fun resume() {
        val s = _state.value
        if (!s.active || s.running || s.finished) return
        val now = clock()
        runningSince = now
        stepEndAt = now + s.remainingMs
        _state.value = s.copy(running = true)
    }

    fun toggle() = if (_state.value.running) pause() else resume()

    fun next() = jumpTo(_state.value.index + 1)

    fun previous() = jumpTo((_state.value.index - 1).coerceAtLeast(0))

    fun stop() {
        _state.value = TimerState()
    }

    private fun jumpTo(i: Int) {
        val s = _state.value
        if (!s.active || s.finished) return
        if (i >= s.steps.size) {
            finish(clock())
            return
        }
        val d = s.steps[i].durationMs
        stepEndAt = clock() + d
        _state.value = s.copy(index = i, remainingMs = d)
    }

    private fun finish(now: Long) {
        val s = _state.value
        if (s.running) activeAccum += now - runningSince
        _state.value = s.copy(running = false, finished = true, remainingMs = 0, elapsedMs = activeAccum)
    }
}

/** Instancia única compartida entre el servicio y la interfaz. */
object WorkoutTimer {
    val engine = WorkoutEngine()
}
