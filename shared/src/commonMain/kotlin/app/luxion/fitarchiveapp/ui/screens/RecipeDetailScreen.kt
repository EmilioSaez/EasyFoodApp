package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.RecipeStore
import app.luxion.fitarchiveapp.domain.RecipeCalculator
import app.luxion.fitarchiveapp.model.MacroTotals
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface DetailState {
    data object Loading : DetailState
    data object NotFound : DetailState
    data object Failed : DetailState
    data class Ready(val recipe: Recipe) : DetailState
}

/**
 * Detalle de una receta (docs/design.md §2.8): macros totales y por ración, ingredientes y «Añadir al diario».
 * La receta entra en el diario como un conjunto de alimentos: una entrada por ingrediente
 * (ver [RecipeCalculator.diaryEntries]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetailScreen(
    navigator: Navigator,
    route: Route.RecipeDetail,
    recipeStore: RecipeStore,
    diaryStore: DiaryStore,
    today: () -> String,
) {
    var state by remember(route) { mutableStateOf<DetailState>(DetailState.Loading) }
    var reload by remember(route) { mutableStateOf(0) }

    LaunchedEffect(route, reload) {
        state = DetailState.Loading
        state = try {
            recipeStore.find(route.recipeId)?.let { DetailState.Ready(it) } ?: DetailState.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DetailState.Failed
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Receta") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Atrás" },
                    ) { Text("←", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    if (state is DetailState.Ready) {
                        IconButton(
                            onClick = { navigator.push(Route.RecipeEdit(route.recipeId)) },
                            modifier = Modifier.semantics { contentDescription = "Editar receta" },
                        ) { Text("✎", style = MaterialTheme.typography.titleLarge) }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                DetailState.Loading -> Unit
                DetailState.NotFound -> EmptyState(
                    icon = "?",
                    title = "Receta no encontrada",
                    message = "Puede que se haya borrado.",
                    tone = EmptyStateTone.Warning,
                    primaryAction = EmptyStateAction("Volver") { navigator.pop() },
                    modifier = Modifier.fillMaxSize(),
                )
                DetailState.Failed -> EmptyState(
                    icon = "!",
                    title = "No se pudo leer la receta",
                    message = "Ha fallado la lectura de los datos guardados.",
                    tone = EmptyStateTone.Error,
                    primaryAction = EmptyStateAction("Reintentar") { reload++ },
                    secondaryAction = EmptyStateAction("Volver") { navigator.pop() },
                    modifier = Modifier.fillMaxSize(),
                )
                is DetailState.Ready -> RecipeContent(navigator, s.recipe, diaryStore, today)
            }
        }
    }
}

@Composable
private fun RecipeContent(navigator: Navigator, recipe: Recipe, diaryStore: DiaryStore, today: () -> String) {
    val scope = rememberCoroutineScope()
    var meal by remember(recipe) { mutableStateOf(MealType.LUNCH) }
    var portionsText by remember(recipe) { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val totals = RecipeCalculator.totals(recipe.ingredients)
    val perServing = RecipeCalculator.perServing(recipe.ingredients, recipe.servings)
    val incomplete = RecipeCalculator.incompleteIngredients(recipe.ingredients)
    val portions = if (recipe.servings != null) parseNumber(portionsText) ?: 0.0 else 1.0
    val factor = RecipeCalculator.factor(recipe.servings, portions)
    val addedKcal = factor?.let { f -> totals.kcal?.let { it * f } }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(recipe.name, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${recipe.listDetail()} · ${formatGrams(RecipeCalculator.totalGrams(recipe.ingredients))} g",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (incomplete.isNotEmpty()) {
                item {
                    Surface(color = MaterialTheme.easyFoodColors.warningContainer, shape = MaterialTheme.shapes.medium) {
                        Text(
                            "A ${incomplete.joinToString(", ")} le faltan datos nutricionales: los macros que dependen " +
                                "de ellos no se pueden calcular.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.easyFoodColors.onWarningContainer,
                            modifier = Modifier.padding(Spacing.m),
                        )
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        if (perServing != null) {
                            MacroBlock("Por ración · 1 de ${recipe.servings}", perServing)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        MacroBlock("Receta entera", totals)
                    }
                }
            }
            item {
                Text(
                    "Ingredientes · ${recipe.ingredients.size}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            itemsIndexed(recipe.ingredients) { _, ing ->
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = { Text(ing.name) },
                    supportingContent = { Text("${formatGrams(ing.grams)} g") },
                    trailingContent = {
                        Text(ing.totals.kcal?.let { "${formatKcal(it)} kcal" } ?: "Sin dato", style = MaterialTheme.typography.labelLarge)
                    },
                )
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    MealType.entries.forEach { m ->
                        FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                    }
                }
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
                    Text(
                        "Se añade la receta entera.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "Se apunta como ${recipe.ingredients.size} ${if (recipe.ingredients.size == 1) "alimento" else "alimentos"}" +
                        (addedKcal?.let { " · ${formatKcal(it)} kcal" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(
                    enabled = factor != null && !saving,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val f = factor ?: return@Button
                        saving = true
                        error = null
                        scope.launch {
                            try {
                                val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
                                // +índice: mantiene el orden de los ingredientes dentro de la comida.
                                RecipeCalculator.diaryEntries(recipe.ingredients, f, today(), meal)
                                    .forEachIndexed { i, entry -> diaryStore.add(entry, now + i) }
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
            }
        }
    }
}

/** Energía grande y los tres macros de [totals]; «Sin dato» donde falta alguna cifra. */
@Composable
private fun MacroBlock(title: String, totals: MacroTotals) {
    val colors = MaterialTheme.easyFoodColors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(
                totals.kcal?.let { "${formatKcal(it)} kcal" } ?: "Sin dato",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MacroCell("Proteína", totals.proteinG, colors.macroProtein, Modifier.weight(1f))
            MacroCell("Hidratos", totals.carbsG, colors.macroCarbs, Modifier.weight(1f))
            MacroCell("Grasa", totals.fatG, colors.macroFat, Modifier.weight(1f))
        }
    }
}
