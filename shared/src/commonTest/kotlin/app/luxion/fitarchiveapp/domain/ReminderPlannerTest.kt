package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.WeightEntry
import app.luxion.fitarchiveapp.model.isoDateToEpochDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderPlannerTest {
    // 2026-11-10 es martes.
    private val today = "2026-11-10"
    private val on = ReminderSettings(enabled = true, weight = false)

    private fun ctx(
        now: Int = 0,
        logged: Set<MealType> = emptySet(),
        weights: List<WeightEntry> = emptyList(),
        water: Int = 0,
        goal: Int = 2000,
    ) = ReminderContext(today, now, 4, logged, weights, water, goal)

    @Test
    fun weekdayIsIso() {
        assertEquals(2, ReminderPlanner.isoWeekday(isoDateToEpochDay(today)!!))
        assertEquals(4, ReminderPlanner.isoWeekday(0)) // 1970-01-01, jueves
        assertEquals(7, ReminderPlanner.isoWeekday(isoDateToEpochDay("2026-11-15")!!))
        assertEquals(3, ReminderPlanner.isoWeekday(-1)) // 1969-12-31, miércoles
    }

    @Test
    fun disabledPlansNothing() {
        assertTrue(ReminderPlanner.plan(on.copy(enabled = false), ctx()).isEmpty())
    }

    @Test
    fun plansOneReminderPerPresetMealAndDay() {
        val plan = ReminderPlanner.plan(on, ctx())
        assertEquals(4 * ReminderPlanner.HORIZON_DAYS, plan.size)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
        assertTrue(plan.all { it.kind == ReminderKind.MEAL })
    }

    @Test
    fun skipsMealsAlreadyLoggedAndHoursAlreadyPassedToday() {
        val now = ReminderSettings.defaultMealMinutes(MealType.LUNCH) + 1
        val plan = ReminderPlanner.plan(on, ctx(now = now, logged = setOf(MealType.SNACK)))
        val todays = plan.filter { it.date == today }
        // Desayuno y comida ya pasaron, merienda registrada: solo queda la cena.
        assertEquals(listOf(ReminderSettings.defaultMealMinutes(MealType.DINNER)), todays.map { it.minutes })
        // Mañana avisa de todo aunque hoy se haya registrado.
        assertEquals(4, plan.count { it.date == "2026-11-11" })
    }

    @Test
    fun respectsEditedHoursAndDays() {
        val settings = on.copy(mealTimes = mapOf(MealType.BREAKFAST to 7 * 60), mealDays = setOf(2, 3))
        val plan = ReminderPlanner.plan(settings, ctx())
        assertEquals(setOf("2026-11-10", "2026-11-11"), plan.map { it.date }.toSet())
        assertEquals(7 * 60, plan.first { it.date == today && it.title.contains("desayuno") }.minutes)
    }

    @Test
    fun weightRemindsOnItsWeekdayOnly() {
        val plan = ReminderPlanner.plan(on.copy(meals = false, weight = true, weightDay = 5, weightMinutes = 600), ctx())
        assertEquals(listOf("2026-11-13"), plan.map { it.date })
        assertEquals(ReminderKind.WEIGHT, plan.single().kind)
    }

    @Test
    fun weightSkippedIfAlreadyWeighedThatWeek() {
        val settings = on.copy(meals = false, weight = true, weightDay = 5)
        val weighed = listOf(WeightEntry("2026-11-09", 70.0)) // lunes de la misma semana
        assertTrue(ReminderPlanner.plan(settings, ctx(weights = weighed)).isEmpty())
        // Un peso de la semana anterior no cuenta.
        val last = listOf(WeightEntry("2026-11-08", 70.0))
        assertEquals(1, ReminderPlanner.plan(settings, ctx(weights = last)).size)
    }

    @Test
    fun waterSkippedTodayWhenGoalReached() {
        val settings = on.copy(meals = false, water = true, waterMinutes = 20 * 60)
        val reached = ReminderPlanner.plan(settings, ctx(water = 2500))
        assertNull(reached.firstOrNull { it.date == today })
        assertEquals(ReminderPlanner.HORIZON_DAYS - 1, reached.size)
        assertEquals(ReminderPlanner.HORIZON_DAYS, ReminderPlanner.plan(settings, ctx(water = 100)).size)
    }

    @Test
    fun invalidTodayPlansNothing() {
        assertTrue(ReminderPlanner.plan(on, ReminderContext("hoy", 0, 4)).isEmpty())
    }

    @Test
    fun settingsRoundTripAndSanitize() {
        val s = ReminderSettings(enabled = true, mealTimes = mapOf(MealType.DINNER to 1300), mealDays = setOf(1, 9), weightDay = 0)
        val back = ReminderSettings.fromJson(s.toJson())!!
        assertEquals(1300, back.mealTimes[MealType.DINNER])
        val clean = ReminderSettings(mealTimes = mapOf(MealType.DINNER to 5000), mealDays = setOf(1, 9), weightDay = 0).sanitized()
        assertEquals(ReminderSettings.MAX_MINUTE, clean.mealTimes[MealType.DINNER])
        assertEquals(setOf(1), clean.mealDays)
        assertEquals(ReminderSettings.DEFAULT_WEIGHT_DAY, clean.weightDay)
        assertNull(ReminderSettings.fromJson("{no es json"))
        assertNull(ReminderSettings.fromJson(null))
    }
}
