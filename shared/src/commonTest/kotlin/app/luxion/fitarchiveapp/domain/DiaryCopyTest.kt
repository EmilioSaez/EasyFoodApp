package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiaryCopyTest {
    private val nutrients = Nutrients(kcal = 100.0, proteinG = 1.0, carbsG = 2.0, fatG = 3.0)
    private fun entry(id: Long, meal: MealType) =
        DiaryEntry(id, "2026-11-04", meal, "123", "Alimento $id", Quantity(1.5, QuantityUnit.SERVINGS), 40.0, nutrients, id)

    @Test
    fun anActiveMealMapsToItself() {
        for (n in MealPlan.options) {
            val active = MealPlan.preset(n)
            active.forEach { assertEquals(it, DiaryCopy.targetMeal(it, active)) }
        }
    }

    @Test
    fun aMealMissingFromThePresetMapsToTheNearestOne() {
        val three = MealPlan.preset(3)
        assertEquals(MealType.BREAKFAST, DiaryCopy.targetMeal(MealType.MID_MORNING, three)) // 11:00 está a 150 min del desayuno y a 180 de la comida
        assertEquals(MealType.DINNER, DiaryCopy.targetMeal(MealType.LATE_SNACK, three))
        assertEquals(MealType.LUNCH, DiaryCopy.targetMeal(MealType.SNACK, three)) // 17:30 empata (210 min) con Comida y Cena: gana la primera
    }

    @Test
    fun planKeepsEverythingButDateAndMealAndTheOrder() {
        val src = listOf(entry(1, MealType.LUNCH), entry(2, MealType.LUNCH))
        val plan = DiaryCopy.plan(src, "2026-11-03", null, MealPlan.preset(4))
        assertFalse(plan.remapped)
        assertEquals(listOf("Alimento 1", "Alimento 2"), plan.entries.map { it.name })
        plan.entries.forEachIndexed { i, e ->
            assertEquals("2026-11-03", e.date)
            assertEquals(MealType.LUNCH, e.meal)
            assertEquals(src[i].quantity, e.quantity)
            assertEquals(src[i].servingSizeG, e.servingSizeG)
            assertEquals(src[i].nutrients, e.nutrients)
            assertEquals(src[i].barcode, e.barcode)
        }
    }

    @Test
    fun anExplicitTargetMealWinsAndIsNotARemap() {
        val plan = DiaryCopy.plan(listOf(entry(1, MealType.LUNCH)), "2026-11-03", MealType.DINNER, MealPlan.preset(3))
        assertEquals(listOf(MealType.DINNER), plan.entries.map { it.meal })
        assertFalse(plan.remapped)
    }

    @Test
    fun remapIsReportedWhenAMealChanges() {
        val plan = DiaryCopy.plan(listOf(entry(1, MealType.SNACK)), "2026-11-03", null, MealPlan.preset(3))
        assertTrue(plan.remapped)
    }
}
