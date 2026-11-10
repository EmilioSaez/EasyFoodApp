package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TemplateFormTest {
    private val oats = Nutrients(380.0, 13.0, 60.0, 7.0)
    private val noData = Nutrients(null, null, null, null)
    private fun item(name: String, g: Double, n: Nutrients = oats) =
        TemplateItem("1", name, Quantity(g, QuantityUnit.GRAMS), null, n)

    @Test
    fun emptyFormIsInvalid() {
        val f = TemplateFormState()
        assertEquals("Escribe un nombre", f.nameError)
        assertEquals("Añade al menos un alimento", f.itemsError)
        assertNull(f.toNewTemplate())
    }

    @Test
    fun validFormTrimsTheName() {
        val t = TemplateFormState("  Desayuno ", listOf(item("Avena", 40.0))).toNewTemplate()
        assertNotNull(t)
        assertEquals("Desayuno", t.name)
    }

    @Test
    fun repeatedNameIsRejectedIgnoringCaseAndSpaces() {
        val f = TemplateFormState("  desayuno", listOf(item("Avena", 40.0)), otherNames = setOf("Desayuno"))
        assertEquals(TemplateRules.NAME_TAKEN, f.nameError)
        assertNull(f.toNewTemplate())
        assertNotNull(f.copy(name = "Desayuno 2").toNewTemplate())
    }

    @Test
    fun addRemoveAndChangeAmountKeepOrderAndUnit() {
        val servings = TemplateItem(null, "Yogur", Quantity(2.0, QuantityUnit.SERVINGS), 125.0, oats)
        var f = TemplateFormState("X").addItem(item("Avena", 40.0)).addItem(servings).addItem(item("Pan", 60.0))
        f = f.withAmount(1, 3.0).withAmount(0, 55.0)
        assertEquals(listOf("Avena", "Yogur", "Pan"), f.items.map { it.name })
        assertEquals(Quantity(55.0, QuantityUnit.GRAMS), f.items[0].quantity)
        assertEquals(Quantity(3.0, QuantityUnit.SERVINGS), f.items[1].quantity)
        assertEquals(listOf("Avena", "Pan"), f.removeItem(1).items.map { it.name })
    }

    @Test
    fun totalsSumKnownItemsAndSkipMissingData() {
        val items = listOf(item("Avena", 100.0), item("Raro", 50.0, noData), item("Pan", 50.0))
        assertEquals(380.0 + 190.0, templateTotals(items).kcal)
        assertNull(templateTotals(listOf(item("Raro", 50.0, noData))).kcal)
    }

    @Test
    fun listTextsDescribeTheTemplate() {
        val t = MealTemplate(
            1, "Desayuno",
            listOf(item("Avena", 100.0), item("Pan", 60.0), item("Leche", 200.0), item("Miel", 10.0)), 0, 0,
        )
        assertEquals("4 alimentos · 1.406 kcal", t.listDetail())
        assertEquals("Avena 100 g, Pan 60 g, Leche 200 g…", t.itemsPreview())
        assertEquals("1 alimento · sin dato de energía", MealTemplate(2, "X", listOf(item("R", 1.0, noData)), 0, 0).listDetail())
    }
}
