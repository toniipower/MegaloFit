package com.megalofit.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Collections

class FitRepository(db: AppDatabase, private val context: Context) {
    private val exercises = db.exerciseDao()
    private val routines = db.routineDao()

    val allExercises = exercises.observeAll()
    val routineSummaries = routines.observeSummaries()

    // --- Catálogo ---------------------------------------------------------------------------

    /** Carga assets/exercises.json. Los ya existentes se ignoran, los nuevos se añaden. */
    suspend fun syncSeedExercises() {
        val seed = withContext(Dispatchers.IO) {
            val json = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Exercise(
                    name = o.getString("name"),
                    muscleGroup = o.getString("muscle"),
                    equipment = o.optString("equipment", "Peso corporal"),
                    description = o.optString("description", ""),
                )
            }
        }
        exercises.insertAll(seed)
    }

    /** @return false si ya existe un ejercicio con ese nombre. */
    suspend fun addCustomExercise(name: String, muscle: String, equipment: String, description: String): Boolean =
        exercises.insert(
            Exercise(
                name = name.trim(),
                muscleGroup = muscle.trim(),
                equipment = equipment.trim().ifEmpty { "Peso corporal" },
                description = description.trim(),
                isCustom = true,
            )
        ) > 0

    suspend fun deleteExercise(exercise: Exercise) {
        if (exercise.isCustom) exercises.delete(exercise)
    }

    // --- Rutinas ----------------------------------------------------------------------------

    fun observeRoutine(id: Long) = routines.observeRoutine(id)
    fun observeItems(routineId: Long) = routines.observeItems(routineId)
    suspend fun getRoutine(id: Long) = routines.getRoutine(id)
    suspend fun getItems(routineId: Long) = routines.getItems(routineId)

    suspend fun createRoutine(name: String): Long = routines.insertRoutine(Routine(name = name.trim()))
    suspend fun updateRoutine(routine: Routine) = routines.updateRoutine(routine)
    suspend fun deleteRoutine(routine: Routine) = routines.deleteRoutine(routine)

    suspend fun addExercisesToRoutine(routineId: Long, exerciseIds: List<Long>) {
        var position = routines.nextPosition(routineId)
        routines.insertItems(exerciseIds.map { RoutineItem(routineId = routineId, exerciseId = it, position = position++) })
    }

    suspend fun updateItem(item: RoutineItem) = routines.updateItem(item)
    suspend fun deleteItem(item: RoutineItem) = routines.deleteItem(item)

    /** Mueve un ejercicio [delta] posiciones (-1 = antes, +1 = después). */
    suspend fun moveItem(routineId: Long, itemId: Long, delta: Int) {
        val list = routines.getItems(routineId).map { it.item }.toMutableList()
        val from = list.indexOfFirst { it.id == itemId }
        val to = from + delta
        if (from < 0 || to !in list.indices) return
        Collections.swap(list, from, to)
        routines.updateItems(list.mapIndexed { index, item -> item.copy(position = index) })
    }
}
