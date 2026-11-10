package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.ai.LabelReading
import app.luxion.fitarchiveapp.model.Nutrients
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LabelFormTest {
    private val good = LabelReading(
        name = "Galletas", brand = "Dorada", ingredients = "Harina", servingLabel = "4 galletas", servingSizeG = 30.0,
        nutrients = Nutrients(430.0, 7.0, 72.0, 12.0, 2.5, 22.0, 3.1, 0.6),
    )

    @Test
    fun readingFillsTheCustomFoodFormAndSavesAsCustomFood() {
        val form = good.toFormState()
        assertEquals("Galletas", form.name)
        assertEquals("430", form.kcal)
        assertEquals("2,5", form.saturated)
        assertEquals("30", form.servingSize)
        val food = assertNotNull(form.toNewCustomFood(brand = good.brand))
        assertEquals("Dorada", food.brand)
        assertEquals(430.0, food.nutrients.kcal)
    }

    @Test
    fun unreadFieldsStayEmptyAndBlockSavingUntilFilled() {
        val form = good.copy(nutrients = good.nutrients.copy(fatG = null), name = null).toFormState()
        assertEquals("", form.fat)
        assertNull(form.toNewCustomFood()) // faltan nombre y grasas: hay que completarlos
        assertEquals("No se leyó en la etiqueta", unreadHint(setOf("fat"), "fat", form.fat))
        assertNull(unreadHint(setOf("fat"), "fat", "12")) // ya rellenado
        assertNull(unreadHint(null, "fat", "")) // formulario normal, sin IA
    }

    @Test
    fun consistentLabelHasNoDoubts() {
        assertEquals(emptyList(), good.toFormState().doubtfulReasons())
    }

    @Test
    fun kcalThatDoNotMatchMacrosAreFlagged() {
        val reasons = good.copy(nutrients = good.nutrients.copy(kcal = 90.0)).toFormState().doubtfulReasons()
        assertTrue(reasons.any { "no cuadran" in it }, reasons.toString())
    }

    @Test
    fun missingValuesAreNotReportedAsDoubtsAndSubfractionsAre() {
        assertEquals(emptyList(), good.copy(nutrients = Nutrients(null, null, null, null)).toFormState().doubtfulReasons())
        val reasons = good.copy(nutrients = good.nutrients.copy(sugarsG = 90.0)).toFormState().doubtfulReasons()
        assertTrue(reasons.any { "azúcares" in it }, reasons.toString())
    }
}
