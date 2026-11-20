package app.luxion.easyfoodapp.ui.screens

import app.luxion.easyfoodapp.ai.AdviceHarness
import app.luxion.easyfoodapp.ai.AiAdviceLimits
import app.luxion.easyfoodapp.ai.AiAdviceParsing
import app.luxion.easyfoodapp.ai.AiAdvicePrompts
import app.luxion.easyfoodapp.ai.AiMealPlan
import app.luxion.easyfoodapp.ai.AiResult
import app.luxion.easyfoodapp.ai.AiReviewPeriod
import app.luxion.easyfoodapp.ai.FakeAdviceCustomFoods
import app.luxion.easyfoodapp.ai.MealSuggestion
import app.luxion.easyfoodapp.data.ProductSearchStore
import app.luxion.easyfoodapp.model.DailyGoals
import app.luxion.easyfoodapp.model.DiaryEntry
import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Product
import app.luxion.easyfoodapp.model.Quantity
import app.luxion.easyfoodapp.model.QuantityUnit
import app.luxion.easyfoodapp.plate.FoodCatalog
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class LinkSearch(private val products: List<Product>) : ProductSearchStore {
    override suspend fun search(text: String, limit: Int, onlyMine: Boolean): List<Product> {
        val words = text.lowercase().split(" ")
        return products.filter { p -> words.all { w -> p.name.lowercase().contains(w) } }
    }
}

/** «¿Qué como?» enlazado con la base, menú con lista de la compra y alimentos reales en el consejo y el chat. */
class AiTabLinksTest {
    private val yogurt = Product("generic:7", "Yogur natural", null, null, Nutrients(60.0, 4.0, 5.0, 3.0), null)
    private val catalog = FoodCatalog(LinkSearch(listOf(yogurt)), FakeAdviceCustomFoods())

    private fun reply(vararg options: String) = """{"options":[${options.joinToString(",")}]}"""

    private fun option(name: String, term: String, grams: Double, kcal: Double = 999.0) =
        """{"name":"$name","amount":"${grams.toInt()} g","kcal":$kcal,"proteinG":1,"carbsG":1,"fatG":1,"reason":"ok","search_term":"$term","grams":$grams}"""

    @Test
    fun aSuggestionFoundInTheBaseUsesItsRealValuesAndIsLoggedAsARealEntry() = runTest {
        val h = AdviceHarness(meal = MealType.SNACK, catalog = catalog)
        h.reply(reply(option("Yogur natural", "yogur natural", 200.0), option("Plato raro", "xyzzy", 100.0, kcal = 300.0)))
        h.refresh()
        h.state.suggest("")

        val link = h.state.links[0]!!
        assertEquals(120.0, link.kcal, 0.001) // 60 kcal/100 g × 200 g, no las 999 de la IA
        assertEquals(8.0, link.proteinG, 0.001)
        assertEquals("De tu base · Genérico", link.origin)
        assertNull(h.state.links[1]) // sin coincidencia: cifras de la IA

        h.state.addToDiary(0)
        val real = h.diary.rows.single()
        assertFalse(real.quick)
        assertEquals("generic:7", real.barcode)
        assertEquals(200.0, real.grams!!, 0.001)
        assertEquals(120.0, real.totals.kcal!!, 0.001)

        h.state.addToDiary(1)
        val quick = h.diary.rows.last()
        assertTrue(quick.quick)
        assertEquals(300.0, quick.totals.kcal!!, 0.001)
    }

    @Test
    fun aLooseOrGramlessMatchIsNotLinked() = runTest {
        val h = AdviceHarness(catalog = catalog)
        // «yogur griego natural» solo casa quitando palabras (aproximado) → no se enlaza; sin gramos tampoco.
        h.reply(reply(option("Yogur griego", "yogur griego natural", 150.0), """{"name":"Yogur","amount":"1 ud","kcal":80,"proteinG":1,"carbsG":1,"fatG":1,"reason":"","search_term":"yogur natural","grams":0}"""))
        h.refresh()
        h.state.suggest("")
        assertTrue(h.state.links.isEmpty())
    }

    @Test
    fun withoutACatalogNothingIsLinked() = runTest {
        val h = AdviceHarness()
        h.reply(reply(option("Yogur natural", "yogur natural", 200.0)))
        h.refresh()
        h.state.suggest("")
        assertTrue(h.state.links.isEmpty())
    }

    @Test
    fun suggestionParsingReadsTermAndGramsAndTheSchemaAsksForThem() {
        val parsed = AiAdviceParsing.suggestions(
            Json.parseToJsonElement(reply(option("A", " yogur  natural ", 150.0), option("B", "x", 0.0), option("C", "y", 99999.0))),
        )
        val list = assertIs<AiResult.Ok<List<MealSuggestion>>>(parsed).value
        assertEquals("yogur natural", list[0].searchTerm)
        assertEquals(150.0, list[0].grams)
        assertNull(list[1].grams)
        assertNull(list[2].grams)
        val props = AiAdvicePrompts.suggestSchema["properties"]!!.jsonObject["options"]!!.jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject
        assertTrue(props.keys.containsAll(listOf("search_term", "grams")))
    }

    private fun entry(id: Long, name: String, grams: Double?, kcal: Double, quick: Boolean = false) = DiaryEntry(
        id, AdviceHarness.TODAY, MealType.BREAKFAST, if (quick) null else "generic:$id", name,
        Quantity(grams ?: 1.0, QuantityUnit.GRAMS), null, Nutrients(kcal, 5.0, 5.0, 5.0), id, quick,
    )

    @Test
    fun theChatAndTheReviewSeeWhatWasEatenTodayButSuggestionsDoNot() = runTest {
        val h = AdviceHarness(entries = listOf(entry(1, "Yogur natural", 125.0, 75.0), entry(2, "Tostada con aceite", 60.0, 180.0)))
        h.reply("Te falta proteína.")
        h.refresh()
        h.state.ask("¿Qué me falta?")
        val chat = h.requests.last()
        assertTrue("Apuntado hoy: <usuario>Yogur natural · 125 g · 94 kcal; Tostada con aceite · 60 g · 108 kcal</usuario>." in chat)

        h.reply("""{"summary":"Bien","points":[]}""")
        h.state.review(AiReviewPeriod.DAY)
        assertTrue("Yogur natural" in h.requests.last())

        h.reply(reply(option("Fruta", "manzana", 150.0)))
        h.state.suggest("")
        assertFalse("Apuntado hoy" in h.requests.last())
    }

    @Test
    fun nothingIsSentAboutFoodsWhenTheDayIsEmptyAndTheListIsCapped() = runTest {
        val empty = AdviceHarness()
        empty.reply("ok")
        empty.refresh()
        empty.state.ask("hola")
        assertTrue("Hoy no hay nada apuntado todavía." in empty.requests.last())

        val many = AdviceHarness(entries = (1L..30L).map { entry(it, "Comida $it", 10.0, 10.0) })
        many.reply("ok")
        many.refresh()
        many.state.ask("hola")
        val req = many.requests.last()
        assertTrue("Comida 15 " in req)
        assertFalse("Comida 16 " in req)
        assertEquals(15, AiAdviceLimits.MAX_TODAY_FOODS)
    }

    @Test
    fun theWeekReviewListsTheMostRepeatedFoodsByNameOnly() = runTest {
        val h = AdviceHarness(entries = listOf(entry(1, "Avena", 40.0, 150.0), entry(2, "Avena", 40.0, 150.0), entry(3, "Plátano", 100.0, 90.0), entry(4, "Gasto rápido", null, 100.0, quick = true)))
        h.reply("""{"summary":"Bien","points":[]}""")
        h.refresh()
        h.state.review(AiReviewPeriod.WEEK)
        val req = h.requests.last()
        assertTrue("Lo que más ha comido esta semana: <usuario>Avena; Plátano</usuario>." in req)
        assertFalse("Gasto rápido; " in req && "esta semana: Gasto" in req) // las entradas rápidas no cuentan
    }

    private val planJson = """{"days":[
        {"day":"Día 1","meals":[{"meal":"desayuno","name":"Avena con plátano"},{"meal":"comida","name":"Lentejas"},{"meal":"","name":""}]},
        {"day":"Día 2","meals":[]},
        {"day":"Día 3","meals":[{"meal":"cena","name":"Tortilla"}]}],
        "shopping":[{"item":"Avena","quantity":"500 g"},{"item":"avena","quantity":"1 kg"},{"item":"Plátanos","quantity":""},{"item":"","quantity":"2"}]}"""

    @Test
    fun theMealPlanIsParsedDefensivelyAndCostsOneCall() = runTest {
        val h = AdviceHarness(goals = DailyGoals(kcal = 2100, proteinG = 120))
        h.reply(planJson)
        h.refresh()
        h.state.planMeals(3, "vegetariano")

        val plan = assertIs<AiLoad.Done<AiMealPlan>>(h.state.plan).value
        assertEquals(listOf("Día 1", "Día 3"), plan.days.map { it.day }) // el día sin comidas se descarta
        assertEquals(2, plan.days[0].meals.size) // la comida sin nombre se descarta
        assertEquals(listOf("Avena · 500 g", "Plátanos"), plan.shopping.map { it.text }) // sin duplicados ni vacíos
        assertEquals(1, h.requests.size)
        assertEquals(1, h.state.callsToday)
        val req = h.requests.single()
        assertTrue("menú de 3 días" in req && "Objetivo diario: 2100 kcal, proteína 120 g" in req)
        assertTrue("<usuario>vegetariano</usuario>" in req)
    }

    @Test
    fun shoppingItemsCanBeTickedAndThePlanCleared() = runTest {
        val h = AdviceHarness()
        h.reply(planJson)
        h.refresh()
        h.state.planMeals(7, "")
        assertEquals(7, h.state.planDays)
        h.state.toggleBought(1)
        assertEquals(setOf(1), h.state.bought)
        h.state.toggleBought(1)
        assertTrue(h.state.bought.isEmpty())
        h.state.toggleBought(0)
        h.state.clearPlan()
        assertEquals(AiLoad.Idle, h.state.plan)
        assertTrue(h.state.bought.isEmpty())
    }

    @Test
    fun anEmptyPlanFailsAndBlockedStatesSendNothing() = runTest {
        val h = AdviceHarness()
        h.reply("""{"days":[],"shopping":[]}""")
        h.refresh()
        h.state.planMeals(3, "")
        assertIs<AiLoad.Failed>(h.state.plan)

        for (blocked in listOf(AdviceHarness(key = null), AdviceHarness(enabled = false), AdviceHarness(ack = false), AdviceHarness(limit = 1).also { it.reply("""{"options":[]}"""); it.refresh(); it.state.suggest("") })) {
            val before = blocked.requests.size
            blocked.reply(planJson)
            blocked.refresh()
            blocked.state.planMeals(3, "")
            assertEquals(before, blocked.requests.size)
            assertEquals(AiLoad.Idle, blocked.state.plan)
        }
    }
}
