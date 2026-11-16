package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.WeightGoalProgress
import app.luxion.fitarchiveapp.domain.WeightProgress
import app.luxion.fitarchiveapp.domain.WeightProjection
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.WeightParse
import app.luxion.fitarchiveapp.model.WeightGoal
import app.luxion.fitarchiveapp.model.WeightRules
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.ui.components.NumberField
import app.luxion.fitarchiveapp.ui.navigation.EasyFoodNavigationBar
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.TopLevelTab
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Error de la fecha escrita en el formulario, o `null` si vale (ISO real y no futura). */
fun weightDateError(text: String, today: String): String? {
    val day = isoDateToEpochDay(text.trim()) ?: return "Usa el formato AAAA-MM-DD"
    val todayDay = isoDateToEpochDay(today)
    return if (todayDay != null && day > todayDay) "La fecha no puede ser futura" else null
}

/** Mi peso: peso actual con su variación, gráfica por periodos (1M/3M/Todo), objetivo, registros y FAB para registrar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(navigator: Navigator, weightStore: WeightStore, today: () -> String) {
    val todayIso = today()
    var entries by remember { mutableStateOf<List<WeightEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var period by remember { mutableStateOf(WeightPeriod.ONE_MONTH) }
    var formOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WeightEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<WeightEntry?>(null) }
    var goal by remember { mutableStateOf<WeightGoal?>(null) }
    var editingGoal by remember { mutableStateOf(false) }
    var goalText by remember { mutableStateOf("") }
    var goalSubmitted by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        try {
            entries = weightStore.all()
            goal = weightStore.goal()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            snackbar.showSnackbar("No se pudieron leer los pesos guardados.")
        } finally {
            loaded = true
        }
    }
    LaunchedEffect(weightStore) { reload() }

    val goalParsed = WeightRules.parse(goalText)
    val goalError = (goalParsed as? WeightParse.Invalid)?.message

    fun saveGoal() {
        goalSubmitted = true
        val kg = (goalParsed as? WeightParse.Valid)?.kg ?: return
        scope.launch {
            try {
                weightStore.setGoal(kg, kotlin.time.Clock.System.now().toEpochMilliseconds())
                editingGoal = false
                reload()
                snackbar.showSnackbar("Peso objetivo guardado")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo guardar el peso objetivo. Inténtalo de nuevo.")
            }
        }
    }

    fun clearGoal() {
        scope.launch {
            try {
                weightStore.clearGoal()
                editingGoal = false
                reload()
                snackbar.showSnackbar("Peso objetivo quitado")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo quitar el peso objetivo.")
            }
        }
    }

    fun delete(entry: WeightEntry) {
        scope.launch {
            try {
                weightStore.delete(entry.date)
                if (editing?.date == entry.date) { editing = null; formOpen = false }
                reload()
                snackbar.showSnackbar("Registro borrado")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo borrar el registro.")
            }
        }
    }

    val goalProgress = WeightProgress.goalProgress(entries, goal?.targetKg, todayIso)

    Scaffold(
        topBar = { ScreenHeader("Mi peso", onBack = { navigator.pop() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                Button(
                    onClick = { editing = null; formOpen = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.md).heightIn(min = 52.dp),
                ) { Text("Registrar peso") }
                EasyFoodNavigationBar(selected = TopLevelTab.You, navigator = navigator)
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, top = Spacing.md, end = Spacing.screen, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item("chart") {
                YouCard {
                    WeightOverview(entries, loaded, goal, todayIso, period, onPeriod = { period = it })
                }
            }

            item("goal") {
                YouCard {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("Peso objetivo", style = MaterialTheme.typography.titleMedium)
                        val current = goal
                        if (editingGoal || (loaded && current == null)) {
                            NumberField(
                                value = goalText,
                                onValueChange = { goalText = it },
                                label = "Objetivo",
                                suffix = "kg",
                                isError = goalSubmitted && goalError != null,
                                supportingText = if (goalSubmitted && goalError != null) goalError else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                                Button(onClick = ::saveGoal) { Text("Guardar objetivo") }
                                if (current != null) TextButton(onClick = { editingGoal = false }) { Text("Cancelar") }
                            }
                        } else if (current != null) {
                            Text(formatKg(current.targetKg), style = MaterialTheme.typography.headlineMedium)
                            goalProgress?.let {
                                LinearProgressIndicator(
                                    progress = { it.percent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                GoalProgressInfo(it)
                            }
                                ?: Text(
                                    "Registra tu peso para ver cuánto te falta.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                TextButton(onClick = {
                                    goalText = formatKgInput(current.targetKg); goalSubmitted = false; editingGoal = true
                                }) { Text("Cambiar") }
                                TextButton(onClick = ::clearGoal) { Text("Quitar") }
                            }
                        }
                    }
                }
            }

            if (entries.isNotEmpty()) {
                item("history-title") { SectionLabel("Registros", Modifier.padding(top = Spacing.xs)) }
                val newestFirst = entries.sortedByDescending { it.date }
                items(newestFirst.size, key = { newestFirst[it].date }) { i ->
                    val e = newestFirst[i]
                    val previous = newestFirst.getOrNull(i + 1)
                    WeightRow(
                        entry = e,
                        change = previous?.let { WeightRules.round(e.weightKg - it.weightKg) },
                        onEdit = { editing = e; formOpen = true },
                        onDelete = { pendingDelete = e },
                    )
                    HorizontalDivider(Modifier.padding(top = Spacing.s), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }

    if (formOpen) {
        WeightEntryDialog(
            weightStore = weightStore,
            entries = entries,
            todayIso = todayIso,
            editing = editing,
            onDismiss = { formOpen = false; editing = null },
            onSaved = { message ->
                formOpen = false
                editing = null
                scope.launch { reload(); snackbar.showSnackbar(message) }
            },
            onError = { message -> scope.launch { snackbar.showSnackbar(message) } },
        )
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Borrar este registro?") },
            text = { Text("Se eliminará el peso del ${formatDayMonthYear(entry.date)} (${formatKg(entry.weightKg)}).") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; delete(entry) }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun GoalProgressInfo(progress: WeightGoalProgress) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Text(goalSummaryText(progress), style = MaterialTheme.typography.bodyLarge)
    progress.projection?.let { Text(projectionText(it), style = MaterialTheme.typography.bodyMedium, color = muted) }
}

/** «Te faltan 4,3 kg (35 % del camino)» · «¡Objetivo alcanzado!». */
fun goalSummaryText(p: WeightGoalProgress): String =
    if (p.reached) "¡Objetivo alcanzado!" else "Te faltan ${formatKg(p.remainingKg)} (${p.percent} % del camino)"

/** Frase de la estimación de llegada al objetivo. */
fun projectionText(projection: WeightProjection): String = when (projection) {
    WeightProjection.NotEnoughData ->
        "Aún no hay datos para estimar cuándo llegarás: registra tu peso al menos 3 veces durante una semana."
    WeightProjection.MovingAway -> "Con tu tendencia actual no te acercas al objetivo."
    WeightProjection.TooSlow -> "Con tu ritmo actual tardarías más de 2 años en llegar."
    is WeightProjection.Estimated -> {
        val whenText = if (projection.daysLeft == 0) "hoy" else "el ${formatDayMonthYear(projection.date)}"
        "Llegarías $whenText si sigues así (${formatKgDelta(projection.kgPerWeek)} por semana)."
    }
}

@Composable
private fun WeightRow(entry: WeightEntry, change: Double?, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formatDayMonthYear(entry.date), style = MaterialTheme.typography.bodyLarge)
            if (change != null) {
                Text(
                    formatKgDelta(change),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(formatKg(entry.weightKg), style = MaterialTheme.typography.titleMedium)
        IconButton(
            onClick = onEdit,
            modifier = Modifier.semantics { contentDescription = "Editar peso del ${entry.date}" },
        ) { Text("✎") }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.semantics { contentDescription = "Borrar peso del ${entry.date}" },
        ) { Text("✕", color = MaterialTheme.colorScheme.error) }
    }
}

/** Peso para el campo de texto: coma decimal y sin «,0» sobrante. */
internal fun formatKgInput(kg: Double): String = app.luxion.fitarchiveapp.ui.components.formatGrams(kg)
