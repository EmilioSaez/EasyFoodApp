package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.ai.LabelReading
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.dataWarnings
import app.luxion.fitarchiveapp.ui.components.parseNumber

/** Formulario de alimento propio relleno con lo que la IA leyó de la etiqueta (lo no leído queda vacío). */
fun LabelReading.toFormState(): CustomFoodFormState =
    customFoodFormStateOf(name.orEmpty(), servingLabel, servingSizeG, nutrients)

/**
 * Motivos de duda sobre los valores del formulario (kcal frente a macros, sumas por encima de 100 g, saturadas o
 * azúcares mayores que su total...), con la comprobación de datos dudosos que ya usa la ficha de producto.
 * Solo avisa de lo que ya está escrito: los campos vacíos los señala el propio formulario.
 */
fun CustomFoodFormState.doubtfulReasons(): List<String> {
    val nutrients = Nutrients(
        kcal = parseNumber(kcal), proteinG = parseNumber(protein), carbsG = parseNumber(carbs), fatG = parseNumber(fat),
        saturatedFatG = parseNumber(saturated), sugarsG = parseNumber(sugars),
        fiberG = parseNumber(fiber), saltG = parseNumber(salt),
    )
    return Product(barcode = "", name = name.ifBlank { "x" }, brand = null, imageUrl = null, nutrients = nutrients)
        .dataWarnings().filterNot { it is app.luxion.fitarchiveapp.model.DataWarning.MissingField }.reasons()
}

/** Ayuda bajo un campo vacío que la IA no pudo leer; `null` si no aplica. */
fun unreadHint(unread: Set<String>?, field: String, value: String): String? =
    if (unread != null && field in unread && value.isBlank()) "No se leyó en la etiqueta" else null
