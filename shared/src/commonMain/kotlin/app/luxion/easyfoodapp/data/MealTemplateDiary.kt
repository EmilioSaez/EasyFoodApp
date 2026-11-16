package app.luxion.easyfoodapp.data

import app.luxion.easyfoodapp.model.MealTemplate
import app.luxion.easyfoodapp.model.MealType

/**
 * Añade al diario, en [date] y [meal], una entrada por cada alimento de [template] (con su cantidad y datos nutricionales).
 * Devuelve cuántas entradas ha creado. Instantes crecientes: conservan el orden de la plantilla dentro de la comida.
 */
suspend fun DiaryStore.addTemplate(template: MealTemplate, date: String, meal: MealType, nowMillis: Long): Int {
    val entries = template.diaryEntries(date, meal)
    entries.forEachIndexed { i, e -> add(e, nowMillis + i) }
    return entries.size
}
