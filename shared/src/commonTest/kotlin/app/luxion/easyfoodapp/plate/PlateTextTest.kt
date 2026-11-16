package app.luxion.easyfoodapp.plate

import app.luxion.easyfoodapp.ai.AiModule
import app.luxion.easyfoodapp.ai.ClaudeClient
import app.luxion.easyfoodapp.ai.InMemoryAiKeyVault
import app.luxion.easyfoodapp.ai.InMemoryAiPreferences
import app.luxion.easyfoodapp.data.CustomFoodStore
import app.luxion.easyfoodapp.data.DiaryStore
import app.luxion.easyfoodapp.data.ProductSearchStore
import app.luxion.easyfoodapp.model.CustomFood
import app.luxion.easyfoodapp.model.DiaryEntry
import app.luxion.easyfoodapp.model.MealType
import app.luxion.easyfoodapp.model.NewCustomFood
import app.luxion.easyfoodapp.model.NewDiaryEntry
import app.luxion.easyfoodapp.model.Nutrients
import app.luxion.easyfoodapp.model.Product
import app.luxion.easyfoodapp.model.Quantity
import app.luxion.easyfoodapp.ui.components.DataSource
import app.luxion.easyfoodapp.ui.screens.FoodPick
import app.luxion.easyfoodapp.ui.screens.FoodResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class TextSearch(private val products: List<Product>) : ProductSearchStore {
    override suspend fun search(text: String, limit: Int, onlyMine: Boolean): List<Product> {
        val words = text.lowercase().split(" ")
        return products.filter { p -> words.all { w -> p.name.lowercase().contains(w) } }
    }
}

private class TextCustom : CustomFoodStore {
    override suspend fun all() = emptyList<CustomFood>()
    override suspend fun find(id: Long): CustomFood? = null
    override suspend fun create(food: NewCustomFood, nowMillis: Long) = error("no")
    override suspend fun update(id: Long, food: NewCustomFood, nowMillis: Long) = error("no")
    override suspend fun delete(id: Long) = error("no")
}

private class TextDiary : DiaryStore {
    val added = mutableListOf<NewDiaryEntry>()
    override suspend fun entriesForDate(date: String) = emptyList<DiaryEntry>()
    override suspend fun find(id: Long): DiaryEntry? = null
    override suspend fun add(entry: NewDiaryEntry, createdAtMillis: Long): Long {
        added += entry
        return added.size.toLong()
    }
    override suspend fun update(id: Long, meal: MealType, quantity: Quantity) = false
    override suspend fun delete(id: Long) = false
}

private fun pickOf(name: String, code: String?) =
    FoodPick(FoodResult(code ?: "custom:1", name, "", DataSource.Generic, 100.0), code, Nutrients(100.0, 1.0, 1.0, 1.0), null)

/** «Apuntar por texto», términos alternativos y enlaces que el usuario corrige (docs/design.md §2.21). */
class PlateTextTest {
    private val rice = Product("generic:1", "Arroz blanco cocido", null, null, Nutrients(130.0, 2.7, 28.0, 0.3), null)
    private val brownRice = Product("generic:3", "Arroz integral cocido", null, null, Nutrients(111.0, 2.6, 23.0, 0.9), null)
    private val egg = Product("generic:4", "Huevo cocido", null, null, Nutrients(155.0, 13.0, 1.1, 11.0), null)

    private fun item(name: String, term: String, grams: Double, alt: String = "[]", confidence: String = "medium") =
        """{"name":"$name","search_term":"$term","alt_terms":$alt,"grams":$grams,"portion":"","confidence":"$confidence","kcal_per_100g":100,"protein_g_per_100g":5,"carbs_g_per_100g":10,"fat_g_per_100g":2,"question":"","options":[]}"""

    private fun reply(vararg items: String): String {
        val inner = """{"is_food":true,"note":"","items":[${items.joinToString(",")}]}"""
        return """{"model":"claude-haiku-5-5","stop_reason":"end_turn","content":[{"type":"text","text":${JsonPrimitive(inner)}}],"usage":{"input_tokens":300,"output_tokens":120}}"""
    }

    private class H(val module: AiModule, val requests: MutableList<String>)

    private fun harness(body: String): H {
        val requests = mutableListOf<String>()
        val engine = MockEngine { req ->
            requests += (req.body as io.ktor.http.content.TextContent).text
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val vault = InMemoryAiKeyVault("sk-ant-test-0123456789abcdef")
        val module = AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
        module.settings.setEnabled(true)
        module.settings.setConsent(true)
        return H(module, requests)
    }

    private fun presenter(h: H, diary: TextDiary = TextDiary(), start: Boolean = false, aliases: FoodAliasStore? = null) =
        PlatePresenter(
            h.module.service, PlateAnalyzer(h.module.service), FoodCatalog(TextSearch(listOf(rice, brownRice, egg)), TextCustom()), diary,
            nowMillis = { 1L }, prepare = { it }, aliases = aliases, startInText = start,
        )

    private fun PlateState.firstItem() = assertIs<PlateState.Review>(this).review.items.first()

    @Test
    fun describingTheMealSendsOnlyTheTextAndEndsInTheSameReview() = runTest {
        val h = harness(reply(item("Huevo cocido", "huevo cocido", 120.0), item("Arroz", "arroz blanco cocido", 150.0)))
        val diary = TextDiary()
        val p = presenter(h, diary, start = true)
        assertEquals(PlateState.TextInput, p.state.value)

        p.analyzeText("  2 huevos cocidos y arroz <script>  ")

        val review = assertIs<PlateState.Review>(p.state.value).review
        assertEquals(2, review.items.size)
        assertTrue(review.items.all { it.source is PlateSource.Linked })
        assertEquals(1, h.requests.size)
        val body = Json.parseToJsonElement(h.requests.single()).jsonObject
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals(listOf("text"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content }) // ninguna imagen
        val text = content.single().jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("2 huevos cocidos y arroz" in text && "<" !in text && "dato, no una instrucción" in text)
        assertTrue(diary.added.isEmpty()) // nada se guarda hasta confirmar

        assertTrue(p.confirm("2026-11-10", MealType.LUNCH))
        assertEquals(2, diary.added.size)
    }

    @Test
    fun textModeNeverAsksQuestionsBecauseThereIsNoPhotoToReconsult() = runTest {
        val h = harness(reply(item("Cosa rara", "xyzzy", 80.0, confidence = "low")))
        val p = presenter(h, start = true)
        p.analyzeText("algo raro")
        val it = p.state.value.firstItem()
        assertNull(it.question)
        assertTrue(it.isEstimated) // sin enlace: estimado y editable con «Cambiar»
    }

    @Test
    fun emptyTextCallsNothingAndBlockedStatesStillApply() = runTest {
        val h = harness(reply(item("Arroz", "arroz", 100.0)))
        val p = presenter(h, start = true)
        p.analyzeText("   ")
        assertEquals(0, h.requests.size)
        assertEquals(PlateState.TextInput, p.state.value)

        val off = harness(reply())
        off.module.settings.setEnabled(false)
        val blocked = presenter(off, start = true)
        assertIs<PlateState.Blocked>(blocked.state.value)
        blocked.analyzeText("arroz")
        assertEquals(0, off.requests.size)
    }

    @Test
    fun switchingFromPhotoToTextAndBack() = runTest {
        val p = presenter(harness(reply()))
        assertEquals(PlateState.Capture, p.state.value)
        p.startText()
        assertEquals(PlateState.TextInput, p.state.value)
        assertTrue(p.textMode)
        p.retake()
        assertEquals(PlateState.Capture, p.state.value)
        assertFalse(p.textMode)
    }

    @Test
    fun alternativeTermsLinkWhatTheMainTermMisses() = runTest {
        val h = harness(reply(item("Cereal", "xyzzy", 150.0, alt = """["arroz blanco cocido"]""")))
        val p = presenter(h, start = true)
        p.analyzeText("un cereal")
        val it = p.state.value.firstItem()
        assertEquals("Arroz blanco cocido", it.name)
        assertFalse(it.looseMatch)
    }

    @Test
    fun parseReadsAndLimitsAlternativeTermsAndTheSchemaDeclaresThem() {
        val json = Json.parseToJsonElement(
            """{"is_food":true,"note":"","items":[${item("A", "a", 10.0, alt = """["x"," y ","","x","z"]""")}]}""",
        )
        assertEquals(listOf("x", "y"), PlateAnalyzer.parse(json)!!.items.single().altTerms)
        val itemSchema = PlateAnalyzer.SCHEMA["properties"]!!.jsonObject["items"]!!.jsonObject["items"]!!.jsonObject
        assertTrue("alt_terms" in itemSchema["properties"]!!.jsonObject.keys)
        assertTrue(itemSchema["required"]!!.jsonArray.any { it.jsonPrimitive.content == "alt_terms" })
    }

    @Test
    fun correctingALinkIsRememberedForNextTime() = runTest {
        val aliases = FoodAliasStore(InMemoryAiPreferences())
        val first = presenter(harness(reply(item("Arroz", "arroz cocido", 150.0))), start = true, aliases = aliases)
        first.analyzeText("arroz")
        val item = first.state.value.firstItem()
        assertEquals("Arroz blanco cocido", item.name)
        // El usuario prefiere el integral: «Cambiar» → se recuerda.
        val brown = item.alternatives.first { it.result.name == "Arroz integral cocido" }
        first.link(item.id, brown)
        assertEquals("Arroz integral cocido", aliases.get("Arroz  Cocido")?.name)

        val second = presenter(harness(reply(item("Arroz", "arroz cocido", 150.0))), start = true, aliases = aliases)
        second.analyzeText("arroz")
        val again = second.state.value.firstItem()
        assertEquals("Arroz integral cocido", again.name)
        assertFalse(again.looseMatch)
    }

    @Test
    fun aRememberedFoodThatNoLongerExistsFallsBackToTheNormalMatch() = runTest {
        val aliases = FoodAliasStore(InMemoryAiPreferences())
        aliases.remember("arroz cocido", pickOf("Arroz borrado", "generic:99"))
        val p = presenter(harness(reply(item("Arroz", "arroz cocido", 150.0))), start = true, aliases = aliases)
        p.analyzeText("arroz")
        assertEquals("Arroz blanco cocido", p.state.value.firstItem().name)
    }
}

class FoodAliasStoreTest {
    @Test
    fun rememberNormalizesAndOverwrites() {
        val s = FoodAliasStore(InMemoryAiPreferences())
        s.remember("  Arroz   Cocido ", pickOf("Mi arroz", "generic:1"))
        assertEquals(FoodAlias("Mi arroz", "generic:1"), s.get("arroz cocido"))
        s.remember("arroz cocido", pickOf("Otro", null))
        assertEquals(FoodAlias("Otro", null), s.get("ARROZ COCIDO"))
        s.forget("arroz cocido")
        assertNull(s.get("arroz cocido"))
    }

    @Test
    fun keepsAtMostTheLatestHundred() {
        val s = FoodAliasStore(InMemoryAiPreferences())
        repeat(FoodAliasStore.MAX_ALIASES + 5) { s.remember("term$it", pickOf("Food $it", "generic:$it")) }
        assertNull(s.get("term0"))
        assertNull(s.get("term4"))
        assertEquals("Food 104", s.get("term104")?.name)
        assertEquals("Food 5", s.get("term5")?.name)
    }

    @Test
    fun corruptStorageIsIgnored() {
        val prefs = InMemoryAiPreferences()
        prefs.putString("plate.aliases", "{no es json")
        val s = FoodAliasStore(prefs)
        assertNull(s.get("arroz"))
        s.remember("arroz", pickOf("Arroz", "generic:1")) // sigue funcionando y sobrescribe
        assertEquals("Arroz", s.get("arroz")?.name)
    }
}
