package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.scanner.isBarcodeScannerSupported
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.FoodResultRow
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 300L

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val results: List<FoodResult>) : SearchState
    data object Failed : SearchState
}

/**
 * Añadir alimento (docs/design.md §2.2): búsqueda por texto (FTS local + alimentos propios),
 * accesos rápidos y filtro por origen. Open Food Facts solo aparece si el producto ya está en la
 * caché local: la búsqueda remota por texto aún no existe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFoodScreen(
    navigator: Navigator,
    route: Route.Add,
    searchStore: ProductSearchStore? = null,
    customFoods: CustomFoodStore? = null,
) {
    var meal by remember { mutableStateOf(route.meal) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(SourceFilter.All) }
    var retry by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }

    LaunchedEffect(query, retry) {
        if (query.isBlank()) {
            state = SearchState.Idle
            return@LaunchedEffect
        }
        state = SearchState.Loading
        delay(SEARCH_DEBOUNCE_MS)
        state = try {
            val custom = customFoods?.all().orEmpty().filter { it.matches(query) }
            val products = searchStore?.search(query).orEmpty()
            SearchState.Done(mergeResults(custom, products))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Failed
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Añadir a") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = { MealPicker(meal) { meal = it } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, Modifier.padding(horizontal = Spacing.l))
            Box(Modifier.fillMaxWidth().height(4.dp)) {
                if (state is SearchState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            QuickActions(
                onScan = { navigator.push(Route.Scanner(meal)) },
                onCustom = { navigator.push(Route.CustomFood(meal = meal)) },
                modifier = Modifier.padding(horizontal = Spacing.l),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                items(SourceFilter.entries) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
            when (val s = state) {
                SearchState.Loading -> Unit
                SearchState.Idle -> EmptyState(
                    icon = "⌕",
                    title = "Busca un alimento",
                    message = "Escribe el nombre o la marca, o usa el código de barras.",
                    modifier = Modifier.fillMaxWidth(),
                )
                SearchState.Failed -> EmptyState(
                    icon = "!",
                    title = "No se pudo buscar",
                    message = "Ha fallado la búsqueda en los datos guardados.",
                    tone = EmptyStateTone.Error,
                    primaryAction = EmptyStateAction("Reintentar") { retry++ },
                    modifier = Modifier.fillMaxWidth(),
                )
                is SearchState.Done -> {
                    val shown = remember(s, filter) { s.results.filteredBy(filter) }
                    if (shown.isEmpty()) {
                        EmptyState(
                            icon = "⌕",
                            title = "Sin resultados para «${query.trim()}»",
                            message = if (filter == SourceFilter.All) {
                                "Prueba con otra palabra o añade el alimento tú mismo."
                            } else {
                                "No hay resultados de este origen. Prueba con «Todos»."
                            },
                            primaryAction = EmptyStateAction("Crear alimento propio") {
                                navigator.push(Route.CustomFood(meal = meal))
                            },
                            secondaryAction = EmptyStateAction("Escanear código") {
                                navigator.push(Route.Scanner(meal))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    "Resultados · ${shown.size}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s),
                                )
                            }
                            items(shown, key = { it.key }) { r ->
                                FoodResultRow(
                                    name = r.name,
                                    detail = r.detail,
                                    source = r.source,
                                    kcalPer100g = r.kcalPer100g,
                                    onClick = { navigator.push(Route.ProductDetail(barcode = r.key, meal = meal)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealPicker(meal: MealType, onSelect: (MealType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.padding(end = Spacing.s)) {
            Text("${meal.label} ▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MealType.entries.forEach { m ->
                DropdownMenuItem(text = { Text(m.label) }, onClick = { onSelect(m); open = false })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Buscar alimento") },
        leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onChange("") },
                    modifier = Modifier.semantics { contentDescription = "Borrar búsqueda" },
                ) { Text("✕") }
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Escanear · Código EAN · Alimento propio. En escritorio no hay escáner: «Código EAN» pasa a destacado. */
@Composable
private fun QuickActions(onScan: () -> Unit, onCustom: () -> Unit, modifier: Modifier = Modifier) {
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
        if (isBarcodeScannerSupported) {
            Button(onClick = onScan, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = primary) {
                Text("Escanear")
            }
        }
        // El escáner y la entrada manual de EAN comparten pantalla (docs/design.md §2.3).
        Button(
            onClick = onScan,
            modifier = tall.weight(1f),
            shape = MaterialTheme.shapes.medium,
            colors = if (isBarcodeScannerSupported) neutral else primary,
        ) { Text("Código EAN") }
        Button(onClick = onCustom, modifier = tall.weight(1f), shape = MaterialTheme.shapes.medium, colors = neutral) {
            Text("Alimento propio")
        }
    }
}
