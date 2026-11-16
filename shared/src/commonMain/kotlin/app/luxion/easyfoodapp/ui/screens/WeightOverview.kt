package app.luxion.easyfoodapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.luxion.easyfoodapp.domain.WeightProgress
import app.luxion.easyfoodapp.model.WeightEntry
import app.luxion.easyfoodapp.model.WeightGoal
import app.luxion.easyfoodapp.ui.components.WeightChart
import app.luxion.easyfoodapp.ui.theme.Spacing

/**
 * Peso actual con su variación, selector de periodo (1M/3M/Todo) y gráfica. Lo comparten «Tú» y «Mi peso».
 * Con [showGoal], si hay [goal], añade la barra de avance hacia el objetivo y la estimación de llegada.
 */
@Composable
fun WeightOverview(
    entries: List<WeightEntry>,
    loaded: Boolean,
    goal: WeightGoal?,
    todayIso: String,
    period: WeightPeriod,
    onPeriod: (WeightPeriod) -> Unit,
    modifier: Modifier = Modifier,
    showGoal: Boolean = false,
) {
    val visible = weightEntriesIn(entries, period, todayIso)
    val summary = WeightProgress.summary(visible)
    val chart = WeightProgress.chart(visible)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        when {
            !loaded -> Unit
            entries.isEmpty() -> Text(
                "Aún no has registrado tu peso. Toca «Registrar peso» para guardar el primero y ver cómo evoluciona.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            summary == null || chart == null -> Text(
                "No hay registros en este periodo. Prueba con un rango mayor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(formatKg(summary.latest.weightKg), style = MaterialTheme.typography.displaySmall)
                if (summary.hasChange) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                formatKgDelta(summary.deltaKg),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                            )
                        }
                        Text(
                            "desde el ${formatDayMonth(summary.first.date)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Un solo registro en este periodo (${formatDayMonth(summary.first.date)})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (showGoal && goal != null && entries.isNotEmpty()) {
            WeightProgress.goalProgress(entries, goal.targetKg, todayIso)?.let { progress ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text("Objetivo ${formatKg(goal.targetKg)}", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    Text(goalSummaryText(progress), style = MaterialTheme.typography.bodyMedium)
                    progress.projection?.let {
                        Text(projectionText(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            WeightPeriod.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = p == period,
                    onClick = { onPeriod(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, WeightPeriod.entries.size),
                ) { Text(p.label) }
            }
        }
        if (loaded && summary != null && chart != null) WeightChart(chart, goalKg = goal?.targetKg)
    }
}
