package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.ui.components.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AddFoodResultsTest {
    private val n = Nutrients(kcal = 255.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0)

    @Test
    fun genericProductIsDetectedByKeyPrefix() {
        val r = Product("generic:123", "Manzana", null, null, n).toFoodResult()
        assertEquals(DataSource.Generic, r.source)
        assertEquals("Genérico", r.detail)
    }

    @Test
    fun offProductShowsBrandServingAndEan() {
        val r = Product("8410000000000", "Galletas", "Marca", null, n, servingSizeG = 30.0).toFoodResult()
        assertEquals(DataSource.OpenFoodFacts, r.source)
        assertEquals("Marca · 30 g · 8410000000000", r.detail)
    }

    @Test
    fun customFoodMatchesIgnoringAccentsAndCase() {
        val f = CustomFood(7, "Tortilla de patata", null, n, null, null, 0, 0)
        assertEquals(true, f.matches("TORT pat"))
        assertEquals(true, f.matches("tortíl"))
        assertEquals(false, f.matches("arroz"))
        assertEquals("custom:7", f.toFoodResult().key)
    }

    @Test
    fun filterKeepsOnlyChosenSource() {
        val all = mergeResults(
            listOf(CustomFood(1, "Mi pan", null, n, null, null, 0, 0)),
            listOf(Product("generic:1", "Pan", null, null, n), Product("841", "Pan molde", null, null, n)),
        )
        assertEquals(3, all.filteredBy(SourceFilter.All).size)
        assertEquals(listOf("custom:1"), all.filteredBy(SourceFilter.Custom).map { it.key })
        assertEquals(listOf("841"), all.filteredBy(SourceFilter.OpenFoodFacts).map { it.key })
    }
}
