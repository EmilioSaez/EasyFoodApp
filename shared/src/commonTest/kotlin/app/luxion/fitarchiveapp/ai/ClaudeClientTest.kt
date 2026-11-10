package app.luxion.fitarchiveapp.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Todas las pruebas usan un motor simulado: nunca se llama a la API real. */
class ClaudeClientTest {
    private val secret = "sk-ant-test-0123456789abcdef"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private class Fixture(val client: ClaudeClient, val requests: MutableList<HttpRequestData>, val pauses: MutableList<Long>)

    private fun fixture(
        key: String? = secret,
        maxRetries: Int = 2,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Fixture {
        val requests = mutableListOf<HttpRequestData>()
        val pauses = mutableListOf<Long>()
        val engine = MockEngine { request ->
            requests += request
            handler(request)
        }
        return Fixture(ClaudeClient(HttpClient(engine), apiKey = { key }, maxRetries = maxRetries, pause = { pauses += it }), requests, pauses)
    }

    private fun ok(text: String, stop: String = "end_turn", extra: String = "") = """
        {"id":"msg_1","type":"message","role":"assistant","model":"claude-haiku-5-5","stop_reason":"$stop",$extra
         "content":[{"type":"thinking","thinking":"","signature":"x"},{"type":"text","text":${JsonPrimitive(text)}}],
         "usage":{"input_tokens":120,"output_tokens":30}}
    """.trimIndent()

    private val request = AiRequest(messages = listOf(AiMessage.user("hola")))

    private fun error(type: String, message: String) = """{"type":"error","error":{"type":"$type","message":"$message"}}"""

    @Test
    fun okResponseIsParsedAndUsesThinkingFreeTextBlock() = runTest {
        val f = fixture { respond(ok("Hola"), HttpStatusCode.OK, json) }
        val r = f.client.createMessage(request)
        val response = assertIs<AiResult.Ok<AiResponse>>(r).value
        assertEquals("Hola", response.text)
        assertEquals(AiUsageTokens(120, 30), response.usage)
        assertEquals(AiResult.Ok("Hola"), response.requireText())
    }

    @Test
    fun requestHasRequiredHeadersAndBody() = runTest {
        val f = fixture { respond(ok("ok"), HttpStatusCode.OK, json) }
        f.client.createMessage(request.copy(system = "sé breve", maxTokens = 300))
        val sent = f.requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("https://api.anthropic.com/v1/messages", sent.url.toString())
        assertEquals(secret, sent.headers["x-api-key"])
        assertEquals(ClaudeClient.API_VERSION, sent.headers["anthropic-version"])
        val body = Json.parseToJsonElement((sent.body as TextContent).text).jsonObject
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals(300, body["max_tokens"]!!.jsonPrimitive.content.toInt())
        assertEquals("sé breve", body["system"]!!.jsonPrimitive.content)
        // Esfuerzo fijado (el de Haiku 5.5 por defecto es medium) y nada de parámetros que ese modelo rechaza.
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        listOf("temperature", "top_p", "top_k").forEach { assertFalse(it in body, it) }
        val message = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals("text", message["content"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("user", (body["messages"] as JsonArray).last().jsonObject["role"]!!.jsonPrimitive.content) // sin prefill
    }

    @Test
    fun structuredOutputAndImageAreSerialized() = runTest {
        val f = fixture { respond(ok("{}"), HttpStatusCode.OK, json) }
        val schema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
        }
        val jpeg = byteArrayOf(1, 2, 3)
        f.client.createMessage(
            AiRequest(listOf(AiMessage.user(AiPart.Image(jpeg), AiPart.Text("lee la etiqueta"))), jsonSchema = schema, effort = AiEffort.MEDIUM),
        )
        val body = Json.parseToJsonElement((f.requests.single().body as TextContent).text).jsonObject
        val format = body["output_config"]!!.jsonObject["format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        assertEquals(schema, format["schema"])
        assertEquals("medium", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        val image = body["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray[0].jsonObject
        assertEquals("image", image["type"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", image["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        assertEquals("AQID", image["source"]!!.jsonObject["data"]!!.jsonPrimitive.content)
    }

    @Test
    fun refusalIsReportedWithCategory() = runTest {
        val f = fixture {
            respond(ok("", stop = "refusal", extra = """"stop_details":{"type":"refusal","category":"cyber","explanation":"x"},"""), HttpStatusCode.OK, json)
        }
        val response = (f.client.createMessage(request) as AiResult.Ok).value
        val err = assertIs<AiResult.Err>(response.requireText()).error
        assertEquals(AiError.Refusal("cyber"), err)
        assertTrue(err.message.contains("rechazado"))
    }

    @Test
    fun maxTokensIsTruncated() = runTest {
        val f = fixture { respond(ok("{\"a\":", stop = "max_tokens"), HttpStatusCode.OK, json) }
        val response = (f.client.createMessage(request) as AiResult.Ok).value
        assertEquals(AiResult.Err(AiError.Truncated), response.requireText())
    }

    @Test
    fun unauthorizedIsNotRetriedAndNeverLeaksTheKey() = runTest {
        val f = fixture { respond(error("authentication_error", "invalid x-api-key $secret"), HttpStatusCode.Unauthorized, json) }
        val err = assertIs<AiResult.Err>(f.client.createMessage(request)).error
        assertEquals(AiError.InvalidKey, err)
        assertEquals(1, f.requests.size)
        assertFalse(err.message.contains(secret))
        assertTrue(err.message.contains("clave"))
    }

    @Test
    fun badRequestMessageHasTheKeyScrubbed() = runTest {
        val f = fixture { respond(error("invalid_request_error", "mala petición con $secret"), HttpStatusCode.BadRequest, json) }
        val err = assertIs<AiError.BadRequest>(assertIs<AiResult.Err>(f.client.createMessage(request)).error)
        assertFalse(err.message.contains(secret))
    }

    @Test
    fun lowCreditBalanceIsABillingError() = runTest {
        val f = fixture { respond(error("invalid_request_error", "Your credit balance is too low"), HttpStatusCode.BadRequest, json) }
        assertEquals(AiError.Billing, assertIs<AiResult.Err>(f.client.createMessage(request)).error)
    }

    @Test
    fun rateLimitRetriesHonoringRetryAfterAndThenSucceeds() = runTest {
        var calls = 0
        val f = fixture {
            calls++
            if (calls == 1) respond(error("rate_limit_error", "slow down"), HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType to listOf("application/json"), "retry-after" to listOf("3")))
            else respond(ok("listo"), HttpStatusCode.OK, json)
        }
        val r = f.client.createMessage(request)
        assertEquals("listo", assertIs<AiResult.Ok<AiResponse>>(r).value.text)
        assertEquals(2, f.requests.size)
        assertEquals(listOf(3_000L), f.pauses)
    }

    @Test
    fun rateLimitGivesUpAfterRetriesWithAClearMessage() = runTest {
        val f = fixture { respond(error("rate_limit_error", "slow down"), HttpStatusCode.TooManyRequests, json) }
        val err = assertIs<AiResult.Err>(f.client.createMessage(request)).error
        assertEquals(AiError.RateLimited(null), err)
        assertEquals(3, f.requests.size) // 1 + 2 reintentos
        assertEquals(listOf(1_000L, 2_000L), f.pauses) // espera exponencial
        assertTrue(err.message.contains("Demasiadas peticiones"))
    }

    @Test
    fun longRetryAfterIsNotWaitedAndShowsTheNumber() = runTest {
        val f = fixture { respond(error("rate_limit_error", "x"), HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType to listOf("application/json"), "retry-after" to listOf("120"))) }
        val err = assertIs<AiResult.Err>(f.client.createMessage(request)).error
        assertEquals(AiError.RateLimited(120), err)
        assertEquals(1, f.requests.size)
        assertTrue(err.message.contains("120"))
    }

    @Test
    fun overloadedAndServerErrorsRetryThenReportServiceDown() = runTest {
        listOf(500, 529).forEach { status ->
            val f = fixture { respond(error("overloaded_error", "busy"), HttpStatusCode.fromValue(status), json) }
            assertEquals(AiError.ServerError, assertIs<AiResult.Err>(f.client.createMessage(request)).error)
            assertEquals(3, f.requests.size)
        }
    }

    @Test
    fun networkFailureRetriesAndReportsNoConnection() = runTest {
        val f = fixture { throw RuntimeException("sin red") }
        assertEquals(AiError.Network, assertIs<AiResult.Err>(f.client.createMessage(request)).error)
        assertEquals(3, f.requests.size)
    }

    @Test
    fun malformedJsonIsABadResponse() = runTest {
        val f = fixture { respond("{esto no es json", HttpStatusCode.OK, json) }
        val err = assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(f.client.createMessage(request)).error)
        assertTrue(err.message.contains("JSON mal formado"))
        assertEquals(1, f.requests.size)
    }

    @Test
    fun responseWithoutContentIsABadResponse() = runTest {
        val f = fixture { respond("""{"stop_reason":"end_turn"}""", HttpStatusCode.OK, json) }
        assertIs<AiError.BadResponse>(assertIs<AiResult.Err>(f.client.createMessage(request)).error)
    }

    @Test
    fun missingKeyDoesNotCallTheApi() = runTest {
        val f = fixture(key = "  ") { respond(ok("x"), HttpStatusCode.OK, json) }
        assertEquals(AiError.NoKey, assertIs<AiResult.Err>(f.client.createMessage(request)).error)
        assertTrue(f.requests.isEmpty())
    }

    @Test
    fun modelNotFoundNamesTheModel() = runTest {
        val f = fixture { respond(error("not_found_error", "model: x"), HttpStatusCode.NotFound, json) }
        val err = assertIs<AiResult.Err>(f.client.getModel("claude-xx")).error
        assertEquals(AiError.ModelNotFound("claude-xx"), err)
        assertTrue(err.message.contains("claude-xx"))
    }

    @Test
    fun getModelReadsCapabilities() = runTest {
        val f = fixture {
            respond(
                """{"id":"claude-haiku-5-5","display_name":"Claude Haiku 5.5","capabilities":{"image_input":{"supported":true},"structured_outputs":{"supported":false}}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val info = (f.client.getModel("claude-haiku-5-5") as AiResult.Ok).value
        assertEquals("https://api.anthropic.com/v1/models/claude-haiku-5-5", f.requests.single().url.toString())
        assertTrue(info.supportsImages)
        assertFalse(info.supportsStructuredOutputs)
        assertEquals("Claude Haiku 5.5", info.displayName)
        assertNull(f.requests.single().headers["content-type"])
    }
}
