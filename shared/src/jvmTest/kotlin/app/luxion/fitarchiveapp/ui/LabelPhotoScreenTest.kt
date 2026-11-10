package app.luxion.fitarchiveapp.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.ClaudeClient
import app.luxion.fitarchiveapp.ai.InMemoryAiKeyVault
import app.luxion.fitarchiveapp.ai.InMemoryAiPreferences
import app.luxion.fitarchiveapp.files.PhotoResult
import app.luxion.fitarchiveapp.files.PhotoSource
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.screens.LabelPhotoScreen
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Flujo de la foto de etiqueta con IA simulada (MockEngine) y una fuente de fotos falsa: sin cámara ni API reales. */
@OptIn(ExperimentalTestApi::class)
class LabelPhotoScreenTest {
    private val label = """{"name":"Galletas María","brand":"Dorada","ingredients":"Harina","serving_label":"4 galletas","serving_size_g":30,
        "per_100g":{"kcal":430,"fat_g":12,"saturated_fat_g":2.5,"carbs_g":72,"sugars_g":22,"fiber_g":null,"protein_g":7,"salt_g":0.6}}"""

    private fun envelope(text: String, stop: String = "end_turn") =
        """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${JsonPrimitive(text)}}],"usage":{"input_tokens":1500,"output_tokens":200}}"""

    private class Reply(var status: HttpStatusCode, var body: String)

    private fun photo(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB), "jpg", out)
        return out.toByteArray()
    }

    private val camera = object : PhotoSource {
        override val canTakePhoto = true
        override val canChooseFile = false
        override fun takePhoto(onResult: (PhotoResult) -> Unit) = onResult(PhotoResult.Done(photo()))
        override fun chooseFile(onResult: (PhotoResult) -> Unit) = onResult(PhotoResult.Cancelled)
    }

    private fun module(reply: Reply, key: String? = "sk-ant-test-0123456789abcdef", calls: IntArray = IntArray(1)): AiModule {
        val engine = MockEngine {
            calls[0]++
            respond(reply.body, reply.status, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val vault = InMemoryAiKeyVault(key)
        val m = AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
        m.settings.setEnabled(true)
        m.settings.setConsent(true)
        return m
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(nav: Navigator, ai: AiModule, source: PhotoSource = camera) =
        setContent { EasyFoodTheme { LabelPhotoScreen(nav, nav.current as Route.LabelPhoto, ai.service, source) } }

    @Test
    fun successOpensTheCustomFoodFormPrefilledWithTheBarcode() = runComposeUiTest {
        val nav = Navigator(Route.LabelPhoto(barcode = "8412345678900"))
        show(nav, module(Reply(HttpStatusCode.OK, envelope(label))))
        onNodeWithText("Hacer foto").performClick()

        waitUntil(timeoutMillis = 5_000) { nav.current is Route.CustomFood }
        val route = assertIs<Route.CustomFood>(nav.current)
        assertEquals("8412345678900", route.barcode)
        assertEquals("Galletas María", route.prefill!!.name)
        assertEquals("430", route.prefill!!.kcal)
        assertEquals("Dorada", route.brand)
        assertEquals(setOf("fiber"), route.unreadFields) // lo no leído queda marcado, no inventado
    }

    @Test
    fun anErrorKeepsThePhotoOnScreenAndRetryWorks() = runComposeUiTest {
        val reply = Reply(HttpStatusCode.InternalServerError, """{"error":{"message":"x"}}""")
        val nav = Navigator(Route.LabelPhoto())
        show(nav, module(reply))
        onNodeWithText("Hacer foto").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTextCount("Reintentar") > 0 }
        onNodeWithContentDescription("Foto de la etiqueta").assertIsDisplayed() // la foto sigue ahí
        onNodeWithText("Rellenar a mano").assertIsDisplayed()

        reply.status = HttpStatusCode.OK
        reply.body = envelope(label)
        onNodeWithText("Reintentar").performClick()
        waitUntil(timeoutMillis = 5_000) { nav.current is Route.CustomFood }
    }

    @Test
    fun aRefusalExplainsItAndDoesNotOfferRetry() = runComposeUiTest {
        val nav = Navigator(Route.LabelPhoto())
        show(nav, module(Reply(HttpStatusCode.OK, envelope("", stop = "refusal"))))
        onNodeWithText("Hacer foto").performClick()

        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTextCount("Probar con otra foto") > 0 }
        onNodeWithContentDescription("Foto de la etiqueta").assertIsDisplayed()
        onNodeWithText("Reintentar").assertDoesNotExist()
        assertTrue(nav.current is Route.LabelPhoto)
    }

    @Test
    fun withoutKeyNothingIsAskedAndManualEntryStaysAvailable() = runComposeUiTest {
        val calls = IntArray(1)
        val nav = Navigator(Route.LabelPhoto(barcode = "8412345678900"))
        show(nav, module(Reply(HttpStatusCode.OK, envelope(label)), key = null, calls = calls))

        onNodeWithText("Hacer foto").assertDoesNotExist()
        onNodeWithText("Ajustes de IA").assertIsDisplayed()
        onNodeWithText("Rellenar a mano").performClick()
        val route = assertIs<Route.CustomFood>(nav.current)
        assertEquals("8412345678900", route.barcode)
        assertEquals(null, route.unreadFields)
        assertEquals(0, calls[0])
    }

    @Test
    fun dailyLimitReachedIsExplainedBeforeTakingAPhoto() = runComposeUiTest {
        val calls = IntArray(1)
        val ai = module(Reply(HttpStatusCode.OK, envelope(label)), calls = calls)
        ai.settings.setDailyLimit(1)
        ai.settings.recordCall("2026-11-10", "claude-haiku-5-5", app.luxion.fitarchiveapp.ai.AiUsageTokens(1, 1))
        val nav = Navigator(Route.LabelPhoto())
        show(nav, ai)

        onNodeWithText("Hacer foto").assertDoesNotExist()
        onNodeWithText("Has alcanzado el límite de 1 llamadas de hoy. Súbelo en Ajustes de IA o espera a mañana.").assertIsDisplayed()
        onNodeWithText("Rellenar a mano").assertIsDisplayed()
        assertEquals(0, calls[0])
    }

    private fun androidx.compose.ui.test.ComposeUiTest.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
}
