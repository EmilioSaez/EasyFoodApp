package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MacroTotals
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.model.RecipeIngredient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RecipeFormatTest {
    private val rice = RecipeIngredient("generic:1", "Arroz", 200.0, Nutrients(350.0, 7.0, 78.0, 1.0))
    private val oil = RecipeIngredient(null, "Aceite", 10.0, Nutrients(900.0, 0.0, 0.0, 100.0))
    private fun recipe(servings: Int?, vararg i: RecipeIngredient) = Recipe(1, "R", servings, i.toList(), 0, 0)

    @Test
    fun listDetailPluralises() {
        assertEquals("1 ingrediente", recipe(null, rice).listDetail())
        assertEquals("2 ingredientes · 1 ración", recipe(1, rice, oil).listDetail())
        assertEquals("2 ingredientes · 4 raciones", recipe(4, rice, oil).listDetail())
    }

    @Test
    fun kcalLabelIsPerServingWhenThereAreServings() {
        assertEquals("198 kcal/ración", recipe(4, rice, oil).kcalLabel())
        assertEquals("790 kcal", recipe(null, rice, oil).kcalLabel())
    }

    @Test
    fun kcalLabelWithoutDataSaysSo() {
        val unknown = RecipeIngredient(null, "X", 10.0, Nutrients(null, null, null, null))
        assertEquals("Sin dato", recipe(2, rice, unknown).kcalLabel())
    }

    @Test
    fun summaryOrNullNeedsEveryFigure() {
        assertEquals("386 kcal · P 17 · H 62 · G 8", MacroTotals(386.0, 17.0, 62.0, 8.0).summaryOrNull())
        assertNull(MacroTotals(386.0, null, 62.0, 8.0).summaryOrNull())
    }
}
