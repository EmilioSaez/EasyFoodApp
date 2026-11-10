package app.luxion.fitarchiveapp.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiPrivacy
import app.luxion.fitarchiveapp.ai.ClaudeClient
import app.luxion.fitarchiveapp.ai.InMemoryAiKeyVault
import app.luxion.fitarchiveapp.ai.InMemoryAiPreferences
import app.luxion.fitarchiveapp.data.ProductLookup
import app.luxion.fitarchiveapp.data.ProductRemoteSource
import app.luxion.fitarchiveapp.data.ProductRepository
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightExerciseStore
import app.luxion.fitarchiveapp.data.SqlDelightFavoriteStore
import app.luxion.fitarchiveapp.data.SqlDelightGoalStore
import app.luxion.fitarchiveapp.data.SqlDelightMealTemplateStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.data.SqlDelightProductStore
import app.luxion.fitarchiveapp.data.SqlDelightRecipeStore
import app.luxion.fitarchiveapp.data.SqlDelightSettingsStore
import app.luxion.fitarchiveapp.data.SqlDelightWaterStore
import app.luxion.fitarchiveapp.data.SqlDelightWeightStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.export.SqlDelightImportTarget
import app.luxion.fitarchiveapp.model.NewWeightEntry
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/** La pestaña «IA» dentro de la [App] completa, con la API de Claude simulada (nunca llamadas ni claves reales). */
@OptIn(ExperimentalTestApi::class)
class AiTabFlowsTest {
    private val remote = object : ProductRemoteSource {
        override suspend fun findByBarcode(barcode: String) = ProductLookup.NotFound
    }

    private val options = """{"options":[
        {"name":"Yogur con nueces","amount":"150 g","kcal":210,"proteinG":10,"carbsG":12,"fatG":13,"reason":"Te faltan proteínas"},
        {"name":"Tortilla francesa","amount":"1 ración","kcal":220,"proteinG":14,"carbsG":2,"fatG":17,"reason":"Saciante"}]}"""

    private var apiCalls = 0

    /** Módulo de IA con clave de prueba, activado y con los avisos aceptados (salvo [ack]); responde [reply] sin red. */
    private fun ai(reply: String, ack: Boolean = true, enabled: Boolean = true, status: HttpStatusCode = HttpStatusCode.OK): AiModule {
        val engine = MockEngine {
            apiCalls++
            val body = if (status == HttpStatusCode.OK) {
                """{"model":"claude-haiku-5-5","stop_reason":"end_turn","content":[{"type":"text","text":${JsonPrimitive(reply)}}],"usage":{"input_tokens":800,"output_tokens":120}}"""
            } else """{"type":"error","error":{"type":"api_error","message":"x"}}"""
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val vault = InMemoryAiKeyVault("sk-ant-test-0123456789abcdef")
        return AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-04" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}, maxRetries = 0)).also {
            it.settings.setEnabled(enabled)
            it.settings.setConsent(true)
            if (ack) it.settings.setAck(AiPrivacy.ADVICE_ACK)
        }
    }

    private fun runApp(module: AiModule, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        kotlinx.coroutines.runBlocking {
            SqlDelightSettingsStore(db.settingsQueries).setMealsPerDay(4)
            SqlDelightWeightStore(db.weightEntryQueries).save(NewWeightEntry("2026-11-04", 80.0), 1L)
        }
        setContent {
            App(
                repository = ProductRepository(remote, SqlDelightProductStore(db.productQueries), nowMillis = System::currentTimeMillis),
                diaryStore = SqlDelightDiaryStore(db.diaryEntryQueries),
                goalStore = SqlDelightGoalStore(db.dailyGoalQueries),
                searchStore = SqlDelightProductSearchStore(db.productSearchQueries),
                customFoodStore = SqlDelightCustomFoodStore(db.customFoodQueries),
                weightStore = SqlDelightWeightStore(db.weightEntryQueries),
                recipeStore = SqlDelightRecipeStore(db.recipeQueries),
                settingsStore = SqlDelightSettingsStore(db.settingsQueries),
                waterStore = SqlDelightWaterStore(db.waterQueries),
                exerciseStore = SqlDelightExerciseStore(db.exerciseQueries),
                favoriteStore = SqlDelightFavoriteStore(db.favoriteFoodQueries),
                templateStore = SqlDelightMealTemplateStore(db.mealTemplateQueries),
                importTarget = SqlDelightImportTarget(db.productQueries, db.diaryEntryQueries, db.customFoodQueries, db.weightEntryQueries, db.recipeQueries, db.settingsQueries, db.favoriteFoodQueries),
                today = { "2026-11-04" },
                nowMinutes = { 17 * 60 + 30 }, // merienda
                ai = module,
            )
        }
        try { block() } finally { driver.close() }
    }

    private fun ComposeUiTest.waitForText(text: String, substring: Boolean = false) = waitUntil(timeoutMillis = 20_000) {
        val matcher = hasText(text, substring = substring)
        if (onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) return@waitUntil true
        val scrollables = onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
        for (i in 0 until scrollables) {
            try { onAllNodes(hasScrollAction())[i].performScrollToNode(matcher) } catch (_: AssertionError) {}
        }
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    private fun ComposeUiTest.openAiTab() {
        waitForText("Nada apuntado hoy")
        onAllNodesWithText("IA").onLast().performClick()
    }

    private fun ComposeUiTest.click(text: String) {
        waitForText(text)
        onNodeWithText(text).performScrollTo()
        onNodeWithText(text).performClick()
    }

    @Test
    fun suggestionIsAddedToTheDiaryAsAQuickEntry() = runApp(ai(options)) {
        openAiTab()
        waitForText("¿Qué como?")
        waitForText("¿Cómo voy?")
        waitForText("Pregunta lo que quieras")
        waitForText("Las respuestas de la IA son orientativas", substring = true)
        click("Sugerir comida")
        waitForText("Yogur con nueces (150 g)")
        waitForText("Tortilla francesa (1 ración)")
        waitForText("Añadir al diario")
        onAllNodesWithText("Añadir al diario")[0].performScrollTo()
        onAllNodesWithText("Añadir al diario")[0].performClick()
        waitForText("Añadido a Merienda")
        assertEquals(1, apiCalls)
        waitForText("Hoy te quedan 29 de 30 llamadas", substring = true)

        onNodeWithText("Diario").performClick()
        waitForText("Yogur con nueces (150 g)")
    }

    @Test
    fun theFeatureNoticeMustBeAcceptedBeforeAnyCall() = runApp(ai(options, ack = false)) {
        openAiTab()
        waitForText("Antes de empezar")
        waitForText(AiPrivacy.ADVICE_NOTICE)
        assertEquals(0, apiCalls)
        click("Entendido")
        click("Sugerir comida")
        waitForText("Yogur con nueces (150 g)")
        assertEquals(1, apiCalls)
    }

    @Test
    fun errorsAreShownInTheCard() = runApp(ai("", status = HttpStatusCode.Unauthorized)) {
        openAiTab()
        click("Sugerir comida")
        waitForText("La clave de API no es válida", substring = true)
    }

    @Test
    fun disabledAiShowsTheActivationState() = runApp(ai(options, enabled = false)) {
        openAiTab()
        waitForText("La IA está desactivada")
        assertEquals(0, apiCalls)
    }

    @Test
    fun aFreeQuestionGetsAnAnswerAndTheLimitBannerAppearsAtTheCap() = runApp(ai("Prueba con legumbres.").also { it.settings.setDailyLimit(1) }) {
        openAiTab()
        waitForText("Tu pregunta")
        onNodeWithText("Tu pregunta").performTextInput("¿Qué ceno?")
        click("Preguntar")
        waitForText("IA: Prueba con legumbres.")
        waitForText("Límite diario alcanzado")
    }
}
