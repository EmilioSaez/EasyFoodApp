package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.domain.DaySummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MonthCalendarTest {
    private val today = "2026-11-16" // lunes

    @Test
    fun monthHelpers() {
        assertEquals("2026-11-01", IsoDate.monthStart("2026-11-16"))
        assertEquals("2026-12-01", IsoDate.shiftMonth("2026-11-16", 1))
        assertEquals("2027-01-01", IsoDate.shiftMonth("2026-11-16", 2))
        assertEquals("2026-10-01", IsoDate.shiftMonth("2026-11-30", -1))
        assertEquals("2025-12-01", IsoDate.shiftMonth("2026-01-15", -1))
        assertEquals(28, IsoDate.daysInMonth("2026-02-10"))
        assertEquals(29, IsoDate.daysInMonth("2028-02-10"))
        assertEquals("noviembre de 2026", IsoDate.monthTitle("2026-11-16"))
        assertEquals(6, IsoDate.weekdayIndex("2026-11-01")) // domingo
        assertEquals(0, IsoDate.weekdayIndex("2026-11-16")) // lunes
    }

    @Test
    fun monthLayoutHasLeadingBlanksAndAllDays() {
        val m = buildMonth("2026-11-16", "2026-11-16", today, 2000, emptyMap())
        assertEquals(6, m.leadingBlanks) // 1 nov 2026 es domingo
        assertEquals(30, m.days.size)
        assertEquals("2026-11-30", m.days.last().date)
        assertEquals(listOf("2026-11-16"), m.days.filter { it.selected }.map { it.date })
    }

    @Test
    fun streakIsOnlyOnCompletedDaysWithinMargin() {
        val summaries = mapOf(
            "2026-11-02" to DaySummary("2026-11-02", 2000.0), // dentro
            "2026-11-03" to DaySummary("2026-11-03", 1800.0), // límite inferior: dentro
            "2026-11-04" to DaySummary("2026-11-04", 2200.0), // límite superior: dentro
            "2026-11-05" to DaySummary("2026-11-05", 1700.0), // fuera
            "2026-11-06" to DaySummary("2026-11-06", 2300.0), // fuera
            "2026-11-16" to DaySummary("2026-11-16", 2100.0), // hoy cumplido
        )
        val byDate = buildMonth("2026-11-16", "2026-11-16", today, 2000, summaries).days.associateBy { it.date }
        assertTrue(byDate.getValue("2026-11-02").streak)
        assertTrue(byDate.getValue("2026-11-03").streak)
        assertTrue(byDate.getValue("2026-11-04").streak)
        assertFalse(byDate.getValue("2026-11-05").streak)
        assertFalse(byDate.getValue("2026-11-06").streak)
        assertFalse(byDate.getValue("2026-11-07").streak) // sin registro
        assertTrue(byDate.getValue("2026-11-16").streak)
        assertEquals(DayStatus.FUTURE, byDate.getValue("2026-11-20").status)
        assertFalse(byDate.getValue("2026-11-20").streak)
    }

    @Test
    fun noGoalMeansNoStreakDays() {
        val summaries = mapOf("2026-11-02" to DaySummary("2026-11-02", 2000.0))
        val m = buildMonth("2026-11-16", "2026-11-16", today, null, summaries)
        assertTrue(m.days.none { it.streak })
    }

    @Test
    fun futureMonthsHaveNoStreakAndSameDefinitionAsStrip() {
        val summaries = mapOf("2026-11-10" to DaySummary("2026-11-10", 2000.0))
        val month = buildMonth("2026-11-10", "2026-11-10", today, 2000, summaries)
        val week = buildWeek("2026-11-10", today, 2000, summaries)
        // Mismo estado en el calendario y en la franja semanal.
        week.forEach { w -> assertEquals(w.status, month.days.first { it.date == w.date }.status) }
        assertTrue(buildMonth("2027-01-01", "2026-11-16", today, 2000, summaries).days.none { it.streak })
    }
}
