package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.WeightChartModel
import app.luxion.fitarchiveapp.ui.screens.formatDayMonth
import app.luxion.fitarchiveapp.ui.screens.formatKg

/**
 * Gráfica de línea del peso dibujada con [Canvas] (sin librerías: funciona igual en Android, escritorio e iOS).
 * Cada registro tiene su punto; el último se destaca (relleno y mayor) y la línea los une.
 */
@Composable
fun WeightChart(model: WeightChartModel, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = label)
    val first = model.points.first().entry
    val last = model.points.last().entry
    val description = "Gráfica de peso: ${model.points.size} " +
        (if (model.points.size == 1) "registro" else "registros") +
        ", de ${formatDayMonth(first.date)} a ${formatDayMonth(last.date)}, " +
        "último ${formatKg(last.weightKg)}"

    Canvas(modifier.fillMaxWidth().height(200.dp).semantics { contentDescription = description }) {
        val tickLabels: List<Pair<Double, TextLayoutResult>> =
            model.ticks.map { it to measurer.measure(formatNumber(it, if (it % 1.0 == 0.0) 0 else 1), labelStyle) }
        val leftPad = (tickLabels.maxOf { it.second.size.width } + 8.dp.toPx())
        val rightPad = 12.dp.toPx()
        val topPad = 10.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        if (plotW <= 0f || plotH <= 0f) return@Canvas

        fun px(x: Float) = leftPad + x * plotW
        fun py(y: Float) = topPad + (1f - y) * plotH

        // Rejilla y etiquetas del eje Y.
        tickLabels.forEach { (value, text) ->
            val y = py(model.yFraction(value))
            drawLine(grid, Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1.dp.toPx())
            drawText(text, topLeft = Offset(leftPad - 8.dp.toPx() - text.size.width, y - text.size.height / 2f))
        }

        // Etiquetas del eje X: primera y última fecha (una sola si solo hay un día).
        val dateLabels = if (model.points.size == 1 || first.date == last.date) listOf(0.5f to first.date)
        else listOf(0f to first.date, 1f to last.date)
        dateLabels.forEach { (x, date) ->
            val text = measurer.measure(formatDayMonth(date), labelStyle)
            val left = (px(x) - text.size.width * (if (x == 0f) 0f else if (x == 1f) 1f else 0.5f))
                .coerceIn(0f, size.width - text.size.width)
            drawText(text, topLeft = Offset(left, size.height - bottomPad + 6.dp.toPx()))
        }

        val pts = model.points.map { Offset(px(it.x), py(it.y)) }
        if (pts.size > 1) {
            val path = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        // Un punto por registro (pequeño); el último, destacado: relleno y mayor.
        pts.dropLast(1).forEach { drawCircle(line, 3.dp.toPx(), it) }
        drawCircle(line, 6.dp.toPx(), pts.last())
    }
}
