package app.luxion.fitarchiveapp.ai

import app.luxion.fitarchiveapp.model.MealType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Prompts, esquemas y lectura de respuestas de las sugerencias y consejos, con la API simulada (nunca llamadas reales). */
class AiAdviceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private var reply: String = message("{}")

    private fun message(text: String, stop: String = "end_turn") =
        """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${JsonPrimitive(text)}}],"usage":{"input_tokens":900,"output_tokens":150}}"""

    private fun module(): AiModule {
        val engine = MockEngine { request ->
            requests += request
            respond(reply, HttpStatusCode.OK, jsonHeaders)
        }
        val vault = InMemoryAiKeyVault("sk-ant-test-0123456789abcdef")
        return AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
            .also { it.settings.setEnabled(true); it.settings.setConsent(true) }
    }

    private fun bodyOf(r: HttpRequestData): JsonObject = Json.parseToJsonElement((r.body as TextContent).text).jsonObject

    private val ctx = AiAdviceContext(
        day = AiDaySummary(1200.0, 60.0, 130.0, 40.0, goalKcal = 2000.0, goalProteinG = 100.0, goalCarbsG = 220.0, goalFatG = 70.0),
        currentMeal = MealType.DINNER,
        currentMealFoods = listOf("Ensalada · 90 kcal"),
        candidates = AiCandidates(favorites = listOf(AiCandidate("Yogur natural", 60, "125 g"))),
    )

    private val options = """{"options":[
        {"name":"Tortilla de dos huevos","amount":"1 ración","kcal":220,"proteinG":14,"carbsG":2,"fatG":17,"reason":"Proteína sin muchos hidratos"},
        {"name":"Yogur natural","amount":"125 g","kcal":60,"proteinG":5,"carbsG":6,"fatG":2,"reason":"Ligero"},
        {"name":"Pechuga a la plancha","amount":"150 g","kcal":165,"proteinG":31,"carbsG":0,"fatG":4,"reason":"Te faltan 40 g de proteína"}]}"""

    @Test
    fun suggestionsSendOnlyTheMinimumSummaryAndAskForStructuredOutput() = runTest {
        reply = message(options)
        val result = AiAdvisor(module().service).suggestMeals(ctx, "sin gluten, rápido")
        val parsed = assertIs<AiResult.Ok<List<MealSuggestion>>>(result).value
        assertEquals(listOf("Tortilla de dos huevos", "Yogur natural", "Pechuga a la plancha"), parsed.map { it.name })
        assertEquals("Tortilla de dos huevos (1 ración)", parsed.first().diaryName)

        val body = bodyOf(requests.single())
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals("json_schema", body["output_config"]!!.jsonObject["format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val system = body["system"]!!.jsonPrimitive.content
        assertTrue("Quedan 800 kcal." in system, system)
        assertTrue("Comida actual: cena." in system, system)
        assertTrue("Yogur natural · 125 g · 60 kcal" in system, system)
        assertTrue("No eres médico" in system)
        val user = body["messages"]!!.jsonArray.single().jsonObject["content"].toString()
        assertTrue("sin gluten, rápido" in user)
        // Nada de cuenta ni de historial: solo lo que construye el resumen.
        val all = body.toString()
        assertFalse("sk-ant" in all, "la clave solo va en la cabecera")
        assertFalse("@" in all)
    }

    @Test
    fun suggestionsTolerateBadOptionsAndCapAtFive() = runTest {
        val many = (1..8).joinToString(",") { """{"name":"Op $it","amount":"1","kcal":100,"proteinG":1,"carbsG":1,"fatG":1,"reason":"r"}""" }
        val bad = """{"name":"","amount":"x","kcal":50,"proteinG":1,"carbsG":1,"fatG":1,"reason":"sin nombre"},{"name":"Absurda","amount":"x","kcal":99999,"proteinG":1,"carbsG":1,"fatG":1,"reason":"kcal"}"""
        reply = message("""{"options":[$bad,$many]}""")
        val r = assertIs<AiResult.Ok<List<MealSuggestion>>>(AiAdvisor(module().service).suggestMeals(ctx, ""))
        assertEquals(AiAdviceLimits.MAX_OPTIONS, r.value.size)
        assertEquals("Op 1", r.value.first().name)
    }

    @Test
    fun noValidOptionsIsABadResponse() = runTest {
        reply = message("""{"options":[]}""")
        val e = assertIs<AiResult.Err>(AiAdvisor(module().service).suggestMeals(ctx, ""))
        assertIs<AiError.BadResponse>(e.error)
    }

    @Test
    fun refusalAndTruncationAreMappedToErrors() = runTest {
        val m = module()
        reply = message("", stop = "refusal")
        assertIs<AiError.Refusal>(assertIs<AiResult.Err>(AiAdvisor(m.service).suggestMeals(ctx, "")).error)
        reply = message("""{"options":[""", stop = "max_tokens")
        assertEquals(AiError.Truncated, assertIs<AiResult.Err>(AiAdvisor(m.service).review(ctx, AiReviewPeriod.DAY)).error)
        reply = message("esto no es json")
        assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(AiAdvisor(m.service).review(ctx, AiReviewPeriod.DAY)).error)
    }

    @Test
    fun reviewParsesSummaryAndPointsAndSendsTheWeekOnlyForWeek() = runTest {
        reply = message(
            """{"summary":"Vas bien de kcal pero corto de proteína.","points":[
            {"topic":"proteína","status":"bajo","advice":"Añade yogur o pollo."},
            {"topic":"sal","status":"raro","advice":"Poca sal, bien."},
            {"topic":"agua","status":"ok","advice":""}]}""",
        )
        val week = AiWeekSummary(3, 3, 1900.0, 80.0, 200.0, 65.0, 2000, 2)
        val r = assertIs<AiResult.Ok<AiReview>>(AiAdvisor(module().service).review(ctx.copy(week = week), AiReviewPeriod.WEEK)).value
        assertEquals("Vas bien de kcal pero corto de proteína.", r.summary)
        assertEquals(listOf("bajo", "ok"), r.points.map { it.status }, "estado desconocido = ok; consejo vacío se descarta")
        val system = bodyOf(requests.single())["system"]!!.jsonPrimitive.content
        assertTrue("Semana en curso: 3 de 3 días con registro." in system, system)

        requests.clear()
        AiAdvisor(module().service).review(ctx.copy(week = week), AiReviewPeriod.DAY)
        assertFalse("Semana en curso" in bodyOf(requests.single())["system"]!!.jsonPrimitive.content)
    }

    @Test
    fun askKeepsOnlyTheLastMessagesAndCleansTheQuestion() = runTest {
        reply = message("  Prueba con legumbres.  ")
        val history = (1..6).map { AiChatTurn(it % 2 == 1, "mensaje $it") } // u a u a u a
        val r = AiAdvisor(module().service).ask(ctx, "¿Y <b>cenar</b>\nligero?", history)
        assertEquals("Prueba con legumbres.", assertIs<AiResult.Ok<String>>(r).value)

        val messages = bodyOf(requests.single())["messages"]!!.jsonArray
        // Últimos 4 (u a u a) + la pregunta nueva = 5 mensajes, empezando y acabando en el usuario.
        assertEquals(listOf("user", "assistant", "user", "assistant", "user"), messages.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        val last = messages.last().jsonObject["content"].toString()
        assertTrue("¿Y b cenar /b ligero?" in last, last)
        assertFalse("<b>" in last)
        assertEquals(AiAdviceLimits.CHAT_TOKENS, bodyOf(requests.single())["max_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun askDropsALeadingAssistantMessageSoTheConversationStartsWithTheUser() = runTest {
        reply = message("ok")
        val history = listOf(AiChatTurn(false, "huérfano"), AiChatTurn(true, "hola"), AiChatTurn(false, "buenas"))
        AiAdvisor(module().service).ask(ctx, "otra", history)
        val roles = bodyOf(requests.single())["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "assistant", "user"), roles)
    }

    @Test
    fun theDailyLimitStopsEveryFeatureBeforeAnyRequest() = runTest {
        val m = module()
        m.settings.setDailyLimit(1)
        reply = message(options)
        assertIs<AiResult.Ok<*>>(AiAdvisor(m.service).suggestMeals(ctx, ""))
        val blocked = assertIs<AiResult.Err>(AiAdvisor(m.service).ask(ctx, "¿hola?", emptyList()))
        assertEquals(AiError.LimitReached(1), blocked.error)
        assertEquals(1, requests.size)
    }

    @Test
    fun userTextIsCappedAndFencedAsData() {
        val long = "x".repeat(1000)
        val user = AiAdvicePrompts.suggestUser(long)
        assertTrue(user.length < AiAdviceLimits.MAX_PREFERENCES + 80)
        assertTrue("<usuario>" in user && "</usuario>" in user)
        assertEquals("¿Qué puedo comer ahora?", AiAdvicePrompts.suggestUser("   "))
    }

    @Test
    fun schemasAreStrictObjects() {
        fun check(o: JsonObject) {
            if (o["type"]?.jsonPrimitive?.content == "object") assertEquals(false, o["additionalProperties"]!!.jsonPrimitive.content.toBoolean())
            o.values.forEach { v ->
                if (v is JsonObject) check(v)
                if (v is JsonArray) v.forEach { if (it is JsonObject) check(it) }
            }
        }
        check(AiAdvicePrompts.suggestSchema)
        check(AiAdvicePrompts.reviewSchema)
    }
}
