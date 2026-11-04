package com.easyfood.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.easyfood.data.ProductRepository
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.PlatformBackHandler
import com.easyfood.ui.navigation.Route
import com.easyfood.ui.screens.AddFoodScreen
import com.easyfood.ui.screens.CustomFoodScreen
import com.easyfood.ui.screens.DiaryScreen
import com.easyfood.ui.screens.GoalsScreen
import com.easyfood.ui.screens.ProductDetailScreen
import com.easyfood.ui.screens.ScannerScreen
import com.easyfood.ui.theme.EasyFoodTheme

/** Raíz de la app: tema + navegación entre pantallas (docs/design.md §4). */
@Composable
fun App(repository: ProductRepository) {
    EasyFoodTheme {
        Surface(Modifier.fillMaxSize()) {
            val navigator = remember { Navigator() }
            PlatformBackHandler(enabled = navigator.canGoBack) { navigator.pop() }

            // En escritorio, las mismas pantallas en una columna de 600 dp como máximo, centrada.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    when (val route = navigator.current) {
                        Route.Diary -> DiaryScreen(navigator)
                        is Route.Add -> AddFoodScreen(navigator, route)
                        is Route.Scanner -> ScannerScreen(navigator, route, repository)
                        is Route.CustomFood -> CustomFoodScreen(navigator, route)
                        is Route.ProductDetail -> ProductDetailScreen(navigator, route)
                        Route.Goals -> GoalsScreen(navigator)
                    }
                }
            }
        }
    }
}
