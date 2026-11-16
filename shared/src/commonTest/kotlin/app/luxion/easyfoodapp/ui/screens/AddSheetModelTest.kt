package app.luxion.easyfoodapp.ui.screens

import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.ui.navigation.AddSheetTarget
import app.luxion.easyfoodapp.ui.navigation.Route
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** La hoja «Añadir» apunta todo a la comida elegida y al día con el que se abrió (nunca se pierde la fecha). */
class AddSheetModelTest {
    private val today = "2026-11-04"
    private val deps = AddSheetDeps(null, null, null, null, null, null, { today }, MealChoices(4) { 12 * 60 })

    private fun model(meal: MealType? = null, date: String? = null) =
        AddSheetModel(AddSheetTarget(meal, date), deps, CoroutineScope(EmptyCoroutineContext))

    @Test
    fun usesTodayAndTheNearestMealWhenOpenedWithoutArguments() {
        val m = model()
        assertEquals(today, m.date)
        assertEquals(MealType.LUNCH, m.meal)
    }

    @Test
    fun keepsTheMealAndDateTheSheetWasOpenedWith() {
        val m = model(MealType.BREAKFAST, "2026-11-01")
        assertEquals("2026-11-01", m.date)
        assertEquals(MealType.BREAKFAST, m.meal)
        assertEquals(Route.ProductDetail("8410000000000", MealType.BREAKFAST, null, "2026-11-01"), m.confirmRoute("8410000000000"))
    }

    @Test
    fun confirmRouteFollowsTheMealPickedInTheSheet() {
        val m = model(MealType.BREAKFAST, "2026-11-01")
        m.meal = MealType.DINNER
        assertEquals(MealType.DINNER, m.confirmRoute("custom:3").meal)
    }

    @Test
    fun dayLabelNamesNearbyDays() {
        assertEquals("hoy", model(date = today).dayLabel())
        assertEquals("ayer", model(date = "2026-11-03").dayLabel())
        assertEquals("mañana", model(date = "2026-11-05").dayLabel())
        assertEquals("1 nov", model(date = "2026-11-01").dayLabel())
    }
}
