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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.WeightReminder
import app.luxion.fitarchiveapp.ui.components.WeightReminderCard
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
import app.luxion.fitarchiveapp.ui.components.mealSummary
import app.luxion.fitarchiveapp.ui.components.parseNumber
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Diario del día (docs/design.md §2.1 y §2.12). [today] es la fecha ISO de hoy. */
@Composable
fun DiaryScreen(
    navigator: Navigator,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    weightStore: WeightStore,
    today: String,
    mealsPerDay: Int? = null,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(diaryStore, goalStore, mealsPerDay) { DiaryPresenter(diaryStore, goalStore, scope, today, mealsPerDay) }
    val state by presenter.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EntryUi?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

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
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigator.push(Route.Add()) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) { Text("+ Añadir alimento") }
        },
        bottomBar = { EasyFoodNavigationBar(TopLevelTab.Diary, navigator) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Fila anclada: «Hoy ▾», día anterior/siguiente, racha y datos.
            DiaryTopRow(
                state,
                onPrevious = { presenter.shiftDay(-1) },
                onNext = { presenter.shiftDay(1) },
                onPickDate = { pickingDate = true },
                onData = { navigator.push(Route.Data) },
                modifier = Modifier.padding(horizontal = Spacing.s),
            )
            val onSetGoal = { navigator.navigateTab(TopLevelTab.Goals) }
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
            } else if (!state.loading) {
                val listState = rememberLazyListState()
                // Cabecera colapsable: en cuanto se desplaza, franja y tarjeta se pliegan en el DiaryHeader compacto.
                val collapsed by remember {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
                }
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    if (collapsed) {
                        // Anclado sobre la lista: el objetivo y cómo se va, siempre visibles (sin tapar tarjetas).
                        DiaryHeader(
                            state,
                            onSetGoal = onSetGoal,
                            modifier = Modifier.padding(start = Spacing.l, end = Spacing.l, top = Spacing.s),
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        item("header") { DiaryFullHeader(state, presenter, onSetGoal) }
                        if (showWeightReminder) item("weight-reminder") { WeightReminderCard(weightDays, registerWeight) }
                        if (state.isEmpty) {
                            // Ayuda discreta de primer uso: no sustituye a las tarjetas de comida.
                            item("empty-hint") {
                                Column {
                                    Text(
                                        if (state.date == today) "Nada apuntado hoy" else "Nada apuntado este día",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        "Añade lo que comas con el «+» de cada comida.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        items(state.meals, key = { it.meal }) { meal ->
                            MealSection(
                                title = meal.meal.label,
                                summary = mealSummary(meal.kcal, meal.proteinG, meal.carbsG, meal.fatG),
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
    }

    if (pickingDate) {
        DiaryDatePicker(
            date = state.date,
            onPick = { pickingDate = false; presenter.select(it) },
            onDismiss = { pickingDate = false },
        )
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

/** Cabecera completa (con la lista arriba): franja semanal y tarjeta de objetivo; la fila anclada va fuera. */
@Composable
private fun DiaryFullHeader(
    state: DiaryUiState,
    presenter: DiaryPresenter,
    onSetGoal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
        WeekStrip(state.week, onSelect = presenter::select, onShiftWeek = { presenter.shiftDay(it * 7) })
        GoalCard(state, onSetGoal)
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
