package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.components.MacroDot
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.components.formatKcal
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

/**
 * Cabecera fija del Diario (docs/design.md §2.10): objetivo y cómo se va. Queda anclada bajo la barra de
 * fecha; la lista de comidas se desplaza por debajo. Compacta (~120 dp): macros siempre en una sola línea.
 */
@Composable
fun DiaryHeader(state: DiaryUiState, onSetGoal: () -> Unit, modifier: Modifier = Modifier) {
    val goal = state.goalKcal
    Card(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            if (goal != null) KcalProgress(state.consumedKcal, goal.toDouble()) else NoGoal(state.consumedKcal, onSetGoal)
            MacrosLine(state)
        }
    }
}

@Composable
private fun KcalProgress(consumedKcal: Double, goalKcal: Double) {
    val over = consumedKcal > goalKcal
    val remaining = if (over) consumedKcal - goalKcal else goalKcal - consumedKcal
    val error = MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            "${formatKcal(consumedKcal)} / ${formatKcal(goalKcal)} kcal",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (over) "${formatKcal(remaining)} kcal de más" else "${formatKcal(remaining)} kcal restantes",
            style = MaterialTheme.typography.labelLarge,
            color = if (over) error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    LinearProgressIndicator(
        progress = { if (goalKcal > 0) (consumedKcal / goalKcal).toFloat().coerceIn(0f, 1f) else 0f },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = if (over) error else MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

@Composable
private fun NoGoal(consumedKcal: Double, onSetGoal: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Sin objetivo diario", style = MaterialTheme.typography.titleMedium)
            Text(
                "${formatKcal(consumedKcal)} kcal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onSetGoal) { Text("Fijar objetivo") }
    }
}

@Composable
internal fun MacrosLine(state: DiaryUiState) {
    val colors = MaterialTheme.easyFoodColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        state.macros.forEach { macro ->
            val color = when (macro.label) {
                "Proteína" -> colors.macroProtein
                "Hidratos" -> colors.macroCarbs
                else -> colors.macroFat
            }
            MacroCell(macro.label, macro.consumedG, macro.goalG, color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroCell(label: String, consumedG: Double, goalG: Double?, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroDot(color, size = 8.dp)
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = Spacing.xs))
        }
        Text(
            if (goalG != null) "${formatGrams(consumedG)} / ${formatGrams(goalG)} g" else "${formatGrams(consumedG)} g",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (goalG != null) {
            LinearProgressIndicator(
                progress = { if (goalG > 0) (consumedG / goalG).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
