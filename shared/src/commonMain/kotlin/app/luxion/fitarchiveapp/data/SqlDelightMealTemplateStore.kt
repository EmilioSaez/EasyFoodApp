package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.MealTemplateEntity
import app.luxion.fitarchiveapp.db.MealTemplateItemEntity
import app.luxion.fitarchiveapp.db.MealTemplateQueries
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.NewMealTemplate
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [MealTemplateStore] respaldado por las tablas `MealTemplateEntity` y `MealTemplateItemEntity` de SQLDelight. */
class SqlDelightMealTemplateStore(
    private val queries: MealTemplateQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MealTemplateStore {
    override suspend fun all(): List<MealTemplate> = withContext(dispatcher) {
        queries.transactionWithResult { queries.readAll() }
    }

    override suspend fun find(id: Long): MealTemplate? = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.selectById(id).executeAsOneOrNull()?.toDomain(queries.selectItems(id).executeAsList())
        }
    }

    override suspend fun create(template: NewMealTemplate, nowMillis: Long): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.insert(template.name.trim(), nowMillis, nowMillis)
            val id = queries.lastInsertedId().executeAsOne()
            queries.writeItems(id, template.items)
            id
        }
    }

    override suspend fun update(id: Long, template: NewMealTemplate, nowMillis: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) {
                queries.update(template.name.trim(), nowMillis, id)
                queries.deleteItems(id)
                queries.writeItems(id, template.items)
            }
            exists
        }
    }

    override suspend fun rename(id: Long, name: String, nowMillis: Long): Boolean = withContext(dispatcher) {
        require(name.isNotBlank()) { "El nombre no puede estar vacío" }
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) queries.update(name.trim(), nowMillis, id)
            exists
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) {
                queries.deleteItems(id)
                queries.deleteById(id)
            }
            exists
        }
    }
}

/** Todas las plantillas con sus alimentos (una consulta por tabla). */
internal fun MealTemplateQueries.readAll(): List<MealTemplate> {
    val byTemplate = selectAllItems().executeAsList().groupBy { it.template_id }
    return selectAll().executeAsList().map { it.toDomain(byTemplate[it.id].orEmpty()) }
}

internal fun MealTemplateQueries.writeItems(templateId: Long, items: List<TemplateItem>) {
    items.forEachIndexed { index, i ->
        insertItem(
            template_id = templateId, position = index.toLong(), barcode = i.barcode, name = i.name,
            quantity = i.quantity.amount, unit = i.quantity.unit.name, serving_size_g = i.servingSizeG,
            kcal_100g = i.nutrients.kcal, protein_100g = i.nutrients.proteinG, carbs_100g = i.nutrients.carbsG,
            fat_100g = i.nutrients.fatG,
        )
    }
}

internal fun MealTemplateEntity.toDomain(items: List<MealTemplateItemEntity>) = MealTemplate(
    id = id, name = name, items = items.map { it.toDomain() }, createdAtMillis = created_at, updatedAtMillis = updated_at,
)

internal fun MealTemplateItemEntity.toDomain() = TemplateItem(
    barcode = barcode,
    name = name,
    quantity = Quantity(quantity, QuantityUnit.valueOf(unit)),
    servingSizeG = serving_size_g,
    nutrients = Nutrients(kcal = kcal_100g, proteinG = protein_100g, carbsG = carbs_100g, fatG = fat_100g),
)
