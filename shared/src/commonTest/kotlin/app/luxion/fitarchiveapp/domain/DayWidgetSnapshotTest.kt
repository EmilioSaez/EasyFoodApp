package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DayWidgetSnapshotTest {
    /** [grams] g de un alimento de 100 kcal y macros 10/20/5 por 100 g. */
    private fun entry(grams: Double, kcal: Double? = 100.0) = DiaryEntry(
        id = 1, date = "2026-11-16", meal = MealType.LUNCH, barcode = null, name = "x",
        quantity = Quantity(grams, QuantityUnit.GRAMS), servingSizeG = null,
        nutrients = Nutrients(kcal, 10.0, 20.0, 5.0), createdAtMillis = 0,
    )

    @Test
    fun remainingIsGoalMinusEatenAndSumsMacros() {
        val s = DayWidgetSnapshot.of(listOf(entry(200.0), entry(50.0)), DailyGoals(2000, 120, 250, 70), 750, 2000)
        assertEquals(250, s.eatenKcal)
        assertEquals(1750, s.remainingKcal)
        assertEquals(MacroProgress(25, 120), s.protein)
        assertEquals(MacroProgress(50, 250), s.carbs)
        assertEquals(MacroProgress(13, 70), s.fat) // 12,5 redondea hacia arriba
        assertEquals(750, s.waterMl)
    }

    @Test
    fun overTheGoalGivesNegativeRemaining() {
        val s = DayWidgetSnapshot.of(listOf(entry(1500.0)), DailyGoals(kcal = 1000), 0, 2000)
        assertEquals(-500, s.remainingKcal)
    }

    @Test
    fun withoutGoalThereIsNoRemaining() {
        val s = DayWidgetSnapshot.of(listOf(entry(100.0)), DailyGoals(), 0, 2000)
        assertNull(s.remainingKcal)
        assertEquals(100, s.eatenKcal)
        assertNull(s.protein.goalG)
    }

    @Test
    fun emptyDayAndUnknownKcalCountAsZero() {
        assertEquals(0, DayWidgetSnapshot.of(emptyList(), DailyGoals(1800), 0, 2000).eatenKcal)
        assertEquals(0, DayWidgetSnapshot.of(listOf(entry(100.0, kcal = null)), DailyGoals(1800), 0, 2000).eatenKcal)
    }
}
