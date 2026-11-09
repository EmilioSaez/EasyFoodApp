package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.WaterQueries
import app.luxion.fitarchiveapp.model.WaterRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [WaterStore] respaldado por las tablas `WaterEntity` y `WaterGoalEntity` de SQLDelight. */
class SqlDelightWaterStore(
    private val queries: WaterQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WaterStore {
    override suspend fun total(date: String): Int = withContext(dispatcher) {
        queries.selectByDate(date).executeAsOneOrNull()?.ml?.toInt() ?: 0
    }

    override suspend fun add(date: String, deltaMl: Int, nowMillis: Long): Int = withContext(dispatcher) {
        queries.transactionWithResult {
            val current = queries.selectByDate(date).executeAsOneOrNull()?.ml?.toInt() ?: 0
            val next = (current.toLong() + deltaMl).coerceIn(0L, WaterRules.MAX_DAY_ML.toLong()).toInt()
            if (next == 0) queries.deleteByDate(date) else queries.upsert(date, next.toLong(), nowMillis)
            next
        }
    }

    override suspend fun goalMl(): Int = withContext(dispatcher) {
        queries.selectGoal().executeAsOneOrNull()?.goal_ml?.toInt() ?: WaterRules.DEFAULT_GOAL_ML
    }

    override suspend fun setGoalMl(ml: Int, nowMillis: Long): Unit = withContext(dispatcher) {
        require(WaterRules.isValidGoal(ml)) {
            "El objetivo de agua debe estar entre ${WaterRules.MIN_GOAL_ML} y ${WaterRules.MAX_GOAL_ML} ml: $ml"
        }
        queries.upsertGoal(ml.toLong(), nowMillis)
        Unit
    }
}
