package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.StackedMacroBar
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
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
        floatingActionButton = {
            if ((state as? RecipesState.Ready)?.recipes?.isNotEmpty() == true) {
                ExtendedFloatingActionButton(
                    onClick = { navigator.push(Route.RecipeEdit()) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text("Nueva receta") }
            }
        },
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
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        // Hueco final para que el FAB no tape la última tarjeta.
                        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        items(s.recipes, key = { it.id }) { r ->
                            RecipeCard(r, onClick = { navigator.push(Route.RecipeDetail(r.id)) })
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta de receta: nombre, kcal por ración (o total), ingredientes y raciones, barra de macros y aviso ámbar. */
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val totals = recipe.cardTotals()
    val perServing = recipe.servings != null
    val protein = totals.proteinG
    val carbs = totals.carbsG
    val fat = totals.fatG
    val notice = recipe.missingDataNotice()
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        recipe.listDetail(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val kcal = totals.kcal
                if (kcal == null) {
                    Text("Sin dato", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatKcal(kcal), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (perServing) "kcal/ración" else "kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (protein != null && carbs != null && fat != null) {
                StackedMacroBar(protein, carbs, fat)
            }
            if (notice != null) {
                Surface(color = MaterialTheme.easyFoodColors.warningContainer, shape = MaterialTheme.shapes.medium) {
                    Text(
                        notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.easyFoodColors.onWarningContainer,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.m),
                    )
                }
            }
        }
    }
}
