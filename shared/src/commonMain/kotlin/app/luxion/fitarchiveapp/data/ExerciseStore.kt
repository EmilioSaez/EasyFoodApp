package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.model.ExerciseEntry
import app.luxion.fitarchiveapp.model.NewExercise

/**
 * Ejercicio registrado por día y su ajuste (la implementación real es [SqlDelightExerciseStore]).
 * Ya no se usa en la app (sin tarjeta Ejercicio ni suma al objetivo): los datos guardados se conservan en la BD.
 */
interface ExerciseStore {
    /** Actividades del día, en orden de registro. */
    suspend fun entriesForDate(date: String): List<ExerciseEntry>

    /** Registra la actividad y devuelve su id. */
    suspend fun add(exercise: NewExercise, nowMillis: Long): Long

    suspend fun delete(id: Long)

    /** Kcal de ejercicio por día entre [from] y [to] (inclusive); los días sin ejercicio no aparecen. */
    suspend fun dailyKcal(from: String, to: String): Map<String, Int>

    /** Porcentaje de las kcal de ejercicio que se suma al objetivo (0 = desactivado). */
    suspend fun percent(): Int

    /** Fija el porcentaje; lanza [IllegalArgumentException] fuera de 0 a 100. */
    suspend fun setPercent(percent: Int, nowMillis: Long)
}
