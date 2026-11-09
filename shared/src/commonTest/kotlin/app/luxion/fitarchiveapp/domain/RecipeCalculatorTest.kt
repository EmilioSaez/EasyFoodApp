package app.luxion.fitarchiveapp.domain

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewRecipe
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.RecipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RecipeCalculatorTest {
    private val rice = RecipeIngredient("generic:1", "Arroz", 200.0, Nutrients(350.0, 7.0, 78.0, 1.0))
    private val oil = RecipeIngredient(null, "Aceite", 10.0, Nutrients(900.0, 0.0, 0.0, 100.0), servingSizeG = 10.0)

    @Test
    fun totalsAreTheSumOfScaledIngredients() {
        val t = RecipeCalculator.totals(listOf(rice, oil))
        assertEquals(790.0, t.kcal!!, 1e-9)
        assertEquals(14.0, t.proteinG!!, 1e-9)
        assertEquals(156.0, t.carbsG!!, 1e-9)
        assertEquals(12.0, t.fatG!!, 1e-9)
    }

    @Test
    fun aMissingValueInAnyIngredientMakesThatTotalUnknown() {
        val unknown = RecipeIngredient("1", "Salsa", 50.0, Nutrients(kcal = 100.0, proteinG = null, carbsG = 5.0, fatG = 1.0))
        val t = RecipeCalculator.totals(listOf(rice, unknown))
        assertEquals(750.0, t.kcal!!, 1e-9)
        assertNull(t.proteinG)
        assertEquals(158.5, t.carbsG!!, 1e-9)
    }

    @Test
    fun emptyListHasNoTotals() {
        assertNull(RecipeCalculator.totals(emptyList()).kcal)
    }

    @Test
    fun perServingDividesByServings() {
        val s = RecipeCalculator.perServing(listOf(rice, oil), 4)!!
        assertEquals(197.5, s.kcal!!, 1e-9)
        assertEquals(3.0, s.fatG!!, 1e-9)
    }

    @Test
    fun perServingIsNullWithoutServings() {
        assertNull(RecipeCalculator.perServing(listOf(rice), null))
        assertNull(RecipeCalculator.perServing(listOf(rice), 0))
    }

    @Test
    fun incompleteIngredientsListsThoseWithMissingValues() {
        val unknown = RecipeIngredient("1", "Salsa", 50.0, Nutrients(kcal = 100.0, proteinG = null, carbsG = 5.0, fatG = 1.0))
        assertEquals(listOf("Salsa"), RecipeCalculator.incompleteIngredients(listOf(rice, unknown, oil)))
        assertEquals(emptyList(), RecipeCalculator.incompleteIngredients(listOf(rice, oil)))
    }

    @Test
    fun totalGramsAddsIngredients() {
        assertEquals(210.0, RecipeCalculator.totalGrams(listOf(rice, oil)), 1e-9)
    }

    @Test
    fun factorUsesPortionsOverServingsOrWholeRecipe() {
        assertEquals(0.5, RecipeCalculator.factor(4, 2.0)!!, 1e-9)
        assertEquals(1.0, RecipeCalculator.factor(null, 3.0)!!, 1e-9)
        assertNull(RecipeCalculator.factor(4, 0.0))
        assertNull(RecipeCalculator.factor(4, Double.NaN))
    }

    @Test
    fun diaryEntriesScaleGramsAndKeepPer100gValues() {
        val entries = RecipeCalculator.diaryEntries(listOf(rice, oil), 0.25, "2026-11-09", MealType.LUNCH)
        assertEquals(2, entries.size)
        assertEquals(50.0, entries[0].quantity.amount, 1e-9)
        assertEquals(QuantityUnit.GRAMS, entries[0].quantity.unit)
        assertEquals("generic:1", entries[0].barcode)
        assertNull(entries[1].barcode)
        assertEquals(rice.nutrients, entries[0].nutrients)
        assertEquals(MealType.LUNCH, entries[1].meal)
        assertEquals("2026-11-09", entries[1].date)
    }

    @Test
    fun diaryEntriesRejectNonPositiveFactor() {
        assertFailsWith<IllegalArgumentException> {
            RecipeCalculator.diaryEntries(listOf(rice), 0.0, "2026-11-09", MealType.LUNCH)
        }
    }

    @Test
    fun validation() {
        assertFailsWith<IllegalArgumentException> { RecipeIngredient(null, " ", 10.0, Nutrients(1.0, 1.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { RecipeIngredient(null, "x", 0.0, Nutrients(1.0, 1.0, 1.0, 1.0)) }
        assertFailsWith<IllegalArgumentException> { NewRecipe("", null, listOf(rice)) }
        assertFailsWith<IllegalArgumentException> { NewRecipe("Paella", null, emptyList()) }
        assertFailsWith<IllegalArgumentException> { NewRecipe("Paella", 0, listOf(rice)) }
        NewRecipe("Paella", 4, listOf(rice))
    }
}
