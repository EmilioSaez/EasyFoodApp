package app.luxion.fitarchiveapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.PlatformBackHandler
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.screens.AddFoodScreen
import app.luxion.fitarchiveapp.ui.screens.CustomFoodScreen
import app.luxion.fitarchiveapp.ui.screens.DiaryScreen
import app.luxion.fitarchiveapp.ui.screens.GoalsScreen
import app.luxion.fitarchiveapp.ui.screens.ProductDetailScreen
import app.luxion.fitarchiveapp.ui.screens.ScannerScreen
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme

/**
 * Raíz de la app: tema + navegación entre pantallas (docs/design.md §4).
 * [today] devuelve la fecha ISO (`YYYY-MM-DD`) del día actual.
 */
@Composable
fun App(
    repository: ProductRepository,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    searchStore: ProductSearchStore,
    customFoodStore: CustomFoodStore,
    today: () -> String,
) {
    EasyFoodTheme {
        Surface(Modifier.fillMaxSize()) {
            val navigator = remember { Navigator() }
            PlatformBackHandler(enabled = navigator.canGoBack) { navigator.pop() }

            // En escritorio, las mismas pantallas en una columna de 600 dp como máximo, centrada.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    when (val route = navigator.current) {
                        Route.Diary -> DiaryScreen(navigator, diaryStore, goalStore, today())
                        is Route.Add -> AddFoodScreen(
                            navigator,
                            route,
                            searchStore = searchStore,
                            customFoods = customFoodStore,
                        )
                        is Route.Scanner -> ScannerScreen(navigator, route, repository)
                        is Route.CustomFood -> CustomFoodScreen(navigator, route, customFoodStore)
                        is Route.ProductDetail -> ProductDetailScreen(navigator, route, repository, diaryStore, today)
                        Route.Goals -> GoalsScreen(navigator, goalStore)
                    }
                }
            }
        }
    }
}
