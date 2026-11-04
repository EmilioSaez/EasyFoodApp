package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * Anillo de calorías: progreso en `primary` y, si se supera el objetivo, arco completo en `error`
 * con «X kcal de más» en el centro.
 */
@Composable
fun CalorieRing(
    consumedKcal: Double,
    goalKcal: Double,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
) {
    val over = goalKcal > 0 && consumedKcal > goalKcal
    val progress = if (goalKcal > 0) (consumedKcal / goalKcal).toFloat().coerceIn(0f, 1f) else 0f
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val arc = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = stroke)
            if (progress > 0f) drawArc(arc, -90f, 360f * progress, false, Offset(inset, inset), arcSize, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val value = if (over) consumedKcal - goalKcal else (goalKcal - consumedKcal).coerceAtLeast(0.0)
            Text(
                formatKcal(value),
                style = MaterialTheme.typography.headlineSmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (over) "kcal de más" else "kcal restantes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun MacroDot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** Fila «Proteína · 68 / 130 g» con barra de 6 dp; el macro siempre lleva su texto además del color. */
@Composable
fun MacroProgressRow(
    label: String,
    consumedG: Double,
    goalG: Double,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MacroDot(color)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f).padding(start = Spacing.s),
            )
            Text(
                "${formatGrams(consumedG)} / ${formatGrams(goalG)} g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { if (goalG > 0) (consumedG / goalG).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** Resumen de una comida: «386 kcal · P 17 · H 62 · G 8». */
fun macroSummary(kcal: Double, proteinG: Double, carbsG: Double, fatG: Double): String =
    "${formatKcal(kcal)} kcal · P ${proteinG.roundToInt()} · H ${carbsG.roundToInt()} · G ${fatG.roundToInt()}"
