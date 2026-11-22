package com.megalofit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY muscleGroup, name")
    fun observeAll(): Flow<List<Exercise>>

    /** Ignora duplicados por nombre: así el catálogo base puede crecer en futuras versiones. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<Exercise>)

    /** Devuelve -1 si ya existe un ejercicio con ese nombre. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(exercise: Exercise): Long

    @Delete
    suspend fun delete(exercise: Exercise)
}

@Dao
interface RoutineDao {
    @Query(
        """
        SELECT r.*, (SELECT COUNT(*) FROM routine_items i WHERE i.routineId = r.id) AS exerciseCount
        FROM routines r ORDER BY r.createdAt DESC
        """
    )
    fun observeSummaries(): Flow<List<RoutineSummary>>

    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeRoutine(id: Long): Flow<Routine?>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getRoutine(id: Long): Routine?

    @Transaction
    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY position")
    fun observeItems(routineId: Long): Flow<List<RoutineItemWithExercise>>

    @Transaction
    @Query("SELECT * FROM routine_items WHERE routineId = :routineId ORDER BY position")
    suspend fun getItems(routineId: Long): List<RoutineItemWithExercise>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM routine_items WHERE routineId = :routineId")
    suspend fun nextPosition(routineId: Long): Int

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Insert
    suspend fun insertItems(items: List<RoutineItem>)

    @Update
    suspend fun updateItem(item: RoutineItem)

    @Update
    suspend fun updateItems(items: List<RoutineItem>)

    @Delete
    suspend fun deleteItem(item: RoutineItem)
}
