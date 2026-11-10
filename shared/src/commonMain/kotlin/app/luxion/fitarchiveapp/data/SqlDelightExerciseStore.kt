package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.ExerciseEntity
import app.luxion.fitarchiveapp.db.ExerciseQueries
import app.luxion.fitarchiveapp.model.ExerciseEntry
import app.luxion.fitarchiveapp.model.ExerciseRules
import app.luxion.fitarchiveapp.model.NewExercise
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [ExerciseStore] respaldado por las tablas `ExerciseEntity` y `ExerciseSettingsEntity` de SQLDelight. */
class SqlDelightExerciseStore(
    private val queries: ExerciseQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ExerciseStore {
    override suspend fun entriesForDate(date: String): List<ExerciseEntry> = withContext(dispatcher) {
        queries.selectByDate(date).executeAsList().map { it.toDomain() }
    }

    override suspend fun add(exercise: NewExercise, nowMillis: Long): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.insert(exercise.date, exercise.name.trim(), exercise.kcal.toLong(), exercise.minutes?.toLong(), nowMillis)
            queries.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(dispatcher) { queries.deleteById(id); Unit }

    override suspend fun dailyKcal(from: String, to: String): Map<String, Int> = withContext(dispatcher) {
        queries.selectDailyKcal(from, to).executeAsList().associate { it.date to (it.kcal ?: 0L).toInt() }
    }

    override suspend fun percent(): Int = withContext(dispatcher) {
        queries.selectPercent().executeAsOneOrNull()?.percent?.toInt() ?: ExerciseRules.DEFAULT_PERCENT
    }

    override suspend fun setPercent(percent: Int, nowMillis: Long): Unit = withContext(dispatcher) {
        require(ExerciseRules.isValidPercent(percent)) { "El porcentaje de ejercicio debe estar entre 0 y 100: $percent" }
        queries.upsertPercent(percent.toLong(), nowMillis)
        Unit
    }
}

internal fun ExerciseEntity.toDomain() = ExerciseEntry(id, date, name, kcal.toInt(), minutes?.toInt(), created_at)
