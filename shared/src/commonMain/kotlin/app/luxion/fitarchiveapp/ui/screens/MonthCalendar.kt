package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.domain.DaySummary

/** Un día del calendario mensual. [streak] = se cumplió la racha ese día (mismo criterio que la franja semanal). */
data class MonthDayUi(
    val date: String,
    val dayNumber: Int,
    val status: DayStatus,
    val selected: Boolean,
) {
    val streak: Boolean get() = status == DayStatus.COMPLETED
}

/** Mes mostrado: [leadingBlanks] huecos hasta el lunes (0..6) y luego los días del mes. */
data class MonthUi(val title: String, val leadingBlanks: Int, val days: List<MonthDayUi>)

/** Calendario del mes de [month] (cualquier fecha del mes) con el estado de cada día, calculado de lo ya guardado. */
fun buildMonth(
    month: String,
    selected: String,
    today: String,
    goalKcal: Int?,
    summaries: Map<String, DaySummary>,
): MonthUi {
    val start = IsoDate.monthStart(month)
    val leading = IsoDate.weekdayIndex(start)
    val days = (1..IsoDate.daysInMonth(start)).map { n ->
        val date = start.substring(0, 8) + n.toString().padStart(2, '0')
        MonthDayUi(date, n, DayStatus.of(date, today, goalKcal, summaries[date]), date == selected)
    }
    return MonthUi(IsoDate.monthTitle(start), leading, days)
}
