package app.luxion.easyfoodapp.ui.components

import app.luxion.easyfoodapp.model.MealType

val MealType.label: String
    get() = when (this) {
        MealType.BREAKFAST -> "Desayuno"
        MealType.MID_MORNING -> "Almuerzo"
        MealType.LUNCH -> "Comida"
        MealType.SNACK -> "Merienda"
        MealType.DINNER -> "Cena"
        MealType.LATE_SNACK -> "Recena"
    }
