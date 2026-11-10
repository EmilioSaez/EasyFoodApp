package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.PeriodRange
import app.luxion.fitarchiveapp.domain.SummaryPeriod
import app.luxion.fitarchiveapp.ui.components.dayAxisLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SummaryFormatTest {
    private fun week(date: String) = PeriodRange.of(SummaryPeriod.WEEK, date)

    @Test
    fun weekLabelsCollapseSharedMonthAndYear() {
        assertEquals("9–15 nov 2026", periodLabel(week("2026-11-11")))
        assertEquals("26 oct – 1 nov 2026", periodLabel(week("2026-10-28")))
        assertEquals("28 dic 2026 – 3 ene 2027", periodLabel(week("2026-12-31")))
    }

    @Test
    fun monthLabelIsCapitalised() {
        assertEquals("Noviembre de 2026", periodLabel(PeriodRange.of(SummaryPeriod.MONTH, "2026-11-11")))
    }

    @Test
    fun kcalDeltaUsesATypographicMinus() {
        assertEquals("+120 kcal", formatKcalDelta(120.0))
        assertEquals("−80 kcal", formatKcalDelta(-80.0))
        assertEquals("En el objetivo", formatKcalDelta(0.2))
    }

    @Test
    fun daysTextPluralises() {
        assertEquals("1 día", daysText(1))
        assertEquals("3 días", daysText(3))
    }

    @Test
    fun axisLabelsAreInitialsInAWeekAndSparseNumbersInAMonth() {
        assertEquals("L", dayAxisLabel(SummaryPeriod.WEEK, 0, "2026-11-09"))
        assertEquals("D", dayAxisLabel(SummaryPeriod.WEEK, 6, "2026-11-15"))
        assertEquals("1", dayAxisLabel(SummaryPeriod.MONTH, 0, "2026-11-01"))
        assertEquals("15", dayAxisLabel(SummaryPeriod.MONTH, 14, "2026-11-15"))
        assertNull(dayAxisLabel(SummaryPeriod.MONTH, 2, "2026-11-03"))
    }
}
