package app.luxion.fitarchiveapp.ai

import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.MealTemplate
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import app.luxion.fitarchiveapp.model.Recipe
import app.luxion.fitarchiveapp.model.RecipeIngredient
import app.luxion.fitarchiveapp.model.TemplateItem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAdviceSourceTest {
    private val day = "2026-11-10" // martes

    private fun source(
        entries: List<app.luxion.fitarchiveapp.model.DiaryEntry> = emptyList(),
        goals: DailyGoals = DailyGoals(kcal = 2000, proteinG = 100, carbsG = 200, fatG = 70),
        favorites: FakeAdviceFavorites = FakeAdviceFavorites(),
        custom: List<CustomFood> = emptyList(),
        recipes: List<Recipe> = emptyList(),
        templates: List<MealTemplate> = emptyList(),
        water: FakeAdviceWater? = FakeAdviceWater(500, 2000),
    ) = AiAdviceSource(
        FakeAdviceDiary(entries), FakeAdviceGoals(goals), favorites, FakeAdviceCustomFoods(custom),
        FakeAdviceRecipes(recipes), FakeAdviceTemplates(templates), water,
    )

    @Test
    fun remainingKcalAndMacrosComeFromTheDayAndGoals() = runTest {
        val ctx = source(listOf(adviceEntry(1, kcal100 = 300.0, grams = 200.0))) // 600 kcal, P 20, H 40, G 10
            .build(day, MealType.DINNER)
        assertEquals(600.0, ctx.day.kcal)
        assertEquals(1400, ctx.remainingKcal)
        val text = ctx.dayText()
        assertTrue("Quedan 1400 kcal." in text, text)
        assertTrue("Proteína: quedan 80 g de 100 g." in text, text)
        assertTrue("Agua: 500 ml de 2000 ml." in text, text)
        assertEquals(MealType.DINNER, ctx.currentMeal)
    }

    @Test
    fun withoutGoalsThereIsNoRemainingAndOverGoalIsSaid() = runTest {
        val none = source(goals = DailyGoals()).build(day, MealType.LUNCH)
        assertNull(none.remainingKcal)
        val over = source(listOf(adviceEntry(1, kcal100 = 1000.0, grams = 300.0))).build(day, MealType.LUNCH)
        assertTrue("Se ha pasado 1000 kcal del objetivo." in over.dayText())
    }

    @Test
    fun currentMealFoodsListsOnlyThatMeal() = runTest {
        val ctx = source(
            listOf(adviceEntry(1, "Tostada", meal = MealType.BREAKFAST), adviceEntry(2, "Arroz", meal = MealType.LUNCH, kcal100 = 130.0, grams = 200.0)),
        ).build(day, MealType.LUNCH)
        assertEquals(listOf("Arroz · 260 kcal"), ctx.currentMealFoods)
    }

    @Test
    fun candidatesAreCappedDedupedAndSkipQuickEntries() = runTest {
        val products = (1..10).map { Product("fav$it", "Favorito $it", null, null, Nutrients(100.0, 1.0, 1.0, 1.0), servingSizeG = 50.0) }
        val custom = CustomFood(7, "Mi bizcocho", null, Nutrients(400.0, 5.0, 50.0, 20.0), 100.0, null, 0, 0)
        val favorites = FakeAdviceFavorites(listOf("custom:7") + products.map { it.barcode }, products)
        val recent = (1L..9L).map { adviceEntry(it, "Reciente $it") } +
            adviceEntry(20, "Favorito 1") + adviceEntry(21, "Cena fuera", quick = true)
        val template = MealTemplate(
            1, "Desayuno tipo",
            listOf(TemplateItem("1", "Pan", Quantity(60.0, QuantityUnit.GRAMS), null, Nutrients(250.0, 8.0, 50.0, 2.0))),
            0, 0,
        )
        val recipe = Recipe(
            1, "Lentejas", 4,
            listOf(RecipeIngredient("2", "Lenteja", 400.0, Nutrients(120.0, 9.0, 20.0, 1.0))),
            0, 0,
        )
        val c = source(recent, favorites = favorites, custom = listOf(custom), templates = listOf(template), recipes = listOf(recipe))
            .build(day, MealType.LUNCH, withCandidates = true).candidates

        assertEquals(AiAdviceLimits.MAX_PER_SOURCE, c.favorites.size)
        assertEquals("Mi bizcocho", c.favorites.first().name)
        assertEquals(400, c.favorites.first().kcal)
        assertEquals(50, c.favorites[1].kcal) // 100 kcal/100 g en una porción de 50 g
        assertTrue(c.recents.none { it.name == "Cena fuera" }, "las entradas rápidas no son alimentos")
        assertTrue(c.recents.none { it.name == "Favorito 1" }, "un favorito no se repite en recientes")
        assertEquals(AiAdviceLimits.MAX_PER_SOURCE, c.recents.size)
        assertEquals(AiCandidate("Desayuno tipo", 150, "1 alimento"), c.templates.single())
        assertEquals(AiCandidate("Lentejas", 120, "1 ración"), c.recipes.single()) // 480 kcal / 4
    }

    @Test
    fun candidatesAreNotReadUnlessAsked() = runTest {
        val s = source(listOf(adviceEntry(1)), favorites = FakeAdviceFavorites(listOf("x")))
        assertTrue(s.build(day, MealType.LUNCH).candidates.isEmpty)
    }

    @Test
    fun weekSummaryAveragesLoggedDaysSoFar() = runTest {
        val entries = listOf(
            adviceEntry(1, date = "2026-11-09", kcal100 = 500.0, grams = 400.0, fiber = 2.5), // 2000 kcal el lunes
            adviceEntry(2, date = "2026-11-10", kcal100 = 100.0, grams = 1000.0, fiber = 2.0), // 1000 kcal hoy
            adviceEntry(3, date = "2026-11-02", kcal100 = 999.0, grams = 100.0), // semana anterior: no cuenta
        )
        val w = source(entries).build(day, MealType.LUNCH, withWeek = true).week!!
        assertEquals(2, w.loggedDays)
        assertEquals(2, w.daysSoFar)
        assertEquals(1500.0, w.avgKcal!!, 0.001)
        assertEquals(1, w.completedDays) // el lunes cuadra con 2000 kcal; hoy aún no
        assertEquals(15.0, w.avgFiberG!!, 0.001)
        assertNull(w.avgSaltG)
        assertTrue("2 de 2 días con registro" in w.toPromptText())
    }

    @Test
    fun theContextNeverCarriesAccountDataOrFullHistory() = runTest {
        val ctx = source(listOf(adviceEntry(1, date = "2026-10-01"), adviceEntry(2))).build(day, MealType.LUNCH, withCandidates = true, withWeek = true)
        val all = ctx.dayText() + ctx.mealText() + ctx.candidates.toPromptText() + ctx.week!!.toPromptText()
        assertFalse("2026-10-01" in all, "no debe viajar ninguna fecha del historial")
    }
}
