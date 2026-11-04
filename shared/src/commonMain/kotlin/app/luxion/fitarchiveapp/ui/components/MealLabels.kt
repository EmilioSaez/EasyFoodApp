package app.luxion.fitarchiveapp.ui.components

import app.luxion.fitarchiveapp.model.MealType

val MealType.label: String
    get() = when (this) {
        MealType.BREAKFAST -> "Desayuno"
        MealType.LUNCH -> "Comida"
        MealType.DINNER -> "Cena"
        MealType.SNACK -> "Snacks"
    }
