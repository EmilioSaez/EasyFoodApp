package app.luxion.fitarchiveapp.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.ClaudeClient
import app.luxion.fitarchiveapp.ai.InMemoryAiKeyVault
import app.luxion.fitarchiveapp.ai.InMemoryAiPreferences
import app.luxion.fitarchiveapp.data.SqlDelightCustomFoodStore
import app.luxion.fitarchiveapp.data.SqlDelightDiaryStore
import app.luxion.fitarchiveapp.data.SqlDelightProductSearchStore
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import app.luxion.fitarchiveapp.model.MealType
import app.luxion.fitarchiveapp.model.NewCustomFood
import app.luxion.fitarchiveapp.model.Nutrients
import app.luxion.fitarchiveapp.ui.navigation.Navigator
import app.luxion.fitarchiveapp.ui.navigation.Route
import app.luxion.fitarchiveapp.ui.screens.MealChoices
import app.luxion.fitarchiveapp.ui.screens.PlatePhotoScreen
import app.luxion.fitarchiveapp.ui.theme.EasyFoodTheme
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** «Foto del plato» de punta a punta con una IA simulada (MockEngine): sin red ni claves reales. */
@OptIn(ExperimentalTestApi::class)
class PlatePhotoUiTest {
    private val inner = """{"is_food":true,"note":"","items":[
        {"name":"Arroz","search_term":"arroz blanco cocido","grams":200,"portion":"","confidence":"high","kcal_per_100g":100,"protein_g_per_100g":2,"carbs_g_per_100g":20,"fat_g_per_100g":1},
        {"name":"Salsa misteriosa","search_term":"salsa xyzzy","grams":50,"portion":"","confidence":"low","kcal_per_100g":200,"protein_g_per_100g":2,"carbs_g_per_100g":20,"fat_g_per_100g":10}]}"""
    private val okBody =
        """{"model":"claude-haiku-5-5","stop_reason":"end_turn","content":[{"type":"text","text":${JsonPrimitive(inner)}}],"usage":{"input_tokens":1500,"output_tokens":300}}"""

    private val png: ByteArray = ByteArrayOutputStream().also {
        ImageIO.write(BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB), "png", it)
    }.toByteArray()

    private fun run(key: String?, block: ComposeUiTest.(EasyFoodDatabase, Navigator) -> Unit) = runComposeUiTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        val customs = SqlDelightCustomFoodStore(db.customFoodQueries)
        runBlocking {
            customs.create(NewCustomFood("Arroz blanco cocido", null, Nutrients(130.0, 2.7, 28.0, 0.3)), 1L)
        }
        val engine = MockEngine { respond(okBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val vault = InMemoryAiKeyVault(key)
        val ai = AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-04" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
        ai.settings.setEnabled(true)
        ai.settings.setConsent(true)
        val navigator = Navigator(Route.Diary).also { it.push(Route.PlatePhoto(MealType.LUNCH)) }
        setContent {
            EasyFoodTheme {
                Surface {
                    PlatePhotoScreen(
                        navigator, Route.PlatePhoto(MealType.LUNCH), ai,
                        SqlDelightProductSearchStore(db.productSearchQueries), customs,
                        SqlDelightDiaryStore(db.diaryEntryQueries), { "2026-11-04" }, MealChoices(4) { 12 * 60 },
                        capture = { onPhoto, _, _ -> Button(onClick = { onPhoto(png) }) { Text("Foto de prueba") } },
                    )
                }
            }
        }
        try { block(db, navigator) } finally { driver.close() }
    }

    private fun entries(db: EasyFoodDatabase) =
        runBlocking { SqlDelightDiaryStore(db.diaryEntryQueries).entriesForDate("2026-11-04") }

    private fun ComposeUiTest.waitForText(text: String) =
        waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun photoToReviewToDiary() = run("sk-ant-test-0123456789abcdef") { db, navigator ->
        onNodeWithText("Foto de prueba").performClick()
        waitForText("Foto lista")
        onNodeWithText("Analizar el plato").performClick()
        waitForText("Revisa lo que ha visto la IA")
        waitForText("20–40 %")
        waitForText("De tu base")            // arroz enlazado con el alimento propio
        waitForText("ESTIMADO por la IA")    // la salsa no está en la base
        waitForText("Total: 360 kcal")       // 200 g × 130/100 (base) + 50 g × 200/100 (estimado)
        // Revisar no escribe nada
        assertEquals(0, entries(db).size)
        onNode(hasText("Añadir al diario", substring = true)).performClick()
        waitUntil(timeoutMillis = 20_000) { entries(db).size == 2 }
        val rows = entries(db)
        assertTrue(rows.all { it.meal == MealType.LUNCH })
        assertEquals(1, rows.count { it.quick })
        assertEquals(Route.Diary, navigator.current)
    }

    @Test
    fun withoutKeyItExplainsAndSendsNothing() = run(null) { _, _ ->
        waitForText("Configura tu clave")
        onNodeWithText("Ajustes de IA").assertIsDisplayed()
    }
}
