package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.model.RecipeRules

/** Estado del formulario de receta: textos tal como los escribe el usuario más los ingredientes ya elegidos. */
data class RecipeFormState(
    val name: String = "",
    val servingsText: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
) {
    val nameError: String? get() = if (name.isBlank()) "Escribe un nombre" else null

    /** Las raciones son opcionales: vacío vale; si hay texto debe ser un entero de 1 a [RecipeRules.MAX_SERVINGS]. */
    val servingsError: String?
        get() = if (parseServings(servingsText) is ServingsParse.Invalid) {
            "Entre 1 y ${RecipeRules.MAX_SERVINGS} raciones"
        } else {
            null
        }

    val ingredientsError: String? get() = if (ingredients.isEmpty()) "Añade al menos un ingrediente" else null

    /** La receta lista para guardar, o `null` si algo no es válido. */
    fun toNewRecipe(): NewRecipe? {
        if (nameError != null || servingsError != null || ingredientsError != null) return null
        val servings = (parseServings(servingsText) as ServingsParse.Valid).servings
        return NewRecipe(name.trim(), servings, ingredients)
    }

    fun addIngredient(ingredient: RecipeIngredient) = copy(ingredients = ingredients + ingredient)

    fun removeIngredient(index: Int) = copy(ingredients = ingredients.filterIndexed { i, _ -> i != index })

    fun withGrams(index: Int, grams: Double) = copy(
        ingredients = ingredients.mapIndexed { i, ing -> if (i == index) ing.copy(grams = grams) else ing },
    )

    companion object {
        fun of(recipe: Recipe) = RecipeFormState(
            name = recipe.name,
            servingsText = recipe.servings?.toString().orEmpty(),
            ingredients = recipe.ingredients,
        )
    }
}

sealed interface ServingsParse {
    data class Valid(val servings: Int?) : ServingsParse
    data object Invalid : ServingsParse
}

fun parseServings(text: String): ServingsParse {
    val t = text.trim()
    if (t.isEmpty()) return ServingsParse.Valid(null)
    val n = t.toIntOrNull() ?: return ServingsParse.Invalid
    return if (n in 1..RecipeRules.MAX_SERVINGS) ServingsParse.Valid(n) else ServingsParse.Invalid
}

/** Gramos propuestos al elegir un ingrediente: una porción si el alimento la define, si no 100 g. */
fun defaultIngredientGrams(pick: FoodPick): Double = pick.servingSizeG ?: 100.0

/** El ingrediente de [pick] con [grams] gramos. */
fun ingredientOf(pick: FoodPick, grams: Double) = RecipeIngredient(
    barcode = pick.barcode,
    name = pick.result.name,
    grams = grams,
    nutrients = pick.nutrients,
    servingSizeG = pick.servingSizeG,
)
