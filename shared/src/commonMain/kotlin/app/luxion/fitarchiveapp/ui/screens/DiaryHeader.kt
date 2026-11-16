package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.components.MacroDot
import app.luxion.fitarchiveapp.ui.components.formatGrams
import app.luxion.fitarchiveapp.ui.theme.Spacing
import app.luxion.fitarchiveapp.ui.theme.easyFoodColors

@Composable
internal fun MacrosLine(state: DiaryUiState) {
    val colors = MaterialTheme.easyFoodColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        state.macros.forEach { macro ->
            val color = when (macro.label) {
                "Proteína" -> colors.heroProtein
                "Hidratos" -> colors.heroCarbs
                else -> colors.heroFat
            }
            MacroCell(macro.label, macro.consumedG, macro.goalG, color, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MacroCell(label: String, consumedG: Double, goalG: Double?, color: Color, modifier: Modifier) {
    val colors = MaterialTheme.easyFoodColors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroDot(color, size = 8.dp)
            Text(
                label, style = MaterialTheme.typography.labelMedium, color = colors.onHeroVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        Text(
            if (goalG != null) "${formatGrams(consumedG)} / ${formatGrams(goalG)} g" else "${formatGrams(consumedG)} g",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onHero,
            maxLines = 1,
        )
        if (goalG != null) {
            LinearProgressIndicator(
                progress = { if (goalG > 0) (consumedG / goalG).toFloat().coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = color,
                trackColor = colors.heroTrack,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
