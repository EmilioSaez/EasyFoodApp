package app.luxion.fitarchiveapp.data

import app.luxion.fitarchiveapp.db.RecipeEntity
import app.luxion.fitarchiveapp.db.RecipeIngredientEntity
import app.luxion.fitarchiveapp.db.RecipeQueries
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.model.RecipeIngredient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [RecipeStore] respaldado por las tablas `RecipeEntity` y `RecipeIngredientEntity` de SQLDelight. */
class SqlDelightRecipeStore(
    private val queries: RecipeQueries,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RecipeStore {
    override suspend fun all(): List<Recipe> = withContext(dispatcher) {
        queries.transactionWithResult { queries.readAll() }
    }

    override suspend fun find(id: Long): Recipe? = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.selectById(id).executeAsOneOrNull()?.toDomain(queries.selectIngredients(id).executeAsList())
        }
    }

    override suspend fun create(recipe: NewRecipe, nowMillis: Long): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.insert(recipe.name.trim(), recipe.servings?.toLong(), nowMillis, nowMillis)
            val id = queries.lastInsertedId().executeAsOne()
            queries.writeIngredients(id, recipe.ingredients)
            id
        }
    }

    override suspend fun update(id: Long, recipe: NewRecipe, nowMillis: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) {
                queries.update(recipe.name.trim(), recipe.servings?.toLong(), nowMillis, id)
                queries.deleteIngredients(id)
                queries.writeIngredients(id, recipe.ingredients)
            }
            exists
        }
    }

    override suspend fun delete(id: Long): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            val exists = queries.selectById(id).executeAsOneOrNull() != null
            if (exists) {
                queries.deleteIngredients(id)
                queries.deleteById(id)
            }
            exists
        }
    }
}

/** Todas las recetas con sus ingredientes (una consulta por tabla). */
internal fun RecipeQueries.readAll(): List<Recipe> {
    val byRecipe = selectAllIngredients().executeAsList().groupBy { it.recipe_id }
    return selectAll().executeAsList().map { it.toDomain(byRecipe[it.id].orEmpty()) }
}

internal fun RecipeQueries.writeIngredients(recipeId: Long, ingredients: List<RecipeIngredient>) {
    ingredients.forEachIndexed { index, i ->
        insertIngredient(
            recipe_id = recipeId, position = index.toLong(), barcode = i.barcode, name = i.name, grams = i.grams,
            serving_size_g = i.servingSizeG, kcal_100g = i.nutrients.kcal, protein_100g = i.nutrients.proteinG,
            carbs_100g = i.nutrients.carbsG, fat_100g = i.nutrients.fatG,
        )
    }
}

internal fun RecipeEntity.toDomain(ingredients: List<RecipeIngredientEntity>) = Recipe(
    id = id,
    name = name,
    servings = servings?.toInt(),
    ingredients = ingredients.map { it.toDomain() },
    createdAtMillis = created_at,
    updatedAtMillis = updated_at,
)

internal fun RecipeIngredientEntity.toDomain() = RecipeIngredient(
    barcode = barcode,
    name = name,
    grams = grams,
    nutrients = Nutrients(kcal = kcal_100g, proteinG = protein_100g, carbsG = carbs_100g, fatG = fat_100g),
    servingSizeG = serving_size_g,
)
