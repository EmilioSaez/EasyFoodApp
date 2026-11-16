package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.luxion.fitarchiveapp.data.addTemplate
import app.luxion.fitarchiveapp.domain.RecipeCalculator
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.ui.components.EasyFoodFilterChip
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.FoodResultRow
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * HUECO B3 · listas de la hoja «Añadir»: Recientes · Favoritos · Plantillas · Recetas, en pestañas. Recientes y favoritos
 * abren «Confirmar alimento»; plantillas y recetas se apuntan en la comida y el día de la hoja (tocar la fila pregunta
 * cantidad, comida y fecha). Los detalles y la edición de plantillas y recetas viven en sus pantallas.
 */
@Composable
fun AddSheetLists(model: AddSheetModel, navigator: Navigator) {
    val deps = model.deps
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AddListMode.entries.forEach { m ->
                if (m == AddListMode.Templates && deps.templateStore == null) return@forEach
                if (m == AddListMode.Recipes && deps.recipeStore == null) return@forEach
                EasyFoodFilterChip(selected = model.mode == m, onClick = { model.mode = m }, label = { Text(m.label) })
            }
        }
        when (model.mode) {
            AddListMode.Recents -> RecentsList(model, navigator)
            AddListMode.Favorites -> FoodList(
                foods = model.favorites,
                emptyIcon = "☆",
                emptyTitle = "Aún no tienes favoritos",
                emptyMessage = "Toca la estrella ☆ de un alimento, en los resultados o en su ficha, para tenerlo aquí.",
                model = model,
                navigator = navigator,
            )
            AddListMode.Templates -> TemplatesList(model, navigator)
            AddListMode.Recipes -> RecipesList(model, navigator)
        }
    }
}

@Composable
private fun RecentsList(model: AddSheetModel, navigator: Navigator) {
    val deps = model.deps
    var recents by remember { mutableStateOf<List<FoodResult>?>(null) }
    LaunchedEffect(deps.diaryStore, deps.customFoods) {
        recents = try {
            recentFoodResults(deps.diaryStore?.recentFoods(RECENT_FOODS_LIMIT).orEmpty(), deps.customFoods?.all().orEmpty())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    FoodList(
        foods = recents,
        emptyIcon = "◷",
        emptyTitle = "Aún no hay recientes",
        emptyMessage = "Los alimentos que apuntes en el diario aparecerán aquí. Busca uno o usa el escáner.",
        model = model,
        navigator = navigator,
    )
}

/** Lista de alimentos (recientes o favoritos); [foods] `null` = aún cargando. */
@Composable
private fun FoodList(
    foods: List<FoodResult>?,
    emptyIcon: String,
    emptyTitle: String,
    emptyMessage: String,
    model: AddSheetModel,
    navigator: Navigator,
) {
    when {
        foods == null -> Unit
        foods.isEmpty() -> EmptyState(icon = emptyIcon, title = emptyTitle, message = emptyMessage, modifier = Modifier.fillMaxWidth())
        else -> Column {
            foods.forEach { r ->
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

@Composable
private fun TemplatesList(model: AddSheetModel, navigator: Navigator) {
    val deps = model.deps
    val store = deps.templateStore ?: return
    val scope = rememberCoroutineScope()
    var templates by remember { mutableStateOf<List<MealTemplate>?>(null) }
    var choosing by remember { mutableStateOf<MealTemplate?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(store) {
        templates = try {
            store.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    // Escribe la plantilla en el diario y vuelve a él; si falla, avisa sin cerrar la hoja.
    val add: (MealTemplate, String, MealType) -> Unit = add@{ template, date, meal ->
        val diary = deps.diaryStore ?: return@add
        scope.launch {
            try {
                diary.addTemplate(template, date, meal, kotlin.time.Clock.System.now().toEpochMilliseconds())
                choosing = null
                navigator.popToDiary()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "No se pudo añadir la plantilla. Inténtalo de nuevo."
            }
        }
    }
    when (val list = templates) {
        null -> Unit
        else -> Column {
            if (list.isEmpty()) {
                EmptyState(
                    icon = "☰",
                    title = "Aún no tienes plantillas",
                    message = "Guarda «mi desayuno de siempre» con «Plantilla» en una comida del Diario, o créala aquí.",
                    primaryAction = EmptyStateAction("Nueva plantilla") { navigator.push(Route.TemplateEdit()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                error?.takeIf { choosing == null }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                list.forEach { t ->
                    SavedRow(
                        title = t.name,
                        detail = "${t.listDetail()}\n${t.itemsPreview()}",
                        onClick = { error = null; choosing = t },
                        actionLabel = "Añadir",
                        actionDescription = "Añadir ${t.name} al diario",
                        onAction = { error = null; add(t, model.date, model.meal) },
                        editDescription = "Editar ${t.name}",
                        onEdit = { navigator.push(Route.TemplateEdit(t.id)) },
                    )
                }
                TextButton(onClick = { navigator.push(Route.TemplateEdit()) }) { Text("Nueva plantilla") }
            }
        }
    }
    choosing?.let { t ->
        AddTemplateDialog(
            template = t,
            today = model.date,
            initialMeal = model.meal,
            mealChoices = deps.mealChoices,
            error = error,
            onDismiss = { choosing = null; error = null },
            onConfirm = { date, meal -> add(t, date, meal) },
        )
    }
}

@Composable
private fun RecipesList(model: AddSheetModel, navigator: Navigator) {
    val deps = model.deps
    val store = deps.recipeStore ?: return
    var recipes by remember { mutableStateOf<List<Recipe>?>(null) }
    var choosing by remember { mutableStateOf<Recipe?>(null) }
    LaunchedEffect(store) {
        recipes = try {
            store.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    when (val list = recipes) {
        null -> Unit
        else -> Column {
            if (list.isEmpty()) {
                EmptyState(
                    icon = "☰",
                    title = "Aún no tienes recetas",
                    message = "Crea una receta con sus ingredientes y apúntala de un toque.",
                    primaryAction = EmptyStateAction("Nueva receta") { navigator.push(Route.RecipeEdit()) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                list.forEach { r ->
                    SavedRow(
                        title = r.name,
                        detail = r.listDetail(),
                        onClick = { choosing = r },
                        actionLabel = "Añadir",
                        actionDescription = "Añadir ${r.name} al diario",
                        onAction = { choosing = r },
                    )
                }
                TextButton(onClick = { navigator.push(Route.RecipeEdit()) }) { Text("Nueva receta") }
            }
        }
    }
    choosing?.let { r ->
        AddRecipeDialog(r, model, navigator, onDismiss = { choosing = null })
    }
}

@Composable
private fun SavedRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    actionLabel: String,
    actionDescription: String,
    onAction: () -> Unit,
    editDescription: String? = null,
    onEdit: () -> Unit = {},
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(detail, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                OutlinedButton(onClick = onAction, modifier = Modifier.semantics { contentDescription = actionDescription }) { Text(actionLabel) }
                if (editDescription != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editDescription }) {
                        Text("✎", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        },
    )
}

/** Pide raciones, comida y día y apunta los ingredientes de [recipe] en el diario (como la pantalla de la receta, pero con el día de la hoja). */
@Composable
private fun AddRecipeDialog(recipe: Recipe, model: AddSheetModel, navigator: Navigator, onDismiss: () -> Unit) {
    val deps = model.deps
    val scope = rememberCoroutineScope()
    var date by remember(recipe) { mutableStateOf(model.date) }
    var meal by remember(recipe) { mutableStateOf(model.meal) }
    var portionsText by remember(recipe) { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val portions = if (recipe.servings != null) parseNumber(portionsText) ?: 0.0 else 1.0
    val factor = RecipeCalculator.factor(recipe.servings, portions)
    val kcal = factor?.let { f -> RecipeCalculator.totals(recipe.ingredients).kcal?.let { it * f } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (recipe.servings != null) {
                    NumberField(
                        value = portionsText,
                        onValueChange = { portionsText = it },
                        label = "Raciones",
                        suffix = "de ${recipe.servings}",
                        isError = factor == null,
                        supportingText = if (factor == null) "Escribe un número mayor que 0" else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text("Se añade la receta entera.", style = MaterialTheme.typography.bodyMedium)
                }
                DateStepperRow(date, { date = it })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    deps.mealChoices.options(model.meal).forEach { m ->
                        EasyFoodFilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                    }
                }
                Text(
                    "Se apunta como ${recipe.ingredients.size} ${if (recipe.ingredients.size == 1) "alimento" else "alimentos"}" +
                        (kcal?.let { " · ${formatKcal(it)} kcal" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = factor != null && !saving && deps.diaryStore != null,
                onClick = {
                    val f = factor ?: return@TextButton
                    val diary = deps.diaryStore ?: return@TextButton
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                            RecipeCalculator.diaryEntries(recipe.ingredients, f, date, meal).forEachIndexed { i, e -> diary.add(e, now + i) }
                            navigator.popToDiary()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = "No se pudo añadir la receta al diario. Inténtalo de nuevo."
                            saving = false
                        }
                    }
                },
            ) { Text("Añadir a ${meal.label}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
