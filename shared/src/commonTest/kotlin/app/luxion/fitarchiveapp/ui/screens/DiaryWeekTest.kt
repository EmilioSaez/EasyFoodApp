package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.domain.DayStatus
import app.luxion.fitarchiveapp.domain.DaySummary
import app.luxion.fitarchiveapp.domain.MealPlan
import app.luxion.fitarchiveapp.model.DailyGoals
import kotlin.test.Test
import kotlin.test.assertEquals

class DiaryWeekTest {
    // 2026-11-09 es lunes.
    private val today = "2026-11-09"

    @Test
    fun weekStartIsTheMondayOfTheWeek() {
        assertEquals("2026-11-09", IsoDate.weekStart("2026-11-09"))
        assertEquals("2026-11-09", IsoDate.weekStart("2026-11-15"))
        assertEquals("2026-11-02", IsoDate.weekStart("2026-11-08"))
        assertEquals("2026-12-28", IsoDate.weekStart("2027-01-01"))
    }

    @Test
    fun weekHasSevenDaysFromMondayToSundayWithInitialsAndNumbers() {
        val week = buildWeek("2026-11-11", today, 2000, emptyMap())
        assertEquals(listOf("L", "M", "X", "J", "V", "S", "D"), week.map { it.initial })
        assertEquals(listOf(9, 10, 11, 12, 13, 14, 15), week.map { it.dayNumber })
        assertEquals(listOf(false, false, true, false, false, false, false), week.map { it.selected })
    }

    @Test
    fun weekStatusesFollowTheDayRules() {
        val summaries = mapOf(
            "2026-11-02" to DaySummary("2026-11-02", 2000.0), // semana anterior: no se ve
            "2026-11-09" to DaySummary("2026-11-09", 1000.0),
        )
        val week = buildWeek("2026-11-12", "2026-11-11", 2000, summaries)
        assertEquals(
            listOf(
                DayStatus.MISSED, // lunes 9: registro fuera de margen
                DayStatus.MISSED, // martes 10: sin registro
                DayStatus.TODAY_PENDING, // miércoles 11 = hoy
                DayStatus.FUTURE, DayStatus.FUTURE, DayStatus.FUTURE, DayStatus.FUTURE,
            ),
            week.map { it.status },
        )
    }

    @Test
    fun weekWithoutGoalIsNotEvaluated() {
        val week = buildWeek("2026-11-09", "2026-11-11", null, mapOf("2026-11-10" to DaySummary("2026-11-10", 800.0)))
        assertEquals(DayStatus.EMPTY_NO_GOAL, week[0].status)
        assertEquals(DayStatus.LOGGED_NO_GOAL, week[1].status)
        assertEquals(DayStatus.EMPTY_NO_GOAL, week[2].status)
        assertEquals(DayStatus.FUTURE, week[3].status)
    }

    @Test
    fun stateCarriesWeekStreakAndToday() {
        val summaries = listOf(DaySummary("2026-11-07", 2000.0), DaySummary("2026-11-08", 1900.0))
        val state = buildDiaryUiState(today, emptyList(), DailyGoals(kcal = 2000), today = today, summaries = summaries)
        assertEquals(2, state.streak)
        assertEquals(today, state.today)
        assertEquals(7, state.week.size)
        assertEquals(DayStatus.TODAY_PENDING, state.week.first { it.selected }.status)
    }

    @Test
    fun dayWithoutEntriesStillHasEveryMealOfThePresetWithZeroTotals() {
        for (count in MealPlan.options) {
            val state = buildDiaryUiState(today, emptyList(), DailyGoals(kcal = 2000), mealsPerDay = count, today = today)
            assertEquals(MealPlan.preset(count), state.meals.map { it.meal })
            assertEquals(true, state.meals.all { it.kcal == 0.0 && it.entries.isEmpty() })
        }
    }
}
