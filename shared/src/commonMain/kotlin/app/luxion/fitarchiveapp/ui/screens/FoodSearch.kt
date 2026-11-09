package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.FoodResultRow
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private const val SEARCH_DEBOUNCE_MS = 300L

private sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Done(val picks: List<FoodPick>) : SearchState
    data object Failed : SearchState
}

/**
 * Buscador de alimentos compartido por «Añadir» y por el editor de recetas (docs/design.md §2.2): campo de
 * búsqueda (FTS local + alimentos propios) y lista de resultados, mezclando todos los orígenes (cada fila muestra el
 * suyo). Open Food Facts solo aparece si el producto ya está en la caché local. [quickActions] se pinta bajo el campo;
 * [idleContent] ocupa el sitio de los resultados mientras no hay texto (por defecto, una invitación a buscar);
 * [emptyPrimary] y [emptySecondary] son las acciones del estado «sin resultados».
 */
@Composable
fun FoodSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    searchStore: ProductSearchStore?,
    customFoods: CustomFoodStore?,
    onPick: (FoodPick) -> Unit,
    modifier: Modifier = Modifier,
    emptyPrimary: EmptyStateAction? = null,
    emptySecondary: EmptyStateAction? = null,
    quickActions: @Composable () -> Unit = {},
    idleContent: (@Composable () -> Unit)? = null,
) {
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
            SearchState.Done(mergePicks(custom, products))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SearchState.Failed
        }
    }

    Column(modifier.fillMaxSize()) {
        SearchField(query, onQueryChange, Modifier.padding(horizontal = Spacing.l))
        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (state is SearchState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        quickActions()
        when (val s = state) {
            SearchState.Loading -> Unit
            SearchState.Idle -> if (idleContent != null) {
                idleContent()
            } else {
                EmptyState(
                    icon = "⌕",
                    title = "Busca un alimento",
                    message = "Escribe el nombre o la marca, o usa el código de barras.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SearchState.Failed -> EmptyState(
                icon = "!",
                title = "No se pudo buscar",
                message = "Ha fallado la búsqueda en los datos guardados.",
                tone = EmptyStateTone.Error,
                primaryAction = EmptyStateAction("Reintentar") { retry++ },
                modifier = Modifier.fillMaxWidth(),
            )
            is SearchState.Done -> {
                val shown = s.picks
                if (shown.isEmpty()) {
                    EmptyState(
                        icon = "⌕",
                        title = "Sin resultados para «${query.trim()}»",
                        message = "Prueba con otra palabra o añade el alimento tú mismo.",
                        primaryAction = emptyPrimary,
                        secondaryAction = emptySecondary,
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
                        items(shown, key = { it.result.key }) { p ->
                            val r = p.result
                            FoodResultRow(
                                name = r.name,
                                detail = r.detail,
                                source = r.source,
                                kcalPer100g = r.kcalPer100g,
                                onClick = { onPick(p) },
                            )
                        }
                    }
                }
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
