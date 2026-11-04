package com.easyfood.ui.screens

import androidx.compose.runtime.Composable
import com.easyfood.model.MealType
import com.easyfood.ui.navigation.EasyFoodNavigationBar
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route
import com.easyfood.ui.navigation.TopLevelTab

/** Diario del día (docs/design.md §2.1). Placeholder: lo implementa otra sesión. */
@Composable
fun DiaryScreen(navigator: Navigator) {
    PlaceholderScreen(
        title = "Hoy",
        description = "Diario del día (pendiente)",
        actions = listOf(
            PlaceholderAction("Añadir alimento") { navigator.push(Route.Add(MealType.SNACK)) },
            PlaceholderAction("Editar entrada (ficha)") {
                navigator.push(Route.ProductDetail(barcode = null, meal = MealType.SNACK, entryId = 1L))
            },
        ),
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.Diary, navigator) },
    )
}
