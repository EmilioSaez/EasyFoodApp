package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.DailyGoalQueries
import app.luxion.fitarchiveapp.model.DailyGoals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [GoalStore] respaldado por la tabla `DailyGoalEntity` de SQLDelight. */
class SqlDelightGoalStore(
    private val queries: DailyGoalQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GoalStore {
    override suspend fun get(): DailyGoals = withContext(dispatcher) {
        queries.select().executeAsOneOrNull()?.let {
            DailyGoals(
                kcal = it.kcal?.toInt(),
                proteinG = it.protein_g?.toInt(),
                carbsG = it.carbs_g?.toInt(),
                fatG = it.fat_g?.toInt(),
            )
        } ?: DailyGoals()
    }

    override suspend fun save(goals: DailyGoals) = withContext(dispatcher) {
        queries.upsert(
            goals.kcal?.toLong(), goals.proteinG?.toLong(), goals.carbsG?.toLong(), goals.fatG?.toLong(),
            0L, // columna `fixed` en desuso (v3→v4); se conserva para no migrar el esquema
        )
        Unit
    }

    override suspend fun clear() = withContext(dispatcher) { queries.clear(); Unit }
}
