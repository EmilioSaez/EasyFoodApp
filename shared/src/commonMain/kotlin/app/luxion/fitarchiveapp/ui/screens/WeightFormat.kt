package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.WeightProgress
import app.luxion.fitarchiveapp.domain.WeightRange
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import app.luxion.fitarchiveapp.ui.components.formatNumber
import kotlin.math.abs

private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** «4 nov» (o el texto tal cual si no es una fecha ISO). */
fun formatDayMonth(iso: String): String =
    if (isoDateToEpochDay(iso) == null) iso else "${iso.substring(8, 10).toInt()} ${MONTHS[iso.substring(5, 7).toInt() - 1]}"

/** «4 nov 2026». */
fun formatDayMonthYear(iso: String): String =
    if (isoDateToEpochDay(iso) == null) iso else "${formatDayMonth(iso)} ${iso.substring(0, 4)}"

/** «78,4 kg». */
fun formatKg(kg: Double): String = "${formatNumber(kg, 1)} kg"

/** «+0,4 kg» · «−1,2 kg» · «Sin cambios» (el signo menos tipográfico evita confundirlo con un guion). */
fun formatKgDelta(delta: Double): String = when {
    abs(delta) < 0.05 -> "Sin cambios"
    delta > 0 -> "+${formatNumber(delta, 1)} kg"
    else -> "−${formatNumber(-delta, 1)} kg"
}

/** Periodo de la gráfica de «Mi peso»: 1M = 30 días, 3M = 90 días, Todo = todo el historial. */
enum class WeightPeriod(val label: String, val range: WeightRange) {
    ONE_MONTH("1M", WeightRange.DAYS_30),
    THREE_MONTHS("3M", WeightRange.DAYS_90),
    ALL("Todo", WeightRange.ALL),
}

/** Registros del [period] terminados en [today] (inclusive), ordenados por fecha. */
fun weightEntriesIn(entries: List<WeightEntry>, period: WeightPeriod, today: String): List<WeightEntry> =
    WeightProgress.inRange(entries, period.range, today)
