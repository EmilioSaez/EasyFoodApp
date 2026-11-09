package app.luxion.fitarchiveapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DayStatusTest {
    private val today = "2026-11-09"
    private fun s(date: String, kcal: Double) = DaySummary(date, kcal)
    private fun status(date: String, goal: Int?, kcal: Double?) =
        DayStatus.of(date, today, goal, kcal?.let { s(date, it) })

    @Test
    fun marginIsTenPercentAndBoundsAreInclusive() {
        assertEquals(0.10, DayStatus.MARGIN)
        assertEquals(1800.0, DayStatus.lowerBound(2000))
        assertEquals(2200.0, DayStatus.upperBound(2000), 1e-9)
        assertTrue(DayStatus.isWithinMargin(1800.0, 2000))
        assertTrue(DayStatus.isWithinMargin(2200.0, 2000))
        assertFalse(DayStatus.isWithinMargin(1799.0, 2000))
        assertFalse(DayStatus.isWithinMargin(2201.0, 2000))
    }

    @Test
    fun pastDayWithinMarginIsCompleted() = assertEquals(DayStatus.COMPLETED, status("2026-11-05", 2000, 2100.0))

    @Test
    fun pastDayOutsideMarginOrEmptyIsMissed() {
        assertEquals(DayStatus.MISSED, status("2026-11-05", 2000, 1500.0))
        assertEquals(DayStatus.MISSED, status("2026-11-05", 2000, 2500.0))
        assertEquals(DayStatus.MISSED, status("2026-11-05", 2000, null))
    }

    @Test
    fun todayIsPendingUntilCompletedAndNeverMissed() {
        assertEquals(DayStatus.TODAY_PENDING, status(today, 2000, null))
        assertEquals(DayStatus.TODAY_PENDING, status(today, 2000, 500.0))
        assertEquals(DayStatus.TODAY_PENDING, status(today, 2000, 3000.0))
        assertEquals(DayStatus.COMPLETED, status(today, 2000, 1900.0))
    }

    @Test
    fun futureIsNeutral() {
        assertEquals(DayStatus.FUTURE, status("2026-11-10", 2000, null))
        assertEquals(DayStatus.FUTURE, status("2026-11-10", null, null))
    }

    @Test
    fun withoutGoalDaysAreNotEvaluated() {
        assertEquals(DayStatus.LOGGED_NO_GOAL, status("2026-11-05", null, 1234.0))
        assertEquals(DayStatus.EMPTY_NO_GOAL, status("2026-11-05", null, null))
        assertEquals(DayStatus.LOGGED_NO_GOAL, status(today, null, 100.0))
        assertEquals(DayStatus.EMPTY_NO_GOAL, status(today, null, null))
    }

    private fun history(vararg dates: String) = dates.associateWith { s(it, 2000.0) }

    @Test
    fun streakCountsConsecutiveCompletedDaysEndingToday() {
        assertEquals(3, DayStatus.streak(today, 2000, history("2026-11-07", "2026-11-08", today)))
    }

    @Test
    fun streakIsNotBrokenWhileTodayIsPending() {
        assertEquals(2, DayStatus.streak(today, 2000, history("2026-11-07", "2026-11-08")))
        assertEquals(2, DayStatus.streak(today, 2000, history("2026-11-07", "2026-11-08") + (today to s(today, 300.0))))
    }

    @Test
    fun streakBreaksAtAMissedDay() {
        assertEquals(1, DayStatus.streak(today, 2000, history("2026-11-06", "2026-11-08")))
        assertEquals(1, DayStatus.streak(today, 2000, history("2026-11-05", "2026-11-06", "2026-11-08") + ("2026-11-07" to s("2026-11-07", 900.0))))
    }

    @Test
    fun streakIsZeroWithoutGoalOrCompletedDays() {
        assertEquals(0, DayStatus.streak(today, null, history("2026-11-08", today)))
        assertEquals(0, DayStatus.streak(today, 2000, emptyMap()))
        assertEquals(0, DayStatus.streak(today, 2000, history("2026-11-07")))
    }

    @Test
    fun streakCrossesMonthAndYearBoundaries() {
        assertEquals(3, DayStatus.streak("2027-01-01", 2000, history("2026-12-30", "2026-12-31", "2027-01-01")))
    }

    @Test
    fun streakFollowsTheMarginConstant() {
        val edge = mapOf("2026-11-08" to s("2026-11-08", DayStatus.upperBound(2000)), today to s(today, DayStatus.lowerBound(2000)))
        assertEquals(2, DayStatus.streak(today, 2000, edge))
    }
}
