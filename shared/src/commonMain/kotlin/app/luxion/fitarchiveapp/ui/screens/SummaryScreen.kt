package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.GoalStore
import app.luxion.fitarchiveapp.data.WeightStore
import app.luxion.fitarchiveapp.domain.PeriodRange
import app.luxion.fitarchiveapp.domain.PeriodSummaries
import app.luxion.fitarchiveapp.domain.PeriodSummary
import app.luxion.fitarchiveapp.domain.SummaryDay
import app.luxion.fitarchiveapp.domain.SummaryPeriod
import app.luxion.fitarchiveapp.domain.WeightProgress
import app.luxion.fitarchiveapp.domain.WeightVsCalories
import app.luxion.fitarchiveapp.ui.components.DailyKcalChart
import app.luxion.fitarchiveapp.ui.components.EmptyState
import app.luxion.fitarchiveapp.ui.components.EmptyStateAction
import app.luxion.fitarchiveapp.ui.components.EmptyStateTone
import app.luxion.fitarchiveapp.ui.components.StackedMacroBar
import app.luxion.fitarchiveapp.ui.components.WeightChart
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlinx.coroutines.CancellationException

/** Lee el diario, el objetivo y los pesajes del [range] y construye su resumen. Lanza si falla algún almacén. */
suspend fun loadPeriodSummary(
    range: PeriodRange,
    today: String,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    weightStore: WeightStore,
): PeriodSummary = PeriodSummaries.build(
    range = range,
    today = today,
    goalKcal = goalStore.get().kcal,
    entries = diaryStore.entriesBetween(range.start, range.end),
    weights = weightStore.all(),
)

private sealed interface SummaryLoad {
    data object Loading : SummaryLoad
    data object Failed : SummaryLoad
    data class Ready(val summary: PeriodSummary) : SummaryLoad
}

/**
 * Resumen semanal y mensual (docs/design.md §2.19): medias de kcal y macros, días cumplidos, racha, mejor y peor día
 * y peso frente a calorías. Todo se deriva de datos existentes; se abre desde el Diario.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    navigator: Navigator,
    diaryStore: DiaryStore,
    goalStore: GoalStore,
    weightStore: WeightStore,
    today: () -> String,
) {
    val todayIso = today()
    var period by remember { mutableStateOf(SummaryPeriod.WEEK) }
    // Periodos hacia atrás respecto al actual (0 = el actual).
    var back by remember { mutableIntStateOf(0) }
    var reload by remember { mutableIntStateOf(0) }
    var load by remember { mutableStateOf<SummaryLoad>(SummaryLoad.Loading) }
    val range = remember(period, back, todayIso) { PeriodRange.of(period, todayIso).shifted(-back) }

    LaunchedEffect(range, reload, diaryStore, goalStore, weightStore) {
        load = SummaryLoad.Loading
        load = try {
            SummaryLoad.Ready(loadPeriodSummary(range, todayIso, diaryStore, goalStore, weightStore))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SummaryLoad.Failed
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Resumen") },
                navigationIcon = {
                    IconButton(
                        onClick = { navigator.pop() },
                        modifier = Modifier.semantics { contentDescription = "Volver" },
                    ) { Text("‹", style = MaterialTheme.typography.titleLarge) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.bottomInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item("selector") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SummaryPeriod.entries.forEachIndexed { i, option ->
                        SegmentedButton(
                            selected = option == period,
                            onClick = { if (option != period) { period = option; back = 0 } },
                            shape = SegmentedButtonDefaults.itemShape(i, SummaryPeriod.entries.size),
                        ) { Text(if (option == SummaryPeriod.WEEK) "Semana" else "Mes") }
                    }
                }
            }
            item("navigator") {
                PeriodNavigator(
                    range = range,
                    canGoNext = back > 0,
                    onPrevious = { back++ },
                    onNext = { if (back > 0) back-- },
                    onCurrent = { back = 0 },
                )
            }
            when (val state = load) {
                // Como el Diario, no se pinta nada mientras se lee (son milisegundos): evita un parpadeo de indicador.
                SummaryLoad.Loading -> Unit
                SummaryLoad.Failed -> item("failed") {
                    EmptyState(
                        icon = "!",
                        title = "No se pudo cargar el resumen",
                        message = "No se pudieron leer los datos guardados.",
                        tone = EmptyStateTone.Error,
                        primaryAction = EmptyStateAction("Reintentar") { reload++ },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is SummaryLoad.Ready -> {
                    val summary = state.summary
                    if (summary.isEmpty) {
                        item("empty") {
                            EmptyState(
                                icon = "▤",
                                title = if (summary.isCurrent) "Aún no hay registros" else "Sin registros en este periodo",
                                message = if (summary.isCurrent) {
                                    "Cuando apuntes comidas en el Diario, aquí verás tus medias, tus días cumplidos y cómo evoluciona tu peso."
                                } else {
                                    "No apuntaste comidas en este periodo."
                                },
                                primaryAction = if (summary.isCurrent) EmptyStateAction("Ir al Diario") { navigator.pop() }
                                else EmptyStateAction("Ver el periodo actual") { back = 0 },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    } else {
                        item("averages") { AveragesCard(summary) }
                        item("days") { DaysCard(summary) }
                        if (summary.bestDay != null) item("best-worst") { BestWorstRow(summary) }
                        item("weight") { WeightCard(summary) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    range: PeriodRange,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit,
) {
    val noun = if (range.period == SummaryPeriod.WEEK) "semana" else "mes"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, modifier = Modifier.semantics { contentDescription = "Anterior: $noun" }) {
            Text("‹", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(periodLabel(range), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (canGoNext) {
                TextButton(onClick = onCurrent) { Text(if (range.period == SummaryPeriod.WEEK) "Esta semana" else "Este mes") }
            }
        }
        IconButton(
            onClick = onNext,
            enabled = canGoNext,
            modifier = Modifier.semantics { contentDescription = "Siguiente: $noun" },
        ) { Text("›", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun SummaryCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun AveragesCard(summary: PeriodSummary) {
    val averages = summary.averages ?: return
    SummaryCard("MEDIA DIARIA") {
        Column {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(formatKcal(averages.kcal), style = MaterialTheme.typography.displayMedium)
                Text(
                    "kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm),
                )
            }
            val goal = summary.goalKcal
            Text(
                (if (goal != null) "Objetivo ${formatKcal(goal.toDouble())} kcal · ${formatKcalDelta(averages.kcal - goal)} · " else "") +
                    "sobre ${daysText(summary.loggedDays)} con registro",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StackedMacroBar(averages.proteinG, averages.carbsG, averages.fatG)
    }
}

@Composable
private fun DaysCard(summary: PeriodSummary) {
    SummaryCard("DÍAS CUMPLIDOS") {
        val completed = summary.completedDays
        if (completed == null) {
            Text(
                "Fija un objetivo de kcal en Objetivos para ver qué días lo cumples (objetivo ±10 %).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("$completed", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "de ${summary.judgedDays}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.xs),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("🔥 ${daysText(summary.bestStreak)}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "mejor racha",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        DailyKcalChart(summary.days, summary.range.period, summary.goalKcal)
    }
}

@Composable
private fun BestWorstRow(summary: PeriodSummary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        DayHighlight("MEJOR DÍA", summary.bestDay, summary.goalKcal, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
        if (summary.worstDay != null) {
            DayHighlight("PEOR DÍA", summary.worstDay, summary.goalKcal, MaterialTheme.colorScheme.error, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayHighlight(title: String, day: SummaryDay?, goalKcal: Int?, accent: Color, modifier: Modifier) {
    val totals = day?.totals ?: return
    SummaryCard(title, modifier) {
        Column {
            Text(formatDayMonth(day.date), style = MaterialTheme.typography.titleMedium)
            Text("${formatKcal(totals.kcal)} kcal", style = MaterialTheme.typography.titleLarge, color = accent)
            if (goalKcal != null) {
                Text(
                    formatKcalDelta(totals.kcal - goalKcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeightCard(summary: PeriodSummary) {
    SummaryCard("PESO FRENTE A CALORÍAS") {
        val trend = summary.weight
        val chart = WeightProgress.chart(summary.weights)
        if (trend == null || chart == null) {
            Text(
                if (summary.weights.size == 1) "Solo hay un pesaje en este periodo. Con dos o más verás cómo cambia tu peso frente a lo que comes."
                else "Registra tu peso al menos dos veces en este periodo, en Mi peso, para compararlo con lo que comes.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(weightTrendText(trend), style = MaterialTheme.typography.bodyLarge)
            WeightChart(chart)
        }
    }
}

/** «Peso: −0,6 kg (de 78,4 a 77,8 kg) con una media de 1.980 kcal al día (−120 kcal frente al objetivo).» */
internal fun weightTrendText(trend: WeightVsCalories): String {
    val change = if (kotlin.math.abs(trend.deltaKg) < 0.05) "Peso estable" else "Peso: ${formatKgDelta(trend.deltaKg)}"
    val range = "de ${formatKg(trend.first.weightKg)} a ${formatKg(trend.last.weightKg)}"
    val versus = trend.kcalVsGoal?.let {
        if (kotlin.math.abs(it) < 0.5) " (en el objetivo)" else " (${formatKcalDelta(it)} frente al objetivo)"
    } ?: ""
    return "$change, $range, con una media de ${formatKcal(trend.avgKcal)} kcal al día$versus."
}
