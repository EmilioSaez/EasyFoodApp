package com.easyfood.ui.screens

import androidx.compose.runtime.Composable
import com.easyfood.ui.components.label
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route

/** Añadir alimento (docs/design.md §2.2). Placeholder: lo implementa otra sesión. */
@Composable
fun AddFoodScreen(navigator: Navigator, route: Route.Add) {
    PlaceholderScreen(
        title = "Añadir a ${route.meal.label}",
        description = "Búsqueda de alimentos (pendiente)",
        actions = listOf(
            PlaceholderAction("Escanear / Código EAN") { navigator.push(Route.Scanner(route.meal)) },
            PlaceholderAction("Alimento propio") { navigator.push(Route.CustomFood(meal = route.meal)) },
            PlaceholderAction("Abrir ficha de ejemplo") {
                navigator.push(Route.ProductDetail(barcode = "8410000000000", meal = route.meal))
            },
        ),
        onBack = { navigator.pop() },
    )
}
