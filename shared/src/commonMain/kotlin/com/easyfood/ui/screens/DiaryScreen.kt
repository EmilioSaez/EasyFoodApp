package com.easyfood.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.easyfood.data.DiaryStore
import com.easyfood.data.GoalStore
import com.easyfood.model.MealType
import com.easyfood.model.Quantity
import com.easyfood.model.QuantityUnit
import com.easyfood.ui.components.CalorieRing
import com.easyfood.ui.components.DiaryEntryRow
import com.easyfood.ui.components.EmptyState
import com.easyfood.ui.components.EmptyStateAction
import com.easyfood.ui.components.MacroProgressRow
import com.easyfood.ui.components.MealSection
import com.easyfood.ui.components.QuantitySelector
import com.easyfood.ui.components.formatGrams
import com.easyfood.ui.components.formatKcal
import com.easyfood.ui.components.label
import com.easyfood.ui.components.macroSummary
import com.easyfood.ui.components.parseNumber
import com.easyfood.ui.navigation.EasyFoodNavigationBar
import com.easyfood.ui.navigation.Navigator
import com.easyfood.ui.navigation.Route
import com.easyfood.ui.navigation.TopLevelTab
import com.easyfood.ui.theme.Spacing
import com.easyfood.ui.theme.easyFoodColors
import kotlinx.coroutines.launch

/** Diario del día (docs/design.md §2.1). [today] es la fecha ISO de hoy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(navigator: Navigator, diaryStore: DiaryStore, goalStore: GoalStore, today: String) {
    val scope = rememberCoroutineScope()
    val presenter = remember(diaryStore, goalStore) { DiaryPresenter(diaryStore, goalStore, scope, today) }
    val state by presenter.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EntryUi?>(null) }

    LaunchedEffect(presenter) { presenter.load() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (state.date == today) "Hoy" else IsoDate.long(state.date))
                        if (state.date == today) {
                            Text(
                                IsoDate.long(state.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { presenter.shiftDay(-1) },
                        modifier = Modifier.semantics { contentDescription = "Día anterior" },
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                },
                actions = {
                    IconButton(
                        onClick = { presenter.shiftDay(1) },
                        modifier = Modifier.semantics { contentDescription = "Día siguiente" },
                    ) { Text("›", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.push(Route.Add(MealType.SNACK)) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Text("+ Añadir alimento") }
        },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.Diary, navigator) },
    ) { padding ->
        if (!state.loading && state.isEmpty) {
            Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(
                    icon = "▤",
                    title = if (state.date == today) "Nada apuntado hoy" else "Nada apuntado este día",
                    message = "Añade lo que comas para ver tus calorías y macros del día.",
                    primaryAction = EmptyStateAction("Añadir alimento") { navigator.push(Route.Add(MealType.SNACK)) },
                    secondaryAction = EmptyStateAction("Fijar objetivo") { navigator.navigateTab(TopLevelTab.Goals) },
                    modifier = Modifier.padding(top = Spacing.xxxl),
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.l, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                item { DaySummaryCard(state, onSetGoal = { navigator.navigateTab(TopLevelTab.Goals) }) }
                items(state.meals, key = { it.meal }) { meal ->
                    MealSection(
                        title = meal.meal.label,
                        summary = macroSummary(meal.kcal, meal.proteinG, meal.carbsG, meal.fatG),
                        onAdd = { navigator.push(Route.Add(meal.meal)) },
                        isEmpty = meal.entries.isEmpty(),
                    ) {
                        meal.entries.forEach { entry ->
                            DiaryEntryRow(
                                name = entry.name,
                                quantityText = entry.quantity.format(),
                                source = entry.source,
                                kcal = entry.kcal,
                                doubtful = entry.doubtful,
                                onClick = {
                                    navigator.push(Route.ProductDetail(entry.barcode, meal.meal, entry.id))
                                },
                                onLongClick = { editing = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        EntryActionsDialog(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { quantity ->
                editing = null
                presenter.updateQuantity(entry.id, quantity)
            },
            onDelete = {
                editing = null
                presenter.delete(entry.id)
                scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = snackbar.showSnackbar("Borrado: ${entry.name}", actionLabel = "Deshacer")
                    if (result == SnackbarResult.ActionPerformed) presenter.undoDelete()
                }
            },
        )
    }
}

@Composable
private fun DaySummaryCard(state: DiaryUiState, onSetGoal: () -> Unit) {
    val colors = MaterialTheme.easyFoodColors
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            Modifier.padding(Spacing.l),
            horizontalArrangement = Arrangement.spacedBy(Spacing.l),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val goal = state.goalKcal
            if (goal != null) {
                CalorieRing(consumedKcal = state.consumedKcal, goalKcal = goal.toDouble())
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(
                    if (goal != null) "${formatKcal(state.consumedKcal)} / ${formatKcal(goal.toDouble())} kcal"
                    else "${formatKcal(state.consumedKcal)} kcal",
                    style = MaterialTheme.typography.titleMedium,
                )
                state.macros.forEach { macro ->
                    val color = when (macro.label) {
                        "Proteína" -> colors.macroProtein
                        "Hidratos" -> colors.macroCarbs
                        else -> colors.macroFat
                    }
                    if (macro.goalG != null) {
                        MacroProgressRow(macro.label, macro.consumedG, macro.goalG, color)
                    } else {
                        Text(
                            "${macro.label} · ${formatGrams(macro.consumedG)} g",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (goal == null) {
                    OutlinedButton(onClick = onSetGoal) { Text("Fijar objetivo") }
                }
            }
        }
    }
}

/** Pulsación larga en una entrada: cambiar la cantidad o borrarla. */
@Composable
private fun EntryActionsDialog(
    entry: EntryUi,
    onDismiss: () -> Unit,
    onSave: (Quantity) -> Unit,
    onDelete: () -> Unit,
) {
    var unit by remember { mutableStateOf(entry.quantity.unit) }
    var amountText by remember { mutableStateOf(formatGrams(entry.quantity.amount).replace(".", "")) }
    val amount = parseNumber(amountText)
    val valid = amount != null && amount > 0 && amount.isFinite()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, maxLines = 2) },
        text = {
            QuantitySelector(
                unit = unit,
                onUnitChange = { next ->
                    // Al cambiar de unidad se convierte la cantidad para no reinterpretar «30 g» como 30 porciones.
                    val size = entry.servingSizeG
                    if (next != unit && amount != null && size != null) {
                        val converted = if (next == QuantityUnit.GRAMS) amount * size else amount / size
                        amountText = formatGrams(converted).replace(".", "")
                    }
                    unit = next
                },
                amountText = amountText,
                onAmountTextChange = { amountText = it },
                servingsEnabled = entry.servingSizeG != null,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(Quantity(amount!!, unit)) }, enabled = valid) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}
