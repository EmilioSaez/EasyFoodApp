package com.easyfood.ui.screens

import androidx.compose.runtime.Composable
import com.easyfood.ui.components.label
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route

/** Ficha de producto, en alta o en edición de una entrada (docs/design.md §2.4). Placeholder. */
@Composable
fun ProductDetailScreen(navigator: Navigator, route: Route.ProductDetail) {
    val editing = route.entryId != null
    PlaceholderScreen(
        title = "Producto",
        description = if (editing) {
            "Editar entrada #${route.entryId} (pendiente)"
        } else {
            "Ficha de ${route.barcode ?: "producto"} (pendiente)"
        },
        actions = listOf(
            PlaceholderAction(if (editing) "Guardar cambios" else "Añadir a ${route.meal.label}") {
                navigator.popToDiary()
            },
        ),
        onBack = { navigator.pop() },
    )
}
