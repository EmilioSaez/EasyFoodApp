package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MacroTotals
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.RecipeIngredient

/** Lógica pura de recetas: macros totales y por ración, y su paso al diario. */
object RecipeCalculator {
    /**
     * Suma de los ingredientes. Un macro es `null` si falta en algún ingrediente: sumar solo los que
     * lo tienen daría un total engañosamente bajo (mismo criterio que [MacroTotals] en el diario).
     */
    fun totals(ingredients: List<RecipeIngredient>): MacroTotals {
        fun sum(pick: (MacroTotals) -> Double?): Double? {
            if (ingredients.isEmpty()) return null
            var total = 0.0
            for (i in ingredients) total += pick(i.totals) ?: return null
            return total
        }
        return MacroTotals(sum { it.kcal }, sum { it.proteinG }, sum { it.carbsG }, sum { it.fatG })
    }

    /** Macros de una ración; `null` si la receta no define raciones. */
    fun perServing(ingredients: List<RecipeIngredient>, servings: Int?): MacroTotals? {
        if (servings == null || servings <= 0) return null
        val t = totals(ingredients)
        fun d(v: Double?) = v?.let { it / servings }
        return MacroTotals(d(t.kcal), d(t.proteinG), d(t.carbsG), d(t.fatG))
    }

    /** Peso total de los ingredientes en gramos. */
    fun totalGrams(ingredients: List<RecipeIngredient>): Double = ingredients.sumOf { it.grams }

    /**
     * Fracción de la receta que se come: [portions] raciones de [servings]; la receta entera (1,0) si no
     * define raciones. `null` si [portions] no es positivo.
     */
    fun factor(servings: Int?, portions: Double): Double? {
        if (!(portions > 0 && portions.isFinite())) return null
        return if (servings == null) 1.0 else portions / servings
    }

    /**
     * Entradas del diario al comer la receta: una por ingrediente, con sus gramos multiplicados por [factor]
     * (los valores por 100 g no cambian, así que la suma de las entradas coincide con los macros de la receta).
     */
    fun diaryEntries(
        ingredients: List<RecipeIngredient>,
        factor: Double,
        date: String,
        meal: MealType,
    ): List<NewDiaryEntry> {
        require(factor > 0 && factor.isFinite()) { "La fracción debe ser positiva" }
        return ingredients.map {
            NewDiaryEntry(
                date = date,
                meal = meal,
                barcode = it.barcode,
                name = it.name,
                quantity = Quantity(it.grams * factor, QuantityUnit.GRAMS),
                servingSizeG = it.servingSizeG,
                nutrients = it.nutrients,
            )
        }
    }
}
