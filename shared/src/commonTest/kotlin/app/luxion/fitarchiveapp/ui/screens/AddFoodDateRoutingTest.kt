package app.luxion.fitarchiveapp.ui.screens

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.navigation.Route
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Regresión: al añadir desde un día anterior del diario, la fecha viaja por todas las rutas de alta. */
class AddFoodDateRoutingTest {
    private val past = "2026-11-03"

    @Test
    fun productDetailKeepsDateAndMealFromAdd() {
        val r = Route.Add(MealType.LUNCH, date = past).toProductDetail("8410000000000")
        assertEquals(Route.ProductDetail("8410000000000", MealType.LUNCH, null, past), r)
    }

    @Test
    fun scannerKeepsDateAndMealFromAdd() {
        assertEquals(Route.Scanner(MealType.DINNER, past), Route.Add(MealType.DINNER, date = past).toScanner())
    }

    @Test
    fun addFromTabHasNoDateSoTodayIsUsed() {
        assertNull(Route.Add().toProductDetail("1").date)
        assertNull(Route.Add().toScanner().date)
    }

    @Test
    fun entryDateIsRouteDateOrToday() {
        val today = "2026-11-10"
        assertEquals(past, (Route.ProductDetail("1", date = past).date ?: today))
        assertEquals(today, (Route.ProductDetail("1").date ?: today))
    }
}
