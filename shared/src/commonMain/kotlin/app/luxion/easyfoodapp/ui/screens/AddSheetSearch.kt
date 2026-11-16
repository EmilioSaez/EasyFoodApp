package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.ui.components.EasyFoodFilterChip
import app.luxion.easyfoodapp.ui.components.EmptyState
import app.luxion.easyfoodapp.ui.components.EmptyStateAction
import app.luxion.easyfoodapp.ui.components.EmptyStateTone
import app.luxion.easyfoodapp.ui.components.FoodResultRow
import app.luxion.easyfoodapp.ui.navigation.Navigator
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SHEET_SEARCH_DEBOUNCE_MS = 300L

private sealed interface SheetSearchState {
    data object Loading : SheetSearchState
    data class Done(val picks: List<FoodPick>) : SheetSearchState
    data object Failed : SheetSearchState
}

/**
 * HUECO B1 · buscador de la hoja «Añadir»: campo de texto (FTS local + alimentos propios, con «Solo mis productos») y, con
 * texto, la lista de resultados bajo él. Tocar un resultado abre «Confirmar alimento» con la comida y el día de la hoja;
 * sin resultados se ofrecen crear el alimento, escanear su código o fotografiar la etiqueta.
 */
@Composable
fun AddSheetSearch(model: AddSheetModel, navigator: Navigator) {
    val deps = model.deps
    val query = model.query
    var retry by remember { mutableStateOf(0) }
    var onlyMine by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<SheetSearchState>(SheetSearchState.Loading) }

    LaunchedEffect(query, retry, onlyMine) {
        if (query.isBlank()) return@LaunchedEffect
        state = SheetSearchState.Loading
        delay(SHEET_SEARCH_DEBOUNCE_MS)
        state = try {
            SheetSearchState.Done(searchPicks(query, onlyMine, deps.searchStore, deps.customFoods))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SheetSearchState.Failed
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SheetSearchField(query, { model.query = it })
        if (query.isBlank()) return@Column
        when (val s = state) {
            SheetSearchState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            SheetSearchState.Failed -> EmptyState(
                icon = "!",
                title = "No se pudo buscar",
                message = "Ha fallado la búsqueda en los datos guardados.",
                tone = EmptyStateTone.Error,
                primaryAction = EmptyStateAction("Reintentar") { retry++ },
                modifier = Modifier.fillMaxWidth(),
            )
            is SheetSearchState.Done -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (s.picks.isEmpty()) "Resultados" else "Resultados · ${s.picks.size}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    EasyFoodFilterChip(selected = onlyMine, onClick = { onlyMine = !onlyMine }, label = { Text("Solo mis productos") })
                }
                if (s.picks.isEmpty()) {
                    EmptyState(
                        icon = "⌕",
                        title = "Sin resultados para «${query.trim()}»",
                        message = if (onlyMine) {
                            "No hay nada así entre tus productos. Quita el filtro para buscar en todos."
                        } else {
                            "Prueba con otra palabra o añade el alimento tú mismo."
                        },
                        primaryAction = EmptyStateAction("Crear alimento propio") { navigator.push(Route.CustomFood(meal = model.meal)) },
                        secondaryAction = EmptyStateAction("Escanear código") { navigator.push(Route.Scanner(model.meal, model.date)) },
                        tertiaryAction = EmptyStateAction("Fotografiar etiqueta") { navigator.push(Route.LabelPhoto(meal = model.meal)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    s.picks.forEach { p ->
                        val r = p.result
                        FoodResultRow(
                            name = r.name,
                            detail = r.detail,
                            source = r.source,
                            kcalPer100g = r.kcalPer100g,
                            onClick = { navigator.push(model.confirmRoute(r.key)) },
                            favorite = if (model.favoritesEnabled) r.key in model.favoriteKeys else null,
                            onToggleFavorite = { model.toggleFavorite(r) },
                        )
                    }
                }
            }
        }
    }
}

/** «Confirmar alimento» de [key] (EAN, `generic:<id>` o `custom:<id>`) con la comida y el día de la hoja. */
internal fun AddSheetModel.confirmRoute(key: String) = Route.ProductDetail(barcode = key, meal = meal, date = date)

@Composable
private fun SheetSearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Busca un alimento o una marca") },
        leadingIcon = { Text("⌕", style = MaterialTheme.typography.titleLarge) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onChange("") },
                    modifier = Modifier.semantics { contentDescription = "Borrar búsqueda" },
                ) { Text("✕") }
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    )
}
