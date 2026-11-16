package app.luxion.easyfoodapp.ui.screens

import app.luxion.easyfoodapp.domain.PeriodRange
import app.luxion.easyfoodapp.domain.SummaryPeriod
import app.luxion.easyfoodapp.ui.components.formatKcal

/**
 * Título del periodo: «9–15 nov 2026», «28 oct – 3 nov 2026», «28 dic 2026 – 3 ene 2027» o «Noviembre de 2026».
 */
fun periodLabel(range: PeriodRange): String = when (range.period) {
    SummaryPeriod.MONTH -> IsoDate.long(range.start).substringAfter(" de ").replaceFirstChar { it.uppercase() }
    SummaryPeriod.WEEK -> {
        val sameYear = range.start.take(4) == range.end.take(4)
        val sameMonth = sameYear && range.start.substring(5, 7) == range.end.substring(5, 7)
        when {
            sameMonth -> "${range.start.substring(8, 10).toInt()}–${formatDayMonthYear(range.end)}"
            sameYear -> "${formatDayMonth(range.start)} – ${formatDayMonthYear(range.end)}"
            else -> "${formatDayMonthYear(range.start)} – ${formatDayMonthYear(range.end)}"
        }
    }
}

/** «+120 kcal» · «−80 kcal» · «En el objetivo» (menos de 1 kcal de diferencia). */
fun formatKcalDelta(delta: Double): String = when {
    kotlin.math.abs(delta) < 0.5 -> "En el objetivo"
    delta > 0 -> "+${formatKcal(delta)} kcal"
    else -> "−${formatKcal(-delta)} kcal"
}

/** «1 día» · «5 días». */
fun daysText(n: Int): String = if (n == 1) "1 día" else "$n días"

/** «+12 g» · «−5 g» · «Igual» (menos de 0,5 g de diferencia). */
fun formatGramsDelta(delta: Double): String = when {
    kotlin.math.abs(delta) < 0.5 -> "Igual"
    delta > 0 -> "+${formatKcal(delta)} g"
    else -> "−${formatKcal(-delta)} g"
}

/** «+2 días» · «−1 día» · «Igual». */
fun formatDaysDelta(delta: Int): String = when {
    delta == 0 -> "Igual"
    delta > 0 -> "+${daysText(delta)}"
    else -> "−${daysText(-delta)}"
}
