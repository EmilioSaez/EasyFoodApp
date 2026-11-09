package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.WeightEntryEntity
import app.luxion.fitarchiveapp.db.WeightEntryQueries
import app.luxion.fitarchiveapp.model.NewWeightEntry
import app.luxion.fitarchiveapp.model.WeightEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [WeightStore] respaldado por la tabla `WeightEntryEntity` de SQLDelight. */
class SqlDelightWeightStore(
    private val queries: WeightEntryQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : WeightStore {
    override suspend fun all(): List<WeightEntry> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun save(entry: NewWeightEntry, nowMillis: Long) = withContext(dispatcher) {
        queries.transaction { write(entry, nowMillis) }
    }

    override suspend fun update(originalDate: String, entry: NewWeightEntry, nowMillis: Long): Boolean =
        withContext(dispatcher) {
            queries.transactionWithResult {
                val original = queries.selectByDate(originalDate).executeAsOneOrNull()
                if (original == null) {
                    false
                } else {
                    if (originalDate != entry.date) queries.deleteByDate(originalDate)
                    write(entry, nowMillis, createdAt = original.created_at)
                    true
                }
            }
        }

    override suspend fun delete(date: String): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val existed = queries.selectByDate(date).executeAsOneOrNull() != null
            queries.deleteByDate(date)
            existed
        }
    }

    private fun write(entry: NewWeightEntry, nowMillis: Long, createdAt: Long? = null) {
        val created = createdAt ?: queries.selectByDate(entry.date).executeAsOneOrNull()?.created_at ?: nowMillis
        queries.upsert(entry.date, entry.weightKg, created, nowMillis)
    }
}

internal fun WeightEntryEntity.toDomain() = WeightEntry(date, weight_kg, created_at, updated_at)
