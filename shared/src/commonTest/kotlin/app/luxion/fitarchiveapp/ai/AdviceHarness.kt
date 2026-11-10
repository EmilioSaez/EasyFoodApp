package app.luxion.fitarchiveapp.ai

import app.luxion.fitarchiveapp.model.DailyGoals
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.ui.screens.AiTabState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pestaña «IA» completa sobre almacenes en memoria y la API simulada (MockEngine): ni claves ni llamadas reales.
 * Guarda el cuerpo de cada petición en [requests] y responde lo último fijado con [reply].
 */
internal class AdviceHarness(
    key: String? = "sk-ant-test-0123456789abcdef",
    enabled: Boolean = true,
    ack: Boolean = true,
    limit: Int = 30,
    goals: DailyGoals = DailyGoals(kcal = 2000),
    meal: MealType = MealType.LUNCH,
) {
    val requests = mutableListOf<String>()
    val diary = FakeAdviceDiary()
    private var body = ""
    private var status = HttpStatusCode.OK

    val module: AiModule

    init {
        val engine = MockEngine { request ->
            requests += (request.body as TextContent).text
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val vault = InMemoryAiKeyVault(key)
        module = AiModule.create(vault, InMemoryAiPreferences(), { TODAY }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}, maxRetries = 0))
        module.settings.setEnabled(enabled)
        module.settings.setConsent(true)
        module.settings.setDailyLimit(limit)
        if (ack) module.settings.setAck(AiPrivacy.ADVICE_ACK)
    }

    val state = AiTabState(
        module = module,
        source = AiAdviceSource(diary, FakeAdviceGoals(goals), FakeAdviceFavorites(), FakeAdviceCustomFoods(), FakeAdviceRecipes(), FakeAdviceTemplates(), FakeAdviceWater(), null),
        diary = diary,
        today = { TODAY },
        defaultMeal = { meal },
        nowMillis = { 1_000L },
    )

    suspend fun refresh() = state.refresh()

    /** Respuesta 200 con [text] como texto del modelo (o un error HTTP si se indica [status]). */
    fun reply(text: String, stop: String = "end_turn", status: Int = 200) {
        this.status = HttpStatusCode.fromValue(status)
        body = if (status == 200) {
            """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${JsonPrimitive(text)}}],"usage":{"input_tokens":800,"output_tokens":120}}"""
        } else {
            """{"type":"error","error":{"type":"api_error","message":"x"}}"""
        }
    }

    companion object {
        const val TODAY = "2026-11-10"
    }
}
