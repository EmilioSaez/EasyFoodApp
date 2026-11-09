package app.luxion.fitarchiveapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.data.generic.GenericFoodSeeder
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ImportTarget
import app.luxion.fitarchiveapp.ui.components.SeedProgressBanner
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.PlatformBackHandler
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.screens.AddFoodScreen
import app.luxion.fitarchiveapp.ui.screens.CustomFoodScreen
import app.luxion.fitarchiveapp.ui.screens.DataPresenter
import app.luxion.fitarchiveapp.ui.screens.DataScreen
import app.luxion.fitarchiveapp.ui.screens.DiaryScreen
import app.luxion.fitarchiveapp.ui.screens.GoalsScreen
import app.luxion.fitarchiveapp.ui.screens.ProductDetailScreen
import app.luxion.fitarchiveapp.ui.screens.RecipeDetailScreen
import app.luxion.fitarchiveapp.ui.screens.RecipeEditScreen
import app.luxion.fitarchiveapp.ui.screens.RecipesScreen
import app.luxion.fitarchiveapp.ui.screens.ScannerScreen
import app.luxion.fitarchiveapp.ui.screens.WeightScreen
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme

/**
 * Raíz de la app: tema + navegación entre pantallas (docs/design.md §4).
 * [today] devuelve la fecha ISO (`YYYY-MM-DD`) del día actual.
 * [genericSeeder], si se indica, carga la base genérica en segundo plano al arrancar y muestra su progreso.
 */
@Composable
fun App(
    repository: ProductRepository,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    searchStore: ProductSearchStore,
    customFoodStore: CustomFoodStore,
    weightStore: WeightStore,
    recipeStore: RecipeStore,
    importTarget: ImportTarget,
    today: () -> String,
    genericSeeder: GenericFoodSeeder? = null,
) {
    EasyFoodTheme {
        Surface(Modifier.fillMaxSize()) {
            val navigator = remember { Navigator() }
            val dataPresenter = remember(importTarget) {
                DataPresenter(
                    exporter = DataExporter(importTarget) { kotlin.time.Clock.System.now().toEpochMilliseconds() },
                    importer = DataImporter(importTarget),
                )
            }
            PlatformBackHandler(enabled = navigator.canGoBack) { navigator.pop() }

            // En escritorio, las mismas pantallas en una columna de 600 dp como máximo, centrada.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                    if (genericSeeder != null) GenericSeedProgress(genericSeeder)
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (val route = navigator.current) {
                            Route.Diary -> DiaryScreen(navigator, diaryStore, goalStore, today())
                            is Route.Add -> AddFoodScreen(
                                navigator,
                                route,
                                searchStore = searchStore,
                                customFoods = customFoodStore,
                            )
                            is Route.Scanner -> ScannerScreen(navigator, route, repository)
                            is Route.CustomFood -> CustomFoodScreen(navigator, route, customFoodStore, route.foodId)
                            is Route.ProductDetail -> ProductDetailScreen(navigator, route, repository, diaryStore, customFoodStore, today)
                            Route.Goals -> GoalsScreen(navigator, goalStore)
                            Route.Weight -> WeightScreen(navigator, weightStore, today)
                            Route.Recipes -> RecipesScreen(navigator, recipeStore)
                            is Route.RecipeDetail -> RecipeDetailScreen(navigator, route, recipeStore, diaryStore, today)
                            is Route.RecipeEdit -> RecipeEditScreen(navigator, route, recipeStore, searchStore, customFoodStore)
                            Route.Data -> DataScreen(navigator, dataPresenter, today)
                        }
                    }
                }
            }
        }
    }
}

/** Lanza la carga inicial de la base genérica (fuera del hilo de UI) y pinta su avance. */
@Composable
private fun GenericSeedProgress(seeder: GenericFoodSeeder) {
    LaunchedEffect(seeder) { seeder.ensureSeeded() }
    val state by seeder.state.collectAsState()
    SeedProgressBanner(state)
}
