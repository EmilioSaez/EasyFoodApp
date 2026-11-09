package app.luxion.fitarchiveapp.ui.screens

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
