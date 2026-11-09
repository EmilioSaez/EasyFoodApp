package app.luxion.fitarchiveapp.model

/**
 * Ingrediente de una receta: un alimento de la BD (Open Food Facts, genérico o propio) con su cantidad en
 * gramos (o ml). Copia el nombre y los valores por 100 g, igual que una entrada del diario, para que la receta
 * no cambie si la caché de productos se purga o se edita el alimento propio.
 */
data class RecipeIngredient(
    /** Clave del producto (EAN o `generic:<id>`); `null` en un alimento propio. */
    val barcode: String?,
    val name: String,
    val grams: Double,
    /** Valores por 100 g; solo se usan kcal, proteínas, hidratos y grasa. */
    val nutrients: Nutrients,
    val servingSizeG: Double? = null,
) {
    init {
        require(name.isNotBlank()) { "El ingrediente necesita un nombre" }
        require(grams > 0 && grams.isFinite()) { "La cantidad de «$name» debe ser mayor que 0" }
    }

    val totals: MacroTotals
        get() = MacroTotals(
            kcal = scaled(nutrients.kcal),
            proteinG = scaled(nutrients.proteinG),
            carbsG = scaled(nutrients.carbsG),
            fatG = scaled(nutrients.fatG),
        )

    private fun scaled(per100g: Double?): Double? = per100g?.let { it * grams / 100.0 }
}

/** Datos para crear o editar una receta; valida al construirse. [servings] son las raciones que salen (opcional). */
data class NewRecipe(
    val name: String,
    val servings: Int? = null,
    val ingredients: List<RecipeIngredient>,
) {
    init {
        require(name.isNotBlank()) { "El nombre no puede estar vacío" }
        require(servings == null || servings in 1..RecipeRules.MAX_SERVINGS) {
            "Las raciones deben estar entre 1 y ${RecipeRules.MAX_SERVINGS}"
        }
        require(ingredients.isNotEmpty()) { "La receta necesita al menos un ingrediente" }
    }
}

/** Receta ya guardada. */
data class Recipe(
    val id: Long,
    val name: String,
    val servings: Int?,
    val ingredients: List<RecipeIngredient>,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

object RecipeRules {
    const val MAX_SERVINGS = 100
}
