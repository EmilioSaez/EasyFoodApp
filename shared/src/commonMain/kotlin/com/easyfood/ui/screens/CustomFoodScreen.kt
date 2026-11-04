package com.easyfood.ui.screens

import androidx.compose.runtime.Composable
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route

/** Alimento propio (docs/design.md §2.5). Placeholder: lo implementa otra sesión. */
@Composable
fun CustomFoodScreen(navigator: Navigator, route: Route.CustomFood) {
    PlaceholderScreen(
        title = "Alimento propio",
        description = route.barcode?.let { "Crear con el código $it (pendiente)" }
            ?: "Formulario de alimento propio (pendiente)",
        actions = listOf(PlaceholderAction("Guardar y volver al diario") { navigator.popToDiary() }),
        onBack = { navigator.pop() },
    )
}
