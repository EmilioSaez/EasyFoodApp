package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.data.DiaryStore
import app.luxion.easyfoodapp.data.MealTemplateStore
import app.luxion.easyfoodapp.ui.components.EasyFoodFilterChip
import app.luxion.easyfoodapp.data.RecipeStore
import app.luxion.easyfoodapp.model.Recipe
import app.luxion.easyfoodapp.ui.components.EmptyState
import app.luxion.easyfoodapp.ui.components.EmptyStateAction
import app.luxion.easyfoodapp.ui.components.EmptyStateTone
import app.luxion.easyfoodapp.ui.components.StackedMacroBar
import app.luxion.easyfoodapp.ui.components.formatKcal
import app.luxion.easyfoodapp.ui.theme.easyFoodColors
import app.luxion.easyfoodapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.easyfoodapp.ui.navigation.Navigator
import app.luxion.easyfoodapp.ui.navigation.Route
import app.luxion.easyfoodapp.ui.navigation.TopLevelTab
import app.luxion.easyfoodapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException

private sealed interface RecipesState {
    data object Loading : RecipesState
    data object Failed : RecipesState
    data class Ready(val recipes: List<Recipe>) : RecipesState
}

/** Pestañas de «Guardado» (docs/design.md §2.23): recetas y plantillas de comida. */
enum class SavedTab { Recipes, Templates }

/**
 * «Guardado» (docs/design.md §2.23): lo que el usuario ha guardado para añadirlo rápido, en dos pestañas: **Recetas**
 * (lista con energía por ración, macros y aviso de datos que faltan) y **Plantillas** (sus comidas de siempre).
 * Se abre desde «Tú» y desde la hoja «Añadir» (`Route.Recipes` / `Route.Templates`). Sin almacén de plantillas (o sin
 * diario, fecha o comidas), solo hay recetas.
 */
@Composable
fun RecipesScreen(
    navigator: Navigator,
    recipeStore: RecipeStore,
    initialTab: SavedTab = SavedTab.Recipes,
    templateStore: MealTemplateStore? = null,
    diaryStore: DiaryStore? = null,
    today: (() -> String)? = null,
    mealChoices: MealChoices? = null,
) {
    val templatesEnabled = templateStore != null && diaryStore != null && today != null && mealChoices != null
    var tab by remember(initialTab) { mutableStateOf(if (templatesEnabled) initialTab else SavedTab.Recipes) }
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

    val recipesState = state
    Scaffold(
        bottomBar = { EasyFoodNavigationBar(selected = TopLevelTab.You, navigator = navigator) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.screen, top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { navigator.pop() },
                    modifier = Modifier.semantics { contentDescription = "Atrás" },
                ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                Column(Modifier.weight(1f)) {
                    Text("Guardado", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Para añadir a tu diario de un toque",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (templatesEnabled) {
                Row(
                    Modifier.padding(horizontal = Spacing.screen, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    EasyFoodFilterChip(selected = tab == SavedTab.Recipes, onClick = { tab = SavedTab.Recipes }, label = { Text("Recetas") })
                    EasyFoodFilterChip(selected = tab == SavedTab.Templates, onClick = { tab = SavedTab.Templates }, label = { Text("Plantillas") })
                }
            } else {
                Spacer(Modifier.height(Spacing.md))
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (tab == SavedTab.Templates && templatesEnabled) {
                    TemplatesTab(navigator, templateStore!!, diaryStore!!, today!!, mealChoices!!)
                } else {
                    RecipesTab(navigator, recipesState) { reload++ }
                }
            }
        }
    }
}

@Composable
private fun RecipesTab(navigator: Navigator, state: RecipesState, onRetry: () -> Unit) {
    when (state) {
        RecipesState.Loading -> Unit
        RecipesState.Failed -> EmptyState(
            icon = "!",
            title = "No se pudieron leer las recetas",
            message = "Ha fallado la lectura de los datos guardados.",
            tone = EmptyStateTone.Error,
            primaryAction = EmptyStateAction("Reintentar", onRetry),
            modifier = Modifier.fillMaxSize(),
        )
        is RecipesState.Ready -> if (state.recipes.isEmpty()) {
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
                contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs, bottom = Spacing.bottomInset),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(state.recipes, key = { it.id }) { r ->
                    RecipeCard(
                        r,
                        onClick = { navigator.push(Route.RecipeDetail(r.id)) },
                        onEdit = { navigator.push(Route.RecipeEdit(r.id)) },
                    )
                }
                item("new") {
                    FilledTonalButton(
                        onClick = { navigator.push(Route.RecipeEdit()) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text("Nueva receta") }
                }
            }
        }
    }
}

/** Tarjeta de receta: nombre, kcal por ración (o total), ingredientes y raciones, barra de macros y aviso ámbar. */
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, onEdit: () -> Unit) {
    val totals = recipe.cardTotals()
    val perServing = recipe.servings != null
    val protein = totals.proteinG
    val carbs = totals.carbsG
    val fat = totals.fatG
    val notice = recipe.missingDataNotice()
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(recipe.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
                        Text(formatKcal(kcal), style = MaterialTheme.typography.headlineMedium)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Toca para ver los ingredientes y añadirla",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Editar ${recipe.name}" }) {
                    Text("✎", style = MaterialTheme.typography.titleLarge)
                }
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
