package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType

/** Comida que toca a [hour] (0 a 23): se usa como valor por defecto al añadir un alimento sin comida elegida. */
fun mealForHour(hour: Int): MealType = when (hour) {
    in 5..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..19 -> MealType.SNACK
    else -> MealType.DINNER
}
