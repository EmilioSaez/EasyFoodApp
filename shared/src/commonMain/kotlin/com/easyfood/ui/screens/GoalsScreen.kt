package com.easyfood.ui.screens

import androidx.compose.runtime.Composable
import com.easyfood.ui.navigation.EasyFoodNavigationBar
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.TopLevelTab

/** Objetivos y TDEE (docs/design.md §2.6). Placeholder: lo implementa otra sesión. */
@Composable
fun GoalsScreen(navigator: Navigator) {
    PlaceholderScreen(
        title = "Objetivos",
        description = "Objetivos y TDEE (pendiente)",
        actions = emptyList(),
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.Goals, navigator) },
    )
}
