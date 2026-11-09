package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.ui.components.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class FavoriteFoodsTest {
    private val n = Nutrients(kcal = 100.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0)
    private fun product(barcode: String, name: String) = Product(barcode, name, null, null, n)
    private fun custom(id: Long, name: String) = CustomFood(id, name, null, n, null, null, 0, 0)

    @Test
    fun mixesProductsAndCustomFoodsSortedByName() {
        val r = favoriteFoodResults(
            keys = listOf("111", "custom:5", "generic:9"),
            products = listOf(product("111", "Nutella"), product("generic:9", "arroz"), product("222", "Otro")),
            customFoods = listOf(custom(5, "Mi pan"), custom(6, "Sin marcar")),
        )
        assertEquals(listOf("generic:9", "custom:5", "111"), r.map { it.key })
        assertEquals(listOf(DataSource.Generic, DataSource.Custom, DataSource.OpenFoodFacts), r.map { it.source })
    }

    @Test
    fun omitsFavoritesWithoutASheet() {
        val r = favoriteFoodResults(
            keys = listOf("999", "custom:7"),
            products = emptyList(),
            customFoods = listOf(custom(5, "Mi pan")),
        )
        assertEquals(emptyList(), r)
    }

    @Test
    fun noKeysMeansNoFavorites() {
        assertEquals(emptyList(), favoriteFoodResults(emptyList(), listOf(product("1", "A")), listOf(custom(1, "B"))))
    }
}
