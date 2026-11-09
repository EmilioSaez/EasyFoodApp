package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.ui.components.DataSource
import app.luxion.fitarchiveapp.ui.components.formatGrams

/** Cuántos alimentos recientes se piden al diario. */
const val RECENT_FOODS_LIMIT = 30

/**
 * «Recientes»: los últimos alimentos distintos del diario (de más reciente a más antiguo) como filas de resultado.
 * [entries] llega ya ordenado. Un alimento con EAN o genérico se abre por su clave; uno propio no guarda su id en
 * el diario, así que se enlaza por nombre con [customFoods]; si ya no existe se omite (no hay ficha a la que ir).
 */
fun recentFoodResults(entries: List<DiaryEntry>, customFoods: List<CustomFood>): List<FoodResult> {
    val seen = HashSet<String>()
    return entries.mapNotNull { e ->
        val key = e.barcode ?: customFoods.firstOrNull { it.name.equals(e.name, ignoreCase = true) }
            ?.let { CUSTOM_FOOD_KEY_PREFIX + it.id }
            ?: return@mapNotNull null
        if (!seen.add(key)) return@mapNotNull null
        val source = dataSourceOf(key)
        FoodResult(
            key = key,
            name = e.name,
            detail = listOfNotNull(
                e.servingSizeG?.let { "${formatGrams(it)} g" },
                e.barcode?.takeUnless { source == DataSource.Generic },
            ).joinToString(" · ").ifEmpty { source.label },
            source = source,
            kcalPer100g = e.nutrients.kcal,
        )
    }
}
