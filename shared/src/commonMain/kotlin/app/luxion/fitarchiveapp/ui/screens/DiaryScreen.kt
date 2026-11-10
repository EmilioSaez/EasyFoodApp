package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.ExerciseStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.MealTemplateStore
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.data.WaterStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.ui.components.ExerciseCard
import app.luxion.fitarchiveapp.ui.components.ExerciseDialog
import app.luxion.fitarchiveapp.ui.components.WaterCard
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
    waterStore: WaterStore? = null,
    templateStore: MealTemplateStore? = null,
    exerciseStore: ExerciseStore? = null,
) {
    val scope = rememberCoroutineScope()
    val presenter = remember(diaryStore, goalStore, mealsPerDay, templateStore, exerciseStore) {
        DiaryPresenter(diaryStore, goalStore, scope, today, mealsPerDay, templates = templateStore, exercise = exerciseStore)
    }
    val state by presenter.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<EntryUi?>(null) }
    var pickingDate by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf<CopyRequest?>(null) }
    var savingTemplate by remember { mutableStateOf<MealType?>(null) }
    var templateError by remember { mutableStateOf<String?>(null) }
    var addingExercise by remember { mutableStateOf(false) }

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
                onClick = { navigator.push(Route.Add(date = state.date)) },
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
                onSummary = { navigator.push(Route.Summary) },
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
                // La franja semanal y la tarjeta de objetivo son el primer elemento de la lista: se desplazan con ella.
                Column(Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.s, bottom = 104.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        item("header") { DiaryFullHeader(state, presenter, onSetGoal) }
                        if (showWeightReminder) item("weight-reminder") { WeightReminderCard(weightDays, registerWeight) }
                        if (waterStore != null) item("water") { WaterCard(waterStore, state.date) }
                        if (exerciseStore != null) item("exercise") {
                            ExerciseCard(
                                state,
                                onAdd = { addingExercise = true },
                                onDelete = { presenter.deleteExercise(it.id) },
                            )
                        }
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
                        item("meals-row") {
                            MealsRow(canCopyDay = !state.isEmpty, onCopyDay = { copying = CopyRequest(null) })
                        }
                        items(state.meals, key = { it.meal }) { meal ->
                            MealSection(
                                title = meal.meal.label,
                                summary = mealSummary(meal.kcal, meal.proteinG, meal.carbsG, meal.fatG),
                                onAdd = { navigator.push(Route.Add(meal.meal, date = state.date)) },
                                onCopy = if (meal.entries.isEmpty()) null else { { copying = CopyRequest(meal.meal) } },
                                onSaveTemplate = if (meal.entries.isEmpty() || templateStore == null) null else {
                                    { templateError = null; savingTemplate = meal.meal }
                                },
                                isEmpty = meal.entries.isEmpty(),
                            ) {
                                meal.entries.forEach { entry ->
                                    DiaryEntryRow(
                                        name = entry.name,
                                        quantityText = if (entry.quick) entry.macrosText else entry.quantity.format(),
                                        source = entry.source,
                                        kcal = entry.kcal,
                                        doubtful = entry.doubtful,
                                        showSource = !entry.quick,
                                        onClick = {
                                            if (entry.quick) {
                                                navigator.push(Route.QuickEntry(meal.meal, state.date, entry.id))
                                            } else {
                                                navigator.push(Route.ProductDetail(entry.barcode, meal.meal, entry.id))
                                            }
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

    if (addingExercise) {
        ExerciseDialog(
            onDismiss = { addingExercise = false },
            onConfirm = { name, kcal, minutes ->
                addingExercise = false
                presenter.addExercise(name, kcal, minutes)
            },
        )
    }

    copying?.let { request ->
        CopyDialog(
            request = request,
            sourceDate = state.date,
            mealsPerDay = mealsPerDay,
            onDismiss = { copying = null },
            onConfirm = { date, meal ->
                copying = null
                if (request.meal == null) presenter.copyDay(date) else presenter.copyMeal(request.meal, date, meal)
            },
        )
    }

    savingTemplate?.let { meal ->
        TemplateNameDialog(
            title = "Guardar ${meal.label} como plantilla",
            initialName = meal.label,
            confirmLabel = "Guardar",
            error = templateError,
            onDismiss = { savingTemplate = null },
            onConfirm = { name ->
                presenter.saveMealAsTemplate(meal, name) { error ->
                    templateError = error
                    if (error == null) savingTemplate = null
                }
            },
        )
    }

    editing?.let { entry ->
        EntryActionsDialog(
            entry = entry,
            onDismiss = { editing = null },
            onEditQuick = {
                editing = null
                navigator.push(Route.QuickEntry(date = state.date, entryId = entry.id))
            },
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
    onEditQuick: () -> Unit,
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
            // Una entrada rápida no tiene cantidad que cambiar: se edita en su pantalla.
            if (entry.quick) Text(entry.macrosText.ifEmpty { "Entrada rápida" }) else QuantitySelector(
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
            if (entry.quick) {
                TextButton(onClick = onEditQuick) { Text("Editar") }
            } else {
                TextButton(onClick = { onSave(Quantity(amount!!, unit)) }, enabled = valid) { Text("Guardar") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

/** Fila «COMIDAS» con «Copiar día» (y su icono) a la derecha; el botón solo aparece si el día tiene entradas. */
@Composable
private fun MealsRow(canCopyDay: Boolean, onCopyDay: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "COMIDAS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (canCopyDay) {
            TextButton(onClick = onCopyDay) {
                Text("Copiar día")
                Spacer(Modifier.width(Spacing.xs))
                CopyIcon(MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** Icono de copiar: dos hojas superpuestas (no hay librería de iconos). */
@Composable
private fun CopyIcon(color: Color) {
    Canvas(Modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.5.dp.toPx())
        val corner = CornerRadius(2.dp.toPx())
        val sheet = Size(10.dp.toPx(), 10.dp.toPx())
        drawRoundRect(color, topLeft = Offset(5.dp.toPx(), 5.dp.toPx()), size = sheet, cornerRadius = corner, style = stroke)
        drawRoundRect(color, topLeft = Offset(1.dp.toPx(), 1.dp.toPx()), size = sheet, cornerRadius = corner, style = stroke)
    }
}
