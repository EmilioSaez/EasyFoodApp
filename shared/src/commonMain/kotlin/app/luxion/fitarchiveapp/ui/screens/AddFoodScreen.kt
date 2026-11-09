package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.FoodResultRow
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException

/**
 * Pestaña Alimentos / añadir alimento (docs/design.md §2.2): el buscador compartido ([FoodSearch]) sin filtros más
 * accesos rápidos «Recientes · Escáner · Alimento propio». Sin texto se ven los recientes del diario. La comida solo
 * viene preseleccionada ([Route.Add.meal]) si se llega desde el «+» de una sección del Diario; si no, se elige en la ficha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navigator: Navigator,
    route: Route.Add,
    searchStore: ProductSearchStore? = null,
    customFoods: CustomFoodStore? = null,
    diaryStore: DiaryStore? = null,
) {
    var query by remember { mutableStateOf("") }
    // null = aún cargando; lista vacía = sin recientes.
    var recents by remember { mutableStateOf<List<FoodResult>?>(null) }

    LaunchedEffect(diaryStore, customFoods) {
        recents = try {
            val entries = diaryStore?.recentFoods(RECENT_FOODS_LIMIT).orEmpty()
            recentFoodResults(entries, customFoods?.all().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Alimentos") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        FoodSearch(
            query = query,
            onQueryChange = { query = it },
            searchStore = searchStore,
            customFoods = customFoods,
            onPick = { navigator.push(Route.ProductDetail(barcode = it.result.key, meal = route.meal)) },
            modifier = Modifier.padding(padding),
            emptyPrimary = EmptyStateAction("Crear alimento propio") { navigator.push(Route.CustomFood(meal = route.meal)) },
            emptySecondary = EmptyStateAction("Escanear código") { navigator.push(Route.Scanner(route.meal)) },
            quickActions = {
                QuickActions(
                    onRecent = { query = "" },
                    onScan = { navigator.push(Route.Scanner(route.meal)) },
                    onCustom = { navigator.push(Route.CustomFood(meal = route.meal)) },
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            },
            idleContent = {
                RecentFoodsList(recents) { navigator.push(Route.ProductDetail(barcode = it.key, meal = route.meal)) }
            },
        )
    }
}

@Composable
private fun RecentFoodsList(recents: List<FoodResult>?, onPick: (FoodResult) -> Unit) {
    when {
        recents == null -> Unit
        recents.isEmpty() -> EmptyState(
            icon = "◷",
            title = "Aún no hay recientes",
            message = "Los alimentos que apuntes en el diario aparecerán aquí. Busca uno o usa el escáner.",
            modifier = Modifier.fillMaxWidth(),
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text(
                    "Últimos alimentos · ${recents.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                )
            }
            items(recents, key = { it.key }) { r ->
                FoodResultRow(
                    name = r.name,
                    detail = r.detail,
                    source = r.source,
                    kcalPer100g = r.kcalPer100g,
                    onClick = { onPick(r) },
                )
            }
        }
    }
}

/**
 * Recientes · Escáner · Alimento propio. «Escáner» abre [ScannerScreen], que con cámara escanea y siempre ofrece la
 * entrada manual de EAN; en escritorio, sin cámara, solo queda esa entrada manual.
 */
@Composable
private fun QuickActions(onRecent: () -> Unit, onScan: () -> Unit, onCustom: () -> Unit, modifier: Modifier = Modifier) {
    val tall = Modifier.height(72.dp)
    val primary = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
    val neutral = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Button(onClick = onRecent, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = primary) {
            Text("Recientes")
        }
        Button(onClick = onScan, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = neutral) {
            Text("Escáner")
        }
        Button(onClick = onCustom, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = neutral) {
            Text("Alimento propio")
        }
    }
}
