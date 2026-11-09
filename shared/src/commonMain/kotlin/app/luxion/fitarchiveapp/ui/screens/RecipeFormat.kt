package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.RecipeCalculator
import app.luxion.fitarchiveapp.model.MacroTotals
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.macroSummary

/** «3 ingredientes · 4 raciones». */
fun Recipe.listDetail(): String = listOfNotNull(
    if (ingredients.size == 1) "1 ingrediente" else "${ingredients.size} ingredientes",
    servings?.let { if (it == 1) "1 ración" else "$it raciones" },
).joinToString(" · ")

/** Energía que se enseña en la lista: por ración si la receta define raciones, si no el total. */
fun Recipe.kcalLabel(): String {
    val perServing = RecipeCalculator.perServing(ingredients, servings)
    val kcal = (perServing ?: RecipeCalculator.totals(ingredients)).kcal ?: return "Sin dato"
    return if (perServing != null) "${formatKcal(kcal)} kcal/ración" else "${formatKcal(kcal)} kcal"
}

/** Macros que se enseñan en la tarjeta: por ración si la receta define raciones, si no el total. */
fun Recipe.cardTotals(): MacroTotals =
    RecipeCalculator.perServing(ingredients, servings) ?: RecipeCalculator.totals(ingredients)

/** Aviso ámbar de la tarjeta si a algún ingrediente le faltan datos, o `null` si están todos. */
fun Recipe.missingDataNotice(): String? {
    val names = RecipeCalculator.incompleteIngredients(ingredients)
    if (names.isEmpty()) return null
    return if (names.size == 1) "A ${names[0]} le faltan datos nutricionales"
    else "Faltan datos nutricionales en ${names.size} ingredientes"
}

/** «386 kcal · P 17 · H 62 · G 8», o `null` si falta alguna cifra. */
fun MacroTotals.summaryOrNull(): String? {
    return macroSummary(kcal ?: return null, proteinG ?: return null, carbsG ?: return null, fatG ?: return null)
}
