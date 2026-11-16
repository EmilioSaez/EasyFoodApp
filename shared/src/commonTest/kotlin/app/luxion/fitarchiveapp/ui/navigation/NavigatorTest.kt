package app.luxion.fitarchiveapp.ui.navigation

import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.screens.YOU_CARD_ROUTES
import app.luxion.fitarchiveapp.ui.screens.settingsSections
import app.luxion.fitarchiveapp.ui.screens.youLinks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorTest {
    @Test
    fun startsAtDiaryWithoutBack() {
        val nav = Navigator()
        assertEquals(Route.Diary, nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.pop())
    }

    @Test
    fun pushAndPopKeepArguments() {
        val nav = Navigator()
        nav.push(Route.Add(MealType.LUNCH))
        nav.push(Route.ProductDetail(barcode = "123", meal = MealType.LUNCH))
        assertEquals(Route.ProductDetail("123", MealType.LUNCH), nav.current)
        assertTrue(nav.pop())
        assertEquals(Route.Add(MealType.LUNCH), nav.current)
    }

    @Test
    fun replaceDoesNotLeaveTheOldScreenInTheStack() {
        val nav = Navigator()
        nav.push(Route.Add())
        nav.push(Route.Scanner())
        nav.replace(Route.CustomFood(barcode = "8410000000000"))
        nav.pop()
        assertEquals(Route.Add(), nav.current)
    }

    @Test
    fun navigateTabResetsStackWithDiaryAsRoot() {
        val nav = Navigator()
        nav.push(Route.Add())
        nav.push(Route.Scanner())
        nav.navigateTab(TopLevelTab.You)
        assertEquals(Route.You, nav.current)
        nav.pop()
        assertEquals(Route.Diary, nav.current)
        nav.navigateTab(TopLevelTab.Today)
        assertFalse(nav.canGoBack)
    }

    @Test
    fun screensHangingFromYouStackOnTopOfIt() {
        val nav = Navigator()
        nav.navigateTab(TopLevelTab.You)
        nav.push(Route.Recipes)
        nav.push(Route.Templates)
        nav.pop()
        nav.push(Route.RecipeDetail(3))
        nav.push(Route.RecipeEdit(3))
        nav.pop()
        nav.pop()
        assertEquals(Route.Recipes, nav.current)
        nav.pop()
        assertEquals(Route.You, nav.current)
        nav.pop()
        assertEquals(Route.Diary, nav.current)
    }

    @Test
    fun addSheetOpensWithMealAndContextDateAndClosesOnPush() {
        val nav = Navigator()
        nav.contextDate = "2026-11-10"
        nav.openAddSheet(MealType.LUNCH)
        assertEquals(AddSheetTarget(MealType.LUNCH, "2026-11-10"), nav.addSheet)
        nav.push(Route.Scanner(MealType.LUNCH, "2026-11-10"))
        assertEquals(null, nav.addSheet)
        nav.openAddSheet()
        assertEquals(AddSheetTarget(null, "2026-11-10"), nav.addSheet)
        nav.closeAddSheet()
        assertEquals(null, nav.addSheet)
    }

    @Test
    fun navigatingTabsClosesTheSheetAndForgetsTheContextDate() {
        val nav = Navigator()
        nav.contextDate = "2026-11-10"
        nav.openAddSheet()
        nav.navigateTab(TopLevelTab.You)
        assertEquals(null, nav.addSheet)
        assertEquals(null, nav.contextDate)
    }

    @Test
    fun youLinksKeepEveryFormerTabAndIconReachable() {
        // «Tú» (lista y tarjetas) y «Ajustes». Alimentos se abre desde la hoja «Añadir» (sesión B).
        val routes = youLinks().map { it.route } + YOU_CARD_ROUTES + settingsSections().flatMap { s -> s.links.map { it.route } }
        listOf(Route.Weight, Route.Summary, Route.Goals, Route.Ai, Route.Recipes, Route.Settings, Route.Reminders, Route.AiSettings, Route.Data)
            .forEach { assertTrue(it in routes, "falta $it en «Tú»") }
    }

    @Test
    fun popToDiaryClearsStack() {
        val nav = Navigator()
        nav.push(Route.Add())
        nav.push(Route.ProductDetail())
        nav.popToDiary()
        assertEquals(Route.Diary, nav.current)
        assertFalse(nav.canGoBack)
    }
}
