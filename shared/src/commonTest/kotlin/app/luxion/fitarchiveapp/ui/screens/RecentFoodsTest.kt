package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.ui.components.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentFoodsTest {
    private val n = Nutrients(kcal = 100.0, proteinG = 1.0, carbsG = 1.0, fatG = 1.0)

    private fun entry(id: Long, name: String, barcode: String?, serving: Double? = null) = DiaryEntry(
        id, "2026-11-09", MealType.LUNCH, barcode, name, Quantity(100.0, QuantityUnit.GRAMS), serving, n, id,
    )

    @Test
    fun keepsOrderAndMapsEachOrigin() {
        val custom = listOf(CustomFood(5, "Mi pan", null, n, null, null, 0, 0))
        val r = recentFoodResults(
            listOf(
                entry(3, "Mi pan", null),
                entry(2, "Manzana", "generic:9"),
                entry(1, "Nutella", "3017620422003", serving = 15.0),
            ),
            custom,
        )
        assertEquals(listOf("custom:5", "generic:9", "3017620422003"), r.map { it.key })
        assertEquals(listOf(DataSource.Custom, DataSource.Generic, DataSource.OpenFoodFacts), r.map { it.source })
        assertEquals("15 g · 3017620422003", r.last().detail)
        assertEquals(100.0, r.first().kcalPer100g)
    }

    @Test
    fun customFoodIsLinkedByNameIgnoringCase() {
        val r = recentFoodResults(listOf(entry(1, "mi PAN", null)), listOf(CustomFood(5, "Mi pan", null, n, null, null, 0, 0)))
        assertEquals(listOf("custom:5"), r.map { it.key })
    }

    @Test
    fun dropsFoodsWithoutSheetAndDuplicates() {
        val r = recentFoodResults(
            listOf(
                entry(4, "Borrado", null),
                entry(3, "Nutella", "3017620422003"),
                entry(2, "Nutella otra vez", "3017620422003"),
            ),
            emptyList(),
        )
        assertEquals(listOf("3017620422003"), r.map { it.key })
        assertEquals("Nutella", r.single().name)
    }

    @Test
    fun emptyDiaryGivesNoRecents() {
        assertEquals(emptyList(), recentFoodResults(emptyList(), emptyList()))
    }
}
