package app.luxion.fitarchiveapp.plate

import app.luxion.fitarchiveapp.ai.AiError
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiResult
import app.luxion.fitarchiveapp.ai.ClaudeClient
import app.luxion.fitarchiveapp.ai.InMemoryAiKeyVault
import app.luxion.fitarchiveapp.ai.InMemoryAiPreferences
import app.luxion.fitarchiveapp.data.CustomFoodStore
import app.luxion.fitarchiveapp.data.DiaryStore
import app.luxion.fitarchiveapp.data.ProductSearchStore
import app.luxion.fitarchiveapp.model.CustomFood
import app.luxion.fitarchiveapp.model.DiaryEntry
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.NewDiaryEntry
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.model.Product
import app.luxion.fitarchiveapp.model.Quantity
import app.luxion.fitarchiveapp.model.QuantityUnit
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSearch(private val products: List<Product>) : ProductSearchStore {
    val queries = mutableListOf<String>()
    override suspend fun search(text: String, limit: Int, onlyMine: Boolean): List<Product> {
        queries += text
        val words = text.lowercase().split(" ")
        return products.filter { p -> words.all { w -> p.name.lowercase().contains(w) } }
    }
}

private class FakeCustom(private val foods: List<CustomFood> = emptyList()) : CustomFoodStore {
    override suspend fun all() = foods
    override suspend fun find(id: Long) = foods.firstOrNull { it.id == id }
    override suspend fun create(food: NewCustomFood, nowMillis: Long) = error("no")
    override suspend fun update(id: Long, food: NewCustomFood, nowMillis: Long) = error("no")
    override suspend fun delete(id: Long) = error("no")
}

private class FakeDiary : DiaryStore {
    val added = mutableListOf<NewDiaryEntry>()
    var failAtCall: Int? = null
    private var calls = 0
    override suspend fun entriesForDate(date: String) = emptyList<DiaryEntry>()
    override suspend fun find(id: Long): DiaryEntry? = null
    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long {
        calls++
        if (failAtCall == calls) throw IllegalStateException("db")
        added += entry
        return calls.toLong()
    }
    override suspend fun update(id: Long, meal: MealType, quantity: Quantity) = false
    override suspend fun delete(id: Long) = false
}

class PlatePhotoTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val rice = Product("generic:1", "Arroz blanco cocido", null, null, Nutrients(130.0, 2.7, 28.0, 0.3), null)
    private val chicken = Product("generic:2", "Pollo, pechuga, a la plancha", null, null, Nutrients(165.0, 31.0, 0.0, 3.6), null)

    private fun item(name: String, term: String, grams: Double, kcal: Double = 100.0) =
        """{"name":"$name","search_term":"$term","grams":$grams,"portion":"","confidence":"medium","kcal_per_100g":$kcal,"protein_g_per_100g":5,"carbs_g_per_100g":10,"fat_g_per_100g":2}"""

    private fun reply(items: List<String>, isFood: Boolean = true, stop: String = "end_turn"): String {
        val inner = """{"is_food":$isFood,"note":"","items":[${items.joinToString(",")}]}"""
        return """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${JsonPrimitive(inner)}}],"usage":{"input_tokens":1500,"output_tokens":300}}"""
    }

    private class Harness(val module: AiModule, val requests: MutableList<String>)

    private fun harness(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        key: String? = "sk-ant-test-0123456789abcdef",
        ready: Boolean = true,
    ): Harness {
        val requests = mutableListOf<String>()
        val engine = MockEngine { req ->
            requests += (req.body as io.ktor.http.content.TextContent).text
            respond(body, status, jsonHeaders)
        }
        val vault = InMemoryAiKeyVault(key)
        val module = AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
        if (ready) {
            module.settings.setEnabled(true)
            module.settings.setConsent(true)
        }
        return Harness(module, requests)
    }

    private fun presenter(h: Harness, diary: FakeDiary = FakeDiary(), products: List<Product> = listOf(rice, chicken), custom: List<CustomFood> = emptyList()) =
        PlatePresenter(h.module.service, PlateAnalyzer(h.module.service), FoodCatalog(FakeSearch(products), FakeCustom(custom)), diary, nowMillis = { 1L }, prepare = { it })

    private val photo = byteArrayOf(1, 2, 3)

    @Test
    fun linksDetectedFoodsWithTheDatabaseAndMarksUnmatchedAsEstimated() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz blanco cocido", 180.0), item("Salsa rara", "salsa xyzzy", 50.0, kcal = 200.0))))
        val p = presenter(h)
        p.onPhoto(photo)
        assertEquals(PlateState.Ready, p.state.value)
        p.analyze("")
        val review = assertIs<PlateState.Review>(p.state.value).review
        val rice = review.items[0]
        assertIs<PlateSource.Linked>(rice.source)
        // kcal reales de la base (130/100 g × 180 g), no los del modelo (100)
        assertEquals(234.0, rice.totals.kcal!!, 0.001)
        val sauce = review.items[1]
        assertTrue(sauce.isEstimated)
        assertEquals(100.0, sauce.totals.kcal!!, 0.001) // 200/100 g × 50 g, del modelo
        assertEquals(1, review.estimatedCount)
        assertEquals(334.0, review.totals.kcal, 0.001)
    }

    @Test
    fun looseSearchDropsTrailingWordsAndFlagsTheMatch() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz integral hervido", 100.0))))
        val p = presenter(h)
        p.onPhoto(photo); p.analyze("")
        val it = assertIs<PlateState.Review>(p.state.value).review.items.single()
        assertIs<PlateSource.Linked>(it.source)
        assertTrue(it.looseMatch)
    }

    @Test
    fun ownFoodsComeBeforeTheGenericOnes() = runTest {
        val own = CustomFood(7, "Mi arroz", null, Nutrients(111.0, 2.0, 24.0, 0.2), null, null, 0, 0)
        val catalog = FoodCatalog(FakeSearch(listOf(rice)), FakeCustom(listOf(own)))
        val picks = catalog.search("arroz")
        assertEquals(listOf("custom:7", "generic:1"), picks.map { it.result.key })
    }

    @Test
    fun nothingIsSavedUntilConfirmedAndThenLinkedGoInAsNormalEntriesAndTheRestAsQuick() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz blanco cocido", 200.0), item("Salsa rara", "salsa xyzzy", 50.0, kcal = 200.0))))
        val diary = FakeDiary()
        val p = presenter(h, diary)
        p.onPhoto(photo); p.analyze("con 200 g de arroz")
        assertTrue(diary.added.isEmpty())
        assertTrue(p.confirm("2026-11-10", MealType.LUNCH))
        assertEquals(2, diary.added.size)
        val normal = diary.added[0]
        assertFalse(normal.quick)
        assertEquals("generic:1", normal.barcode)
        assertEquals(Quantity(200.0, QuantityUnit.GRAMS), normal.quantity)
        assertEquals(MealType.LUNCH, normal.meal)
        val quick = diary.added[1]
        assertTrue(quick.quick)
        assertEquals("Salsa rara (estimado)", quick.name)
        assertEquals(100.0, quick.nutrients.kcal!!, 0.001)
    }

    @Test
    fun editingChangesFoodQuantityAndRemovesBeforeConfirming() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz blanco cocido", 100.0), item("Pollo", "pollo plancha", 100.0))))
        val diary = FakeDiary()
        val p = presenter(h, diary)
        p.onPhoto(photo); p.analyze("")
        val items = assertIs<PlateState.Review>(p.state.value).review.items
        p.setGrams(items[0].id, 250.0)
        p.remove(items[1].id)
        p.addManual("Postre", Nutrients(300.0, null, null, null))
        p.addLinked(PlateReviewHelpers.pick(chicken), 120.0)
        val review = assertIs<PlateState.Review>(p.state.value).review
        assertEquals(3, review.items.size)
        assertEquals(325.0 + 300.0 + 198.0, review.totals.kcal, 0.001)
        p.confirm("2026-11-10", MealType.DINNER)
        assertEquals(listOf(250.0, 100.0, 120.0), diary.added.map { it.quantity.amount })
        assertTrue(diary.added[1].quick)
    }

    @Test
    fun aFailedSaveDoesNotDuplicateWhatWasAlreadyWritten() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz blanco cocido", 100.0), item("Pollo", "pollo plancha", 100.0))))
        val diary = FakeDiary().also { it.failAtCall = 2 }
        val p = presenter(h, diary)
        p.onPhoto(photo); p.analyze("")
        assertFalse(p.confirm("2026-11-10", MealType.LUNCH))
        assertNotNull(assertIs<PlateState.Review>(p.state.value).error)
        assertEquals(1, diary.added.size)
        assertTrue(p.confirm("2026-11-10", MealType.LUNCH))
        assertEquals(2, diary.added.size)
    }

    @Test
    fun withoutKeyDisabledOrWithoutConsentNothingIsSent() = runTest {
        val noKey = harness(reply(emptyList()), key = null).also { it.module.settings.setEnabled(true); it.module.settings.setConsent(true) }
        assertEquals(AiError.NoKey, assertIs<PlateState.Blocked>(presenter(noKey).state.value).error)
        val off = harness(reply(emptyList()), ready = false)
        assertEquals(AiError.Disabled, assertIs<PlateState.Blocked>(presenter(off).state.value).error)
        val noConsent = harness(reply(emptyList()), ready = false).also { it.module.settings.setEnabled(true) }
        assertEquals(AiError.ConsentRequired, assertIs<PlateState.Blocked>(presenter(noConsent).state.value).error)
        assertTrue(noKey.requests.isEmpty() && off.requests.isEmpty() && noConsent.requests.isEmpty())
    }

    @Test
    fun dailyLimitBlocksTheAnalysis() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz", 100.0))))
        h.module.settings.setDailyLimit(1)
        val p = presenter(h)
        p.onPhoto(photo); p.analyze("")
        assertIs<PlateState.Review>(p.state.value)
        val p2 = presenter(h)
        assertEquals(AiError.LimitReached(1), assertIs<PlateState.Blocked>(p2.state.value).error)
        assertEquals(1, h.requests.size)
    }

    @Test
    fun refusalAndServerErrorsAreReportedAndRetryable() = runTest {
        val refusal = harness(reply(emptyList(), stop = "refusal"))
        val p = presenter(refusal)
        p.onPhoto(photo); p.analyze("")
        val f = assertIs<PlateState.Failed>(p.state.value)
        assertTrue(f.message.contains("rechazado"))

        val down = harness("""{"error":{"type":"api_error","message":"x"}}""", HttpStatusCode.InternalServerError)
        val p2 = presenter(down)
        p2.onPhoto(photo); p2.analyze("")
        assertTrue(assertIs<PlateState.Failed>(p2.state.value).retryable)

        val badKey = harness("""{"error":{"type":"authentication_error","message":"x"}}""", HttpStatusCode.Unauthorized)
        val p3 = presenter(badKey)
        p3.onPhoto(photo); p3.analyze("")
        assertTrue(assertIs<PlateState.Failed>(p3.state.value).message.contains("clave"))
    }

    @Test
    fun noFoodAndUnreadablePhotoHaveTheirOwnStates() = runTest {
        val h = harness(reply(emptyList(), isFood = false))
        val p = presenter(h)
        p.onPhoto(photo); p.analyze("")
        assertIs<PlateState.NoFood>(p.state.value)

        val bad = PlatePresenter(h.module.service, PlateAnalyzer(h.module.service), FoodCatalog(null, null), FakeDiary(), prepare = { null })
        bad.onPhoto(photo)
        assertFalse(assertIs<PlateState.Failed>(bad.state.value).retryable)
    }

    @Test
    fun requestCarriesTheImageNotesStructuredOutputAndNothingElse() = runTest {
        val h = harness(reply(listOf(item("Arroz", "arroz", 100.0))))
        val p = presenter(h)
        p.onPhoto(photo); p.analyze("con 200 g de arroz")
        val body = Json.parseToJsonElement(h.requests.single()).jsonObject
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals(listOf("image", "text"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertTrue(content[1].jsonObject["text"]!!.jsonPrimitive.content.contains("con 200 g de arroz"))
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertTrue(body["system"].toString().contains("NUNCA inventes ni menciones marcas"))
        // ni resumen del día ni datos personales
        assertFalse(h.requests.single().contains("kcal, proteína"))
    }

    @Test
    fun parseClampsAbsurdValuesAndDropsBrokenItems() {
        val json = Json.parseToJsonElement(
            """{"is_food":true,"note":"  ","items":[
              {"name":"","search_term":"x","grams":10,"portion":"","confidence":"high","kcal_per_100g":1,"protein_g_per_100g":1,"carbs_g_per_100g":1,"fat_g_per_100g":1},
              {"name":"Tarta","search_term":"tarta","grams":99999,"portion":"1 trozo","confidence":"???","kcal_per_100g":5000,"protein_g_per_100g":1,"carbs_g_per_100g":1,"fat_g_per_100g":1},
              {"name":"Cero","search_term":"cero","grams":0,"portion":"","confidence":"low","kcal_per_100g":10,"protein_g_per_100g":1,"carbs_g_per_100g":1,"fat_g_per_100g":1}]}""",
        )
        val a = assertNotNull(PlateAnalyzer.parse(json))
        val tarta = a.items.single()
        assertEquals(PlateAnalyzer.MAX_GRAMS, tarta.grams)
        assertEquals(PlateConfidence.LOW, tarta.confidence)
        assertNull(tarta.estimate) // 5000 kcal/100 g no es creíble
        assertNull(a.note)
        assertNull(PlateAnalyzer.parse(JsonPrimitive("hola")))
    }
}

private object PlateReviewHelpers {
    fun pick(p: Product) = app.luxion.fitarchiveapp.ui.screens.mergePicks(emptyList(), listOf(p)).single()
}
