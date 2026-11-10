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
    fun customFoodToleratesPluralsTyposAndSupermarkets() {
        val f = CustomFood(8, "Tortillas de patata", "Hacendado", n, null, null, 0, 0)
        assertEquals(true, f.matches("tortilla"))
        assertEquals(true, f.matches("patatas"))
        assertEquals(true, f.matches("tortila patata")) // errata
        assertEquals(true, f.matches("mercadona tortilla"))
        assertEquals(false, f.matches("lidl tortilla"))
    }

    @Test
    fun mergeKeepsAllSourcesWithCustomFirst() {
        val all = mergeResults(
            listOf(CustomFood(1, "Mi pan", null, n, null, null, 0, 0)),
            listOf(Product("generic:1", "Pan", null, null, n), Product("841", "Pan molde", null, null, n)),
        )
        assertEquals(listOf("custom:1", "generic:1", "841"), all.map { it.key })
        assertEquals(listOf(DataSource.Custom, DataSource.Generic, DataSource.OpenFoodFacts), all.map { it.source })
    }

    @Test
    fun customFoodIdParsesOnlyCustomKeys() {
        assertEquals(7L, customFoodIdOf("custom:7"))
        assertEquals(null, customFoodIdOf("custom:"))
        assertEquals(null, customFoodIdOf("custom:abc"))
        assertEquals(null, customFoodIdOf("generic:7"))
        assertEquals(null, customFoodIdOf("3017620422003"))
    }

    @Test
    fun dataSourceOfKeyDistinguishesOrigins() {
        assertEquals(DataSource.Generic, dataSourceOf("generic:174988"))
        assertEquals(DataSource.OpenFoodFacts, dataSourceOf("3017620422003"))
        assertEquals(DataSource.Custom, dataSourceOf("custom:3"))
        assertEquals(DataSource.Custom, dataSourceOf(null))
    }
}
