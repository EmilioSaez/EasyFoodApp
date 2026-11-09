package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException

private sealed interface RecipesState {
    data object Loading : RecipesState
    data object Failed : RecipesState
    data class Ready(val recipes: List<Recipe>) : RecipesState
}

/** Pestaña «Recetas» (docs/design.md §2.8): lista de recetas con su energía y acceso a crear una nueva. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(navigator: Navigator, recipeStore: RecipeStore) {
    var state by remember { mutableStateOf<RecipesState>(RecipesState.Loading) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(recipeStore, reload) {
        state = RecipesState.Loading
        state = try {
            RecipesState.Ready(recipeStore.all())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RecipesState.Failed
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Recetas") }) },
        bottomBar = { EasyFoodNavigationBar(selected = TopLevelTab.Recipes, navigator = navigator) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                RecipesState.Loading -> Unit
                RecipesState.Failed -> EmptyState(
                    icon = "!",
                    title = "No se pudieron leer las recetas",
                    message = "Ha fallado la lectura de los datos guardados.",
                    tone = EmptyStateTone.Error,
                    primaryAction = EmptyStateAction("Reintentar") { reload++ },
                    modifier = Modifier.fillMaxSize(),
                )
                is RecipesState.Ready -> if (s.recipes.isEmpty()) {
                    EmptyState(
                        icon = "≣",
                        title = "Aún no tienes recetas",
                        message = "Combina alimentos de la base de datos, indica la cantidad de cada uno y calcula sus macros.",
                        primaryAction = EmptyStateAction("Crear receta") { navigator.push(Route.RecipeEdit()) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Button(
                                onClick = { navigator.push(Route.RecipeEdit()) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.s),
                            ) { Text("+ Nueva receta") }
                        }
                        items(s.recipes, key = { it.id }) { r ->
                            ListItem(
                                modifier = Modifier.clickable { navigator.push(Route.RecipeDetail(r.id)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = { Text(r.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(r.listDetail()) },
                                trailingContent = {
                                    Text(
                                        r.kcalLabel(),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
