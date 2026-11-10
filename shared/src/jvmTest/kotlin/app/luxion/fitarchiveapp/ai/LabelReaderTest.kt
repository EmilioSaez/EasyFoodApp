package app.luxion.fitarchiveapp.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

/** Lectura de etiquetas con respuestas simuladas (MockEngine): nunca se llama a la API real ni hay claves reales. */
class LabelReaderTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()
    private val fakeKey = "sk-ant-test-0123456789abcdef"

    private val label = """
        {"name":"Galletas María","brand":"Dorada","ingredients":"Harina de trigo, azúcar, aceite de girasol",
         "serving_label":"4 galletas (30 g)","serving_size_g":30,
         "per_100g":{"kcal":430,"fat_g":12,"saturated_fat_g":2.5,"carbs_g":72,"sugars_g":22,"fiber_g":3.1,"protein_g":7,"salt_g":0.6}}
    """.trimIndent()

    private fun envelope(text: String, stop: String = "end_turn") =
        """{"model":"claude-haiku-5-5","stop_reason":"$stop","content":[{"type":"text","text":${JsonPrimitive(text)}}],"usage":{"input_tokens":1500,"output_tokens":200}}"""

    private fun module(
        key: String? = fakeKey,
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = envelope(label),
        ready: Boolean = true,
    ): AiModule {
        val engine = MockEngine { request ->
            requests += request
            respond(body, status, jsonHeaders)
        }
        val vault = InMemoryAiKeyVault(key)
        val m = AiModule.create(vault, InMemoryAiPreferences(), { "2026-11-10" }, ClaudeClient(HttpClient(engine), apiKey = { vault.read() }, pause = {}))
        if (ready) {
            m.settings.setEnabled(true)
            m.settings.setConsent(true)
        }
        return m
    }

    private fun photo(width: Int = 2400, height: Int = 1800): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun sentBody(): JsonObject = Json.parseToJsonElement((requests.single().body as TextContent).text).jsonObject

    @Test
    fun readsTheLabelAndSendsAShrunkenPhotoWithTheSchema() = runTest {
        val m = module()
        val reading = (LabelReader(m.service).read(photo()) as AiResult.Ok).value

        assertEquals("Galletas María", reading.name)
        assertEquals("Dorada", reading.brand)
        assertEquals("4 galletas (30 g)", reading.servingLabel)
        assertEquals(30.0, reading.servingSizeG)
        assertEquals(430.0, reading.nutrients.kcal)
        assertEquals(12.0, reading.nutrients.fatG)
        assertEquals(2.5, reading.nutrients.saturatedFatG)
        assertEquals(72.0, reading.nutrients.carbsG)
        assertEquals(22.0, reading.nutrients.sugarsG)
        assertEquals(3.1, reading.nutrients.fiberG)
        assertEquals(7.0, reading.nutrients.proteinG)
        assertEquals(0.6, reading.nutrients.saltG)
        assertTrue(reading.unreadFields.isEmpty())

        val body = sentBody()
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals(LabelReader.SCHEMA, format["schema"])
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val content = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        val image = content.first { it.jsonObject["type"]!!.jsonPrimitive.content == "image" }.jsonObject["source"]!!.jsonObject
        assertEquals("image/jpeg", image["media_type"]!!.jsonPrimitive.content)
        val sent = ImageIO.read(kotlin.io.encoding.Base64.decode(image["data"]!!.jsonPrimitive.content).inputStream())
        assertEquals(AI_IMAGE_MAX_SIDE, maxOf(sent.width, sent.height)) // 2400x1800 llega a 1024x768
        assertEquals(1, requests.size)
        assertEquals(1, m.settings.usage("2026-11-10").callsToday) // cuenta para el tope diario
    }

    @Test
    fun whatCannotBeReadStaysNullAndIsFlaggedNeverInvented() = runTest {
        val partial = """{"name":null,"brand":"","ingredients":null,"serving_label":null,"serving_size_g":null,
            "per_100g":{"kcal":250,"fat_g":null,"saturated_fat_g":null,"carbs_g":30,"sugars_g":null,"fiber_g":null,"protein_g":-4,"salt_g":"mucha"}}"""
        val reading = (LabelReader(module(body = envelope(partial)).service).read(photo()) as AiResult.Ok).value

        assertNull(reading.name)
        assertNull(reading.brand) // texto vacío = no leído
        assertEquals(250.0, reading.nutrients.kcal)
        assertNull(reading.nutrients.fatG)
        assertNull(reading.nutrients.proteinG) // negativo: se descarta
        assertNull(reading.nutrients.saltG) // no numérico: se descarta
        assertEquals(
            setOf("name", "servingSize", "protein", "fat", "saturated", "sugars", "fiber", "salt"),
            reading.unreadFields,
        )
        assertFalse(reading.isEmpty)
    }

    @Test
    fun aPictureThatIsNotALabelComesBackEmpty() = runTest {
        val nothing = """{"name":null,"brand":null,"ingredients":null,"serving_label":null,"serving_size_g":null,
            "per_100g":{"kcal":null,"fat_g":null,"saturated_fat_g":null,"carbs_g":null,"sugars_g":null,"fiber_g":null,"protein_g":null,"salt_g":null}}"""
        assertTrue((LabelReader(module(body = envelope(nothing)).service).read(photo()) as AiResult.Ok).value.isEmpty)
    }

    @Test
    fun withoutKeyDisabledConsentOrLimitNothingLeavesTheDevice() = runTest {
        assertEquals(AiError.NoKey, assertIs<AiResult.Err>(LabelReader(module(key = null).service).read(photo())).error)
        assertEquals(AiError.Disabled, assertIs<AiResult.Err>(LabelReader(module(ready = false).service).read(photo())).error)
        val noConsent = module().also { it.settings.setConsent(false) }
        assertEquals(AiError.ConsentRequired, assertIs<AiResult.Err>(LabelReader(noConsent.service).read(photo())).error)
        assertTrue(requests.isEmpty())
        val limited = module().also { it.settings.setDailyLimit(1) }
        LabelReader(limited.service).read(photo())
        assertEquals(AiError.LimitReached(1), assertIs<AiResult.Err>(LabelReader(limited.service).read(photo())).error)
        assertEquals(1, requests.size) // solo la llamada permitida
    }

    @Test
    fun apiFailuresBecomeClearErrors() = runTest {
        val refusal = module(body = envelope("", stop = "refusal"))
        assertIs<AiError.Refusal>(assertIs<AiResult.Err>(LabelReader(refusal.service).read(photo())).error)

        val badKey = module(status = HttpStatusCode.Unauthorized, body = """{"error":{"message":"x"}}""")
        assertEquals(AiError.InvalidKey, assertIs<AiResult.Err>(LabelReader(badKey.service).read(photo())).error)

        val notJson = module(body = envelope("esto no es json"))
        assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(LabelReader(notJson.service).read(photo())).error)

        val notObject = module(body = envelope("[1,2]"))
        assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(LabelReader(notObject.service).read(photo())).error)

        val cut = module(body = envelope("{\"name\":", stop = "max_tokens"))
        assertEquals(AiError.Truncated, assertIs<AiResult.Err>(LabelReader(cut.service).read(photo())).error)
    }

    @Test
    fun anUndecodableImageIsRejectedBeforeAnyCall() = runTest {
        val m = module()
        assertIs<AiError.BadRequest>(assertIs<AiResult.Err>(LabelReader(m.service).read(byteArrayOf(1, 2, 3))).error)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun theKeyNeverAppearsInTheBodyOrInErrors() = runTest {
        val m = module(status = HttpStatusCode.Unauthorized, body = """{"error":{"message":"x"}}""")
        val err = assertIs<AiResult.Err>(LabelReader(m.service).read(photo())).error
        assertFalse(fakeKey in err.message)
        assertFalse(fakeKey in (requests.single().body as TextContent).text)
    }

    @Test
    fun schemaIsStrictEverywhere() {
        fun check(o: JsonObject) {
            if (o["type"]?.jsonPrimitive?.content == "object") {
                assertEquals(JsonPrimitive(false), o["additionalProperties"])
                val props = o["properties"]!!.jsonObject.keys
                assertEquals(props, (o["required"] as JsonArray).map { it.jsonPrimitive.content }.toSet())
            }
            o["properties"]?.jsonObject?.values?.forEach { check(it.jsonObject) }
        }
        check(LabelReader.SCHEMA)
    }
}
