package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.model.MealType

/**
 * Comidas que el usuario puede elegir al añadir un alimento (docs/design.md §2.9): las del preset de
 * [mealsPerDay] y, para no perder la que ya tiene un dato, la comida actual aunque ya no esté en el preset.
 * [nowMinutes] da la hora actual en minutos desde medianoche.
 */
data class MealChoices(val mealsPerDay: Int?, val nowMinutes: () -> Int) {
    /** Comidas del preset, en orden de aparición en el día. */
    val active: List<MealType> get() = MealPlan.preset(mealsPerDay)

    /** Opciones del selector: las activas más [current] si no lo está. */
    fun options(current: MealType? = null): List<MealType> = MealPlan.visible(mealsPerDay, setOfNotNull(current))

    /** La comida activa más cercana a la hora actual. */
    fun defaultMeal(): MealType = MealPlan.nearest(active, nowMinutes())
}
