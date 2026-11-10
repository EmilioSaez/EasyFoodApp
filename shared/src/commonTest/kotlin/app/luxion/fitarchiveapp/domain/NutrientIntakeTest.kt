package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.screens.cellText
import app.luxion.fitarchiveapp.ui.screens.valueText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NutrientIntakeTest {
    private fun entry(id: Long, n: Nutrients, q: Quantity = Quantity(100.0, QuantityUnit.GRAMS), serving: Double? = null) =
        DiaryEntry(id, "2026-11-04", MealType.LUNCH, null, "A$id", q, serving, n, id)

    private fun List<NutrientIntake>.of(label: String) = first { it.label == label }

    @Test
    fun sumsKnownValuesScaledByQuantity() {
        val full = Nutrients(100.0, 1.0, 1.0, 1.0, saturatedFatG = 2.0, sugarsG = 10.0, fiberG = 5.0, saltG = 1.0)
        val r = IntakeGuidelines.intake(listOf(entry(1, full), entry(2, full, Quantity(50.0, QuantityUnit.GRAMS))), 2000)
        assertEquals(7.5, r.of("Fibra").totalG, 1e-9)
        assertEquals(1.5, r.of("Sal").totalG, 1e-9)
        assertFalse(r.of("Fibra").incomplete)
        assertTrue(r.of("Fibra").hasData)
    }

    @Test
    fun missingDataIsIncompleteNotSilentZero() {
        val full = Nutrients(100.0, 1.0, 1.0, 1.0, fiberG = 4.0)
        val r = IntakeGuidelines.intake(listOf(entry(1, full), entry(2, Nutrients(100.0, 1.0, 1.0, 1.0))), null)
        assertEquals(4.0, r.of("Fibra").totalG, 1e-9)
        assertTrue(r.of("Fibra").incomplete)
        assertTrue(r.of("Fibra").valueText().endsWith("incompleto"))
    }

    @Test
    fun noDataAtAllHasNoTotal() {
        val r = IntakeGuidelines.intake(listOf(entry(1, Nutrients(100.0, 1.0, 1.0, 1.0))), null)
        assertFalse(r.of("Sal").hasData)
        assertTrue(r.of("Sal").valueText().startsWith("sin datos"))
    }

    @Test
    fun servingsWithoutSizeCountAsIncomplete() {
        val n = Nutrients(100.0, 1.0, 1.0, 1.0, saltG = 1.0)
        val r = IntakeGuidelines.intake(listOf(entry(1, n, Quantity(1.0, QuantityUnit.SERVINGS), serving = null)), null)
        assertTrue(r.of("Sal").incomplete)
        assertFalse(r.of("Sal").hasData)
    }

    @Test
    fun exceededOnlyForMaxLimits() {
        val salty = Nutrients(100.0, 1.0, 1.0, 1.0, saltG = 6.0, fiberG = 1.0)
        val r = IntakeGuidelines.intake(listOf(entry(1, salty)), 2000)
        assertTrue(r.of("Sal").exceeded)
        assertFalse(r.of("Fibra").exceeded) // por debajo del mínimo no es «pasarse»
    }

    @Test
    fun cellTextShowsTotalOverGoalAndDashWithoutData() {
        val partial = Nutrients(100.0, 1.0, 1.0, 1.0, sugarsG = 12.0, fiberG = 5.0)
        val r = IntakeGuidelines.intake(listOf(entry(1, partial), entry(2, Nutrients(100.0, 1.0, 1.0, 1.0, fiberG = 2.5))), 2000)
        assertEquals("7,5 / 25 g", r.of("Fibra").cellText())
        assertEquals("12+ / 50 g", r.of("Azúcares").cellText())
        assertEquals("—", r.of("Sal").cellText())
        assertEquals("—", r.of("Saturadas").cellText())
    }

    @Test
    fun emptyDayGivesFourNutrientsWithoutData() {
        val r = IntakeGuidelines.intake(emptyList(), null)
        assertEquals(listOf("Fibra", "Sal", "Azúcares", "Saturadas"), r.map { it.label })
        assertTrue(r.none { it.hasData || it.incomplete || it.exceeded })
        assertTrue(r.all { it.cellText() == "—" })
    }

    @Test
    fun energyBasedGoalsFollowKcalGoal() {
        assertEquals(50.0, IntakeGuidelines.sugarsMaxG(null), 1e-9)
        assertEquals(25.0, IntakeGuidelines.sugarsMaxG(1000), 1e-9)
        assertEquals(200.0 / 9.0, IntakeGuidelines.saturatedMaxG(null), 1e-9)
    }
}
