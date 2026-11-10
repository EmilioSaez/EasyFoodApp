package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.WeightEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodSummaryTest {
    // 2026-11-11 es miércoles; la semana va del 9 (lunes) al 15 (domingo).
    private val today = "2026-11-11"
    private val week = PeriodRange.of(SummaryPeriod.WEEK, today)

    private var nextId = 1L

    /** Entrada en gramos de un alimento de [kcal] kcal/100 g y macros 10/20/5 por 100 g. */
    private fun entry(date: String, kcal: Double?, grams: Double = 100.0, serving: Boolean = false) = DiaryEntry(
        id = nextId++, date = date, meal = MealType.LUNCH, barcode = null, name = "x",
        quantity = Quantity(grams, if (serving) QuantityUnit.SERVINGS else QuantityUnit.GRAMS),
        servingSizeG = null, nutrients = Nutrients(kcal, 10.0, 20.0, 5.0), createdAtMillis = 0,
    )

    /** Entrada que suma exactamente [kcal] kcal (alimento de 100 kcal/100 g, [kcal] gramos). */
    private fun day(date: String, kcal: Double) = entry(date, 100.0, grams = kcal)

    private fun weight(date: String, kg: Double) = WeightEntry(date, kg)

    @Test
    fun weekRangeGoesFromMondayToSunday() {
        assertEquals(PeriodRange(SummaryPeriod.WEEK, "2026-11-09", "2026-11-15"), week)
        assertEquals(7, week.dayCount)
        assertEquals(PeriodRange(SummaryPeriod.WEEK, "2026-11-02", "2026-11-08"), week.shifted(-1))
        assertEquals(PeriodRange(SummaryPeriod.WEEK, "2026-11-16", "2026-11-22"), week.shifted(1))
    }

    @Test
    fun monthRangeHandlesLengthsLeapYearsAndYearBoundaries() {
        val nov = PeriodRange.of(SummaryPeriod.MONTH, "2026-11-20")
        assertEquals(PeriodRange(SummaryPeriod.MONTH, "2026-11-01", "2026-11-30"), nov)
        assertEquals(PeriodRange(SummaryPeriod.MONTH, "2026-12-01", "2026-12-31"), nov.shifted(1))
        assertEquals(PeriodRange(SummaryPeriod.MONTH, "2027-01-01", "2027-01-31"), nov.shifted(2))
        assertEquals(PeriodRange(SummaryPeriod.MONTH, "2026-01-01", "2026-01-31"), nov.shifted(-10))
        assertEquals(PeriodRange(SummaryPeriod.MONTH, "2025-12-01", "2025-12-31"), nov.shifted(-11))
        assertEquals("2028-02-29", PeriodRange.of(SummaryPeriod.MONTH, "2028-02-10").end)
        assertEquals(28, PeriodRange.of(SummaryPeriod.MONTH, "2027-02-10").dayCount)
    }

    @Test
    fun weekCrossingAMonthBoundaryKeepsAllSevenDays() {
        val range = PeriodRange.of(SummaryPeriod.WEEK, "2026-12-31")
        assertEquals("2026-12-28", range.start)
        assertEquals("2027-01-03", range.end)
        assertEquals(7, range.dayCount)
    }

    @Test
    fun emptyPeriodHasNoAveragesAndIsEmpty() {
        val s = PeriodSummaries.build(week, today, 2000, emptyList(), emptyList())
        assertTrue(s.isEmpty)
        assertNull(s.averages)
        assertNull(s.bestDay)
        assertNull(s.worstDay)
        assertEquals(0, s.completedDays)
        assertEquals(0, s.bestStreak)
        assertTrue(s.isCurrent)
    }

    @Test
    fun averagesAreOverLoggedDaysOnly() {
        val entries = listOf(
            entry("2026-11-09", 200.0, grams = 1000.0), // 2000 kcal; 100/200/50 g
            entry("2026-11-10", 100.0, grams = 1000.0), // 1000 kcal
            entry("2026-11-10", 100.0, grams = 1000.0), // +1000 = 2000 kcal; 200/400/100 g
        )
        val s = PeriodSummaries.build(week, today, 2000, entries, emptyList())
        assertEquals(2, s.loggedDays)
        val a = assertNotNull(s.averages)
        assertEquals(2000.0, a.kcal, 1e-9)
        assertEquals(150.0, a.proteinG, 1e-9)
        assertEquals(300.0, a.carbsG, 1e-9)
        assertEquals(75.0, a.fatG, 1e-9)
    }

    @Test
    fun entriesWithoutDataOrServingSizeCountZeroButMarkTheDayAsLogged() {
        val s = PeriodSummaries.build(
            week, today, 2000,
            listOf(entry("2026-11-09", null), entry("2026-11-10", 100.0, grams = 1.0, serving = true)),
            emptyList(),
        )
        assertEquals(2, s.loggedDays)
        assertEquals(0.0, s.averages!!.kcal, 1e-9)
    }

    @Test
    fun entriesOutsideThePeriodAndFutureDaysAreIgnored() {
        val s = PeriodSummaries.build(
            week, today, 2000,
            listOf(entry("2026-11-08", 100.0), entry("2026-11-13", 100.0), entry("2026-11-09", 100.0)),
            emptyList(),
        )
        assertEquals(1, s.loggedDays)
    }

    @Test
    fun completedDaysUseTheTenPercentMarginAndTodayIsNotJudgedUntilCompleted() {
        val s = PeriodSummaries.build(
            week, today, 2000,
            listOf(
                day("2026-11-09", 2200.0), // límite superior: cumplido
                day("2026-11-10", 2201.0), // fuera por 1 kcal: no cumplido
                day("2026-11-11", 1500.0), // hoy, sin cumplir: no se juzga
            ),
            emptyList(),
        )
        assertEquals(1, s.completedDays)
        assertEquals(2, s.judgedDays)
    }

    @Test
    fun withoutGoalThereAreNoCompletedDaysNorBestAndWorst() {
        val s = PeriodSummaries.build(week, today, null, listOf(day("2026-11-09", 500.0)), emptyList())
        assertNull(s.completedDays)
        assertNull(s.bestDay)
        assertNull(s.worstDay)
        assertEquals(1, s.loggedDays)
    }

    @Test
    fun bestStreakIsTheLongestRunOfCompletedDaysInThePeriod() {
        val s = PeriodSummaries.build(
            week, "2026-11-20", 2000,
            listOf(
                day("2026-11-09", 2000.0), day("2026-11-10", 1900.0), // 2 seguidos
                day("2026-11-11", 500.0), // rompe la racha
                day("2026-11-12", 2000.0), day("2026-11-13", 2100.0), day("2026-11-14", 1850.0), // 3 seguidos
                // domingo 15 sin registro
            ),
            emptyList(),
        )
        assertEquals(5, s.completedDays)
        assertEquals(3, s.bestStreak)
        assertEquals(7, s.judgedDays)
    }

    @Test
    fun bestAndWorstDayAreTheClosestAndFarthestFromTheGoal() {
        val s = PeriodSummaries.build(
            week, "2026-11-15", 2000,
            listOf(day("2026-11-09", 2050.0), day("2026-11-10", 1990.0), day("2026-11-11", 3000.0), day("2026-11-12", 1200.0)),
            emptyList(),
        )
        assertEquals("2026-11-10", s.bestDay!!.date)
        assertEquals("2026-11-11", s.worstDay!!.date)
    }

    @Test
    fun aSingleJudgedDayIsTheBestDayAndThereIsNoWorstDay() {
        val s = PeriodSummaries.build(week, today, 2000, listOf(day("2026-11-09", 2000.0)), emptyList())
        assertEquals("2026-11-09", s.bestDay!!.date)
        assertNull(s.worstDay)
    }

    @Test
    fun todayPendingNeverCountsAsWorstDay() {
        val s = PeriodSummaries.build(week, today, 2000, listOf(day("2026-11-09", 1900.0), day("2026-11-11", 100.0)), emptyList())
        assertEquals("2026-11-09", s.bestDay!!.date)
        assertNull(s.worstDay)
    }

    @Test
    fun weightTrendNeedsTwoWeighInsInThePeriodAndComparesWithTheGoal() {
        val entries = listOf(day("2026-11-09", 1800.0), day("2026-11-10", 1800.0))
        val one = PeriodSummaries.build(week, today, 2000, entries, listOf(weight("2026-11-09", 80.0), weight("2026-11-02", 81.0)))
        assertNull(one.weight)

        val two = PeriodSummaries.build(
            week, today, 2000, entries,
            listOf(weight("2026-11-02", 90.0), weight("2026-11-10", 79.4), weight("2026-11-09", 80.0)),
        )
        val w = assertNotNull(two.weight)
        assertEquals(-0.6, w.deltaKg, 1e-9)
        assertEquals(1800.0, w.avgKcal, 1e-9)
        assertEquals(-200.0, w.kcalVsGoal!!, 1e-9)
        assertEquals(listOf("2026-11-09", "2026-11-10"), two.weights.map { it.date })
    }

    @Test
    fun weightTrendIsAbsentWithoutLoggedFood() {
        val s = PeriodSummaries.build(week, today, 2000, emptyList(), listOf(weight("2026-11-09", 80.0), weight("2026-11-10", 79.0)))
        assertNull(s.weight)
    }

    @Test
    fun monthSummaryCoversEveryDayOfTheMonth() {
        val month = PeriodRange.of(SummaryPeriod.MONTH, today)
        val s = PeriodSummaries.build(month, today, 2000, listOf(day("2026-11-01", 2000.0)), emptyList())
        assertEquals(30, s.days.size)
        assertEquals(DayStatus.COMPLETED, s.days.first().status)
        assertEquals(DayStatus.FUTURE, s.days.last().status)
        assertEquals(1, s.completedDays)
        // Del 1 al 10 están cerrados; hoy (11) sin completar no se juzga.
        assertEquals(10, s.judgedDays)
    }

    @Test
    fun pastPeriodIsNotCurrent() {
        val s = PeriodSummaries.build(week.shifted(-1), today, 2000, emptyList(), emptyList())
        assertTrue(!s.isCurrent)
    }
}
