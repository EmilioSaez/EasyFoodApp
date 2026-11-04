package com.easyfood.data

import com.easyfood.db.CustomFoodEntity
import com.easyfood.db.CustomFoodQueries
import com.easyfood.model.CustomFood
import com.easyfood.model.NewCustomFood
import com.easyfood.model.Nutrients
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [CustomFoodStore] respaldado por la tabla `CustomFoodEntity` de SQLDelight. */
class SqlDelightCustomFoodStore(
    private val queries: CustomFoodQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CustomFoodStore {
    override suspend fun all(): List<CustomFood> = withContext(dispatcher) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun find(id: Long): CustomFood? = withContext(dispatcher) {
        queries.selectById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun create(food: NewCustomFood, nowMillis: Long): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.insert(
                name = food.name.trim(),
                brand = food.brand?.trim()?.ifEmpty { null },
                kcal = food.nutrients.kcal!!,
                protein_g = food.nutrients.proteinG!!,
                carbs_g = food.nutrients.carbsG!!,
                fat_g = food.nutrients.fatG!!,
                sugars_g = food.nutrients.sugarsG,
                saturated_fat_g = food.nutrients.saturatedFatG,
                fiber_g = food.nutrients.fiberG,
                salt_g = food.nutrients.saltG,
                serving_size_g = food.servingSizeG,
                serving_label = food.servingLabel?.trim()?.ifEmpty { null },
                created_at = nowMillis,
                updated_at = nowMillis,
            )
            queries.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun update(id: Long, food: NewCustomFood, nowMillis: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) {
                queries.update(
                    name = food.name.trim(),
                    brand = food.brand?.trim()?.ifEmpty { null },
                    kcal = food.nutrients.kcal!!,
                    protein_g = food.nutrients.proteinG!!,
                    carbs_g = food.nutrients.carbsG!!,
                    fat_g = food.nutrients.fatG!!,
                    sugars_g = food.nutrients.sugarsG,
                    saturated_fat_g = food.nutrients.saturatedFatG,
                    fiber_g = food.nutrients.fiberG,
                    salt_g = food.nutrients.saltG,
                    serving_size_g = food.servingSizeG,
                    serving_label = food.servingLabel?.trim()?.ifEmpty { null },
                    updated_at = nowMillis,
                    id = id,
                )
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

internal fun CustomFoodEntity.toDomain() = CustomFood(
    id = id,
    name = name,
    brand = brand,
    nutrients = Nutrients(
        kcal = kcal,
        proteinG = protein_g,
        carbsG = carbs_g,
        fatG = fat_g,
        saturatedFatG = saturated_fat_g,
        sugarsG = sugars_g,
        fiberG = fiber_g,
        saltG = salt_g,
    ),
    servingSizeG = serving_size_g,
    servingLabel = serving_label,
    createdAtMillis = created_at,
    updatedAtMillis = updated_at,
)
