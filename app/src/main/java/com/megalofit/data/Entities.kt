package com.megalofit.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: String,
    val equipment: String,
    val description: String = "",
    /** true = creado por el usuario (se puede borrar); false = viene del catálogo base. */
    val isCustom: Boolean = false,
)

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "routine_items",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routineId"), Index("exerciseId")],
)
data class RoutineItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val position: Int,
    val sets: Int = 3,
    val reps: Int = 10,
    /** Segundos de trabajo por serie. 0 = ejercicio por repeticiones (avanzas tú con "Hecho"). */
    val workSeconds: Int = 0,
    val restSeconds: Int = 60,
) {
    val isTimed: Boolean get() = workSeconds > 0

    fun prescription(): String =
        if (isTimed) "$sets × ${workSeconds}s" else "$sets × $reps reps"
}

data class RoutineItemWithExercise(
    @Embedded val item: RoutineItem,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: Exercise,
)

data class RoutineSummary(
    @Embedded val routine: Routine,
    val exerciseCount: Int,
)
