package app.luxion.fitarchiveapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.domain.SummaryDay
import app.luxion.fitarchiveapp.domain.SummaryPeriod
import app.luxion.fitarchiveapp.ui.theme.Spacing
import kotlin.math.max

private val weekInitials = listOf("L", "M", "X", "J", "V", "S", "D")

/** Etiqueta bajo la barra de cada día: inicial en la semana; en el mes, el número de los días 1, 5, 10, 15, 20, 25 y 30. */
internal fun dayAxisLabel(period: SummaryPeriod, index: Int, date: String): String? = when (period) {
    SummaryPeriod.WEEK -> weekInitials[index % 7]
    SummaryPeriod.MONTH -> date.substring(8, 10).toInt().let { if (it == 1 || it % 5 == 0) it.toString() else null }
}

/**
 * Barras de kcal por día con la banda del objetivo (líneas discontinuas en objetivo −10 % y +10 %).
 * Las barras dentro del margen van en `tertiary`, las de fuera en `error` y las de hoy sin completar en `primary`;
 * sin objetivo, todas en `primary`. El color no va solo: bajo la gráfica hay una leyenda con texto.
 */
@Composable
fun DailyKcalChart(
    days: List<SummaryDay>,
    period: SummaryPeriod,
    goalKcal: Int?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val bandColor = colors.onSurfaceVariant
    val logged = days.count { it.totals != null }
    val description = "Kcal por día: $logged ${if (logged == 1) "día" else "días"} con registro" +
        (goalKcal?.let { ", objetivo ${formatKcal(it.toDouble())} kcal" } ?: "")

    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val bottomPad = 20.dp.toPx()
            val topPad = 6.dp.toPx()
            val plotH = size.height - bottomPad - topPad
            if (plotH <= 0f || days.isEmpty()) return@Canvas
            val maxKcal = max(
                days.maxOf { it.totals?.kcal ?: 0.0 },
                goalKcal?.let { DayStatus.upperBound(it) } ?: 0.0,
            ).coerceAtLeast(1.0) * 1.1
            fun y(kcal: Double) = topPad + plotH * (1f - (kcal / maxKcal).toFloat())

            val slot = size.width / days.size
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val baseline = topPad + plotH
            drawLine(colors.outlineVariant, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())

            if (goalKcal != null) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
                for (bound in listOf(DayStatus.lowerBound(goalKcal), DayStatus.upperBound(goalKcal))) {
                    drawLine(bandColor, Offset(0f, y(bound)), Offset(size.width, y(bound)), 1.dp.toPx(), pathEffect = dash)
                }
            }

            days.forEachIndexed { i, day ->
                val cx = slot * i + slot / 2
                val totals = day.totals
                if (totals != null) {
                    val color = when (day.status) {
                        DayStatus.COMPLETED -> colors.tertiary
                        DayStatus.MISSED -> colors.error
                        else -> colors.primary
                    }
                    val top = y(totals.kcal).coerceAtMost(baseline - 2.dp.toPx())
                    drawRoundRect(
                        color, Offset(cx - barW / 2, top), Size(barW, baseline - top),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                } else if (day.date <= days.last().date) {
                    // Sin registro: marca mínima en la base para que el hueco se lea como «día vacío».
                    drawLine(colors.outlineVariant, Offset(cx - barW / 4, baseline), Offset(cx + barW / 4, baseline), 2.dp.toPx())
                }
                dayAxisLabel(period, i, day.date)?.let { label ->
                    val text = measurer.measure(label, labelStyle)
                    drawText(text, topLeft = Offset(cx - text.size.width / 2f, baseline + 4.dp.toPx()))
                }
            }
        }
        if (goalKcal != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                LegendItem(colors.tertiary, "Dentro del margen")
                LegendItem(colors.error, "Fuera")
                Text("- - objetivo ±10 %", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MacroDot(color, size = 8.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
