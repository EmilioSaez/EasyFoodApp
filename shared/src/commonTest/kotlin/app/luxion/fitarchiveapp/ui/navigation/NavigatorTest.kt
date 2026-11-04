package app.luxion.fitarchiveapp.ui.navigation

import app.luxion.fitarchiveapp.model.MealType
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
        nav.navigateTab(TopLevelTab.Goals)
        assertEquals(Route.Goals, nav.current)
        nav.pop()
        assertEquals(Route.Diary, nav.current)
        nav.navigateTab(TopLevelTab.Diary)
        assertFalse(nav.canGoBack)
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
