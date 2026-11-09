package app.luxion.fitarchiveapp.ui.screens

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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.WeightReminder
import app.luxion.fitarchiveapp.ui.components.WeightReminderCard
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.components.DiaryEntryRow
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.MealSection
import app.luxion.fitarchiveapp.ui.components.QuantitySelector
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.label
import app.luxion.fitarchiveapp.ui.components.macroSummary
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Diario del día (docs/design.md §2.1). [today] es la fecha ISO de hoy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    navigator: Navigator,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    weightStore: WeightStore,
    today: String,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(diaryStore, goalStore) { DiaryPresenter(diaryStore, goalStore, scope, today) }
    val state by presenter.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EntryUi?>(null) }

    // Aviso semanal de peso: se recalcula cada vez que se abre el Diario, así desaparece al registrar.
    var weightDue by remember { mutableStateOf(false) }
    var weightDays by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(weightStore, today) {
        try {
            val entries = weightStore.all()
            weightDue = WeightReminder.isDue(entries, today)
            weightDays = WeightReminder.daysSinceLast(entries, today)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            weightDue = false // el aviso es opcional: si falla la lectura, no se muestra
        }
    }
    val showWeightReminder = weightDue && state.date == today
    val registerWeight = { navigator.navigateTab(TopLevelTab.Weight) }

    LaunchedEffect(presenter) { presenter.load() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(it)
            presenter.consumeMessage()
        }
    }

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
                    IconButton(
                        onClick = { navigator.push(Route.Data) },
                        modifier = Modifier.semantics { contentDescription = "Datos: exportar e importar" },
                    ) { Text("⇅", style = MaterialTheme.typography.titleLarge) }
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!state.loadFailed && !state.loading) {
                DiaryHeader(
                    state,
                    onSetGoal = { navigator.navigateTab(TopLevelTab.Goals) },
                    modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.s),
                )
            }
            if (state.loadFailed) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        icon = "!",
                        title = "No se pudo cargar el diario",
                        message = "Ha fallado la lectura de los datos guardados.",
                        tone = EmptyStateTone.Error,
                        primaryAction = EmptyStateAction("Reintentar") { presenter.load() },
                        modifier = Modifier.padding(top = Spacing.xxxl),
                    )
                }
            } else if (!state.loading && state.isEmpty) {
                Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (showWeightReminder) {
                        WeightReminderCard(weightDays, registerWeight, Modifier.padding(Spacing.l))
                    }
                    EmptyState(
                        icon = "▤",
                        title = if (state.date == today) "Nada apuntado hoy" else "Nada apuntado este día",
                        message = "Añade lo que comas para ver tus calorías y macros del día.",
                        primaryAction = EmptyStateAction("Añadir alimento") { navigator.push(Route.Add(MealType.SNACK)) },
                        // Sin objetivo, «Fijar objetivo» ya está en la cabecera: no se duplica.
                        secondaryAction = if (state.goalKcal == null) null
                        else EmptyStateAction("Fijar objetivo") { navigator.navigateTab(TopLevelTab.Goals) },
                        modifier = Modifier.padding(top = Spacing.xxxl),
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    if (showWeightReminder) item("weight-reminder") { WeightReminderCard(weightDays, registerWeight) }
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
