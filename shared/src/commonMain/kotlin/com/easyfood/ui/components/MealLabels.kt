package com.easyfood.ui.components

import com.easyfood.model.MealType

val MealType.label: String
    get() = when (this) {
        MealType.BREAKFAST -> "Desayuno"
        MealType.LUNCH -> "Comida"
        MealType.DINNER -> "Cena"
        MealType.SNACK -> "Snacks"
    }
