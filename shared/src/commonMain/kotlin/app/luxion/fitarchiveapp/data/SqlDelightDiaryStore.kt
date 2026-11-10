package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.DiaryEntryEntity
import app.luxion.fitarchiveapp.db.DiaryEntryQueries
import app.luxion.fitarchiveapp.domain.DaySummary
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [DiaryStore] respaldado por la tabla `DiaryEntryEntity` de SQLDelight. */
class SqlDelightDiaryStore(
    private val queries: DiaryEntryQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DiaryStore {
    override suspend fun entriesForDate(date: String): List<DiaryEntry> = withContext(dispatcher) {
        queries.selectByDate(date).executeAsList().map { it.toDomain() }
    }

    override suspend fun daySummaries(from: String, to: String): List<DaySummary> = withContext(dispatcher) {
        queries.selectDailyKcal(from, to).executeAsList().map { DaySummary(it.date, it.kcal ?: 0.0) }
    }

    override suspend fun find(id: Long): DiaryEntry? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun recentFoods(limit: Int): List<DiaryEntry> = withContext(dispatcher) {
        queries.selectRecent(limit.toLong()).executeAsList().map { it.toDomain() }
    }

    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.insert(
                date = entry.date,
                meal = entry.meal.name,
                barcode = entry.barcode,
                name = entry.name,
                quantity = entry.quantity.amount,
                unit = entry.quantity.unit.name,
                serving_size_g = entry.servingSizeG,
                kcal_100g = entry.nutrients.kcal,
                protein_100g = entry.nutrients.proteinG,
                carbs_100g = entry.nutrients.carbsG,
                fat_100g = entry.nutrients.fatG,
                created_at = createdAtMillis,
                quick = if (entry.quick) 1L else 0L,
            )
            queries.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun update(id: Long, meal: MealType, quantity: Quantity): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) queries.updateQuantityAndMeal(meal.name, quantity.amount, quantity.unit.name, id)
            exists
        }
    }

    override suspend fun updateQuick(id: Long, meal: MealType, name: String, totals: Nutrients): Boolean =
        withContext(dispatcher) {
            queries.transactionWithResult {
                val exists = queries.selectById(id).executeAsOneOrNull()?.quick == 1L
                if (exists) {
                    queries.updateQuick(meal.name, name, totals.kcal, totals.proteinG, totals.carbsG, totals.fatG, id)
                }
                exists
            }
        }

    override suspend fun delete(id: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) queries.deleteById(id)
            exists
        }
    }
}

internal fun DiaryEntryEntity.toDomain() = DiaryEntry(
    id = id,
    date = date,
    meal = MealType.valueOf(meal),
    barcode = barcode,
    name = name,
    quantity = Quantity(quantity, QuantityUnit.valueOf(unit)),
    servingSizeG = serving_size_g,
    nutrients = Nutrients(
        kcal = kcal_100g,
        proteinG = protein_100g,
        carbsG = carbs_100g,
        fatG = fat_100g,
    ),
    createdAtMillis = created_at,
    quick = quick == 1L,
)
