package app.luxion.fitarchiveapp.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AiServiceTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var day = "2026-11-10"
    private var calls = 0

    private fun message(text: String, stop: String = "end_turn") =
        """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}],"usage":{"input_tokens":1000,"output_tokens":200}}"""

    private fun module(
        key: String? = "sk-ant-test-0123456789abcdef",
        body: String = message("hola"),
        status: HttpStatusCode = HttpStatusCode.OK,
    ): AiModule {
        val engine = MockEngine {
            calls++
            respond(body, status, jsonHeaders)
        }
        val vault = InMemoryAiKeyVault(key)
        return AiModule.create(vault, InMemoryAiPreferences(), { day }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
    }

    private fun AiModule.enable() {
        settings.setEnabled(true)
        settings.setConsent(true)
    }

    private val request = AiRequest(listOf(AiMessage.user("hola")))

    @Test
    fun withoutKeyOrActivationOrConsentNothingIsSent() = runTest {
        val noKey = module(key = null).also { it.settings.setEnabled(true); it.settings.setConsent(true) }
        assertEquals(AiError.NoKey, assertIs<AiResult.Err>(noKey.service.send(request)).error)

        val disabled = module()
        assertEquals(AiError.Disabled, assertIs<AiResult.Err>(disabled.service.send(request)).error)

        val noConsent = module().also { it.settings.setEnabled(true) }
        assertEquals(AiError.ConsentRequired, assertIs<AiResult.Err>(noConsent.service.send(request)).error)
        assertEquals(0, calls)
    }

    @Test
    fun successfulCallIsCountedAndPriced() = runTest {
        val m = module().also { it.enable() }
        assertEquals(AiResult.Ok("hola"), m.service.askText(request))
        val usage = m.settings.usage(day)
        assertEquals(1, usage.callsToday)
        assertEquals(1L, usage.callsTotal)
        assertEquals(1000L, usage.inputTokensTotal)
        assertEquals(200L, usage.outputTokensTotal)
        // Haiku 5.5: 1000 × 0,10 + 200 × 0,50 = 200 µ$
        assertEquals(200L, usage.estimatedMicroUsd)
        assertTrue(m.service.isReady())
    }

    @Test
    fun dailyLimitBlocksFurtherCallsAndResetsTomorrow() = runTest {
        val m = module().also { it.enable(); it.settings.setDailyLimit(2) }
        m.service.send(request)
        m.service.send(request)
        val blocked = assertIs<AiResult.Err>(m.service.send(request)).error
        assertEquals(AiError.LimitReached(2), blocked)
        assertTrue(blocked.message.contains("2"))
        assertEquals(2, calls)
        day = "2026-11-11"
        assertIs<AiResult.Ok<AiResponse>>(m.service.send(request))
        assertEquals(1, m.settings.usage(day).callsToday)
        assertEquals(3L, m.settings.usage(day).callsTotal)
    }

    @Test
    fun failedCallsDoNotCountTowardTheLimit() = runTest {
        val m = module(body = """{"error":{"message":"x"}}""", status = HttpStatusCode.Unauthorized).also { it.enable() }
        assertEquals(AiError.InvalidKey, assertIs<AiResult.Err>(m.service.send(request)).error)
        assertEquals(0, m.settings.usage(day).callsToday)
    }

    @Test
    fun refusalCountsAsACallButReturnsAnError() = runTest {
        val m = module(body = message("", stop = "refusal")).also { it.enable() }
        assertIs<AiError.Refusal>(assertIs<AiResult.Err>(m.service.askText(request)).error)
        assertEquals(1, m.settings.usage(day).callsToday) // la respuesta se factura
    }

    @Test
    fun askJsonParsesStructuredOutput() = runTest {
        val m = module(body = message("""{"kcal":120}""")).also { it.enable() }
        val schema: JsonObject = buildJsonObject { put("type", "object") }
        val value = (m.service.askJson(request.copy(jsonSchema = schema)) as AiResult.Ok).value
        assertEquals("120", value.jsonObject["kcal"]!!.jsonPrimitive.content)
    }

    @Test
    fun askJsonWithBrokenJsonIsABadResponse() = runTest {
        val m = module(body = message("{kcal: ")).also { it.enable() }
        val schema = buildJsonObject { put("type", "object") }
        assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(m.service.askJson(request.copy(jsonSchema = schema))).error)
    }

    @Test
    fun testConnectionDoesNotNeedConsentNorCountAsACall() = runTest {
        val m = module(body = """{"id":"claude-haiku-5-5","display_name":"Haiku","capabilities":{"image_input":{"supported":true}}}""")
        val info = (m.service.testConnection() as AiResult.Ok).value
        assertTrue(info.supportsImages)
        assertEquals(0, m.settings.usage(day).callsToday)
    }

    @Test
    fun usedModelComesFromSettings() = runTest {
        val m = module().also { it.enable(); it.settings.setModel("claude-sonnet-5-5") }
        m.service.send(request)
        // Sonnet 5.5: 1000 × 2 + 200 × 10 = 4000 µ$
        assertEquals(4000L, m.settings.usage(day).estimatedMicroUsd)
    }

    @Test
    fun settingsDefaultsAreSafe() {
        val s = AiSettingsStore(InMemoryAiPreferences()).load()
        assertFalse(s.enabled)
        assertFalse(s.consentAccepted)
        assertEquals("claude-haiku-5-5", s.model)
        assertEquals(AiSettingsStore.DEFAULT_DAILY_LIMIT, s.dailyLimit)
    }

    @Test
    fun dailyLimitIsClamped() {
        val store = AiSettingsStore(InMemoryAiPreferences())
        store.setDailyLimit(0)
        assertEquals(AiSettingsStore.MIN_DAILY_LIMIT, store.load().dailyLimit)
        store.setDailyLimit(100_000)
        assertEquals(AiSettingsStore.MAX_DAILY_LIMIT, store.load().dailyLimit)
    }

    @Test
    fun resetUsageClearsCounters() {
        val store = AiSettingsStore(InMemoryAiPreferences())
        store.recordCall(day, "claude-haiku-5-5", AiUsageTokens(10, 10))
        store.resetUsage()
        assertEquals(AiUsage(0, 0, 0, 0, 0), store.usage(day))
    }

    @Test
    fun errorMessagesAreInSpanishAndFreeOfSecrets() {
        val all = listOf(
            AiError.NoKey, AiError.Disabled, AiError.ConsentRequired, AiError.InvalidKey, AiError.Billing, AiError.ServerError,
            AiError.Network, AiError.Truncated, AiError.TooLarge, AiError.RateLimited(5), AiError.Refusal(null),
        )
        all.forEach { assertTrue(it.message.isNotBlank() && !it.message.contains("sk-ant"), it.toString()) }
    }
}
