package app.luxion.fitarchiveapp.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/** Parte de un mensaje: texto o imagen JPEG (ya reducida con [downscaleToJpeg]). */
sealed interface AiPart {
    data class Text(val text: String) : AiPart
    class Image(val jpeg: ByteArray, val mediaType: String = "image/jpeg") : AiPart
}

data class AiMessage(val role: String, val parts: List<AiPart>) {
    companion object {
        fun user(text: String) = AiMessage("user", listOf(AiPart.Text(text)))
        fun user(vararg parts: AiPart) = AiMessage("user", parts.toList())
    }
}

/**
 * Petición a `POST /v1/messages`. [jsonSchema] activa la salida estructurada (`output_config.format`); el esquema
 * debe llevar `additionalProperties: false` en cada objeto. [model] lo rellena [AiService] con el de los ajustes.
 */
data class AiRequest(
    val messages: List<AiMessage>,
    val system: String? = null,
    val maxTokens: Int = 2048,
    val effort: AiEffort = AiEffort.LOW,
    val jsonSchema: JsonObject? = null,
    val model: String = AiModels.DEFAULT_ID,
)

data class AiUsageTokens(val inputTokens: Long, val outputTokens: Long)

/** Respuesta 200 de la API. Mira [stopReason] antes de fiarte de [text]: usa [requireText]. */
data class AiResponse(
    val model: String,
    val stopReason: String?,
    val stopCategory: String?,
    val text: String,
    val usage: AiUsageTokens,
) {
    /** El texto si la respuesta terminó bien; `Refusal`/`Truncated`/`BadResponse` si no. */
    fun requireText(): AiResult<String> = when {
        stopReason == "refusal" -> AiResult.Err(AiError.Refusal(stopCategory))
        stopReason == "max_tokens" -> AiResult.Err(AiError.Truncated)
        text.isBlank() -> AiResult.Err(AiError.BadResponse("respuesta vacía"))
        else -> AiResult.Ok(text)
    }
}

/** Capacidades del modelo según la Models API (`GET /v1/models/{id}`). */
data class AiModelInfo(
    val id: String,
    val displayName: String,
    val supportsImages: Boolean,
    val supportsStructuredOutputs: Boolean,
)

/**
 * Cliente mínimo de la API de Claude (no hay SDK oficial de KMP: HTTP directo con Ktor).
 * La clave se pide en cada llamada a [apiKey] (puede cambiar en ajustes) y nunca se registra ni se incluye en errores.
 * Reintenta con espera 429/5xx/529 y fallos de red hasta [maxRetries] veces; 401/403/400 no se reintentan.
 */
class ClaudeClient(
    private val http: HttpClient = defaultHttpClient(),
    private val apiKey: () -> String?,
    private val baseUrl: String = "https://api.anthropic.com",
    private val maxRetries: Int = 2,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun createMessage(request: AiRequest): AiResult<AiResponse> {
        val key = apiKey()?.trim().orEmpty()
        if (key.isEmpty()) return AiResult.Err(AiError.NoKey)
        val body = buildBody(request).toString()
        val result = execute(key, request.model) {
            http.post("$baseUrl/v1/messages") {
                authHeaders(key)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (result) {
            is AiResult.Err -> result
            is AiResult.Ok -> parseMessage(result.value)
        }
    }

    /** Comprueba la clave y lee las capacidades del modelo. No envía ningún dato personal ni cuenta como llamada. */
    suspend fun getModel(model: String): AiResult<AiModelInfo> {
        val key = apiKey()?.trim().orEmpty()
        if (key.isEmpty()) return AiResult.Err(AiError.NoKey)
        val result = execute(key, model) { http.get("$baseUrl/v1/models/$model") { authHeaders(key) } }
        return when (result) {
            is AiResult.Err -> result
            is AiResult.Ok -> parseModel(model, result.value)
        }
    }

    private fun HttpRequestBuilder.authHeaders(key: String) {
        header("x-api-key", key)
        header("anthropic-version", API_VERSION)
    }

    /** Ejecuta la petición con reintentos y devuelve el cuerpo de una respuesta 2xx o el error ya traducido. */
    private suspend fun execute(key: String, model: String, call: suspend () -> HttpResponse): AiResult<String> {
        var attempt = 0
        while (true) {
            val outcome: Attempt = try {
                val response = call()
                val text = response.bodyAsText()
                val status = response.status.value
                val retryAfter = response.headers["retry-after"]?.trim()?.toLongOrNull()
                if (status in 200..299) Attempt.Done(text)
                else Attempt.Failed(classify(status, text, key, model, retryAfter), retryAfter)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Attempt.Failed(AiError.Network, null)
            }
            when (outcome) {
                is Attempt.Done -> return AiResult.Ok(outcome.body)
                is Attempt.Failed -> {
                    val wait = outcome.retryAfter?.times(1000) ?: (BASE_BACKOFF_MS shl attempt)
                    // Con Retry-After largo no se espera: se avisa al usuario con la cifra.
                    val worthRetrying = outcome.error.retryable && attempt < maxRetries && wait <= MAX_WAIT_MS
                    if (!worthRetrying) return AiResult.Err(outcome.error)
                    pause(wait)
                    attempt++
                }
            }
        }
    }

    private sealed interface Attempt {
        data class Done(val body: String) : Attempt
        data class Failed(val error: AiError, val retryAfter: Long?) : Attempt
    }

    private fun classify(status: Int, body: String, key: String, model: String, retryAfter: Long?): AiError {
        val message = errorMessage(body)?.replace(key, "[clave]")
        return when (status) {
            401 -> AiError.InvalidKey
            402 -> AiError.Billing
            403 -> AiError.Permission(message)
            404 -> AiError.ModelNotFound(model)
            413 -> AiError.TooLarge
            429 -> AiError.RateLimited(retryAfter)
            400 ->
                if (message?.contains("credit balance", ignoreCase = true) == true) AiError.Billing
                else AiError.BadRequest(message ?: "error 400")
            in 500..599 -> AiError.ServerError // 500, 502, 503, 504 y 529 (sobrecargada)
            else -> AiError.BadRequest(message ?: "error $status")
        }
    }

    private fun errorMessage(body: String): String? = try {
        (json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message") as? JsonPrimitive)?.contentOrNull
    } catch (e: Exception) {
        null
    }

    private fun parseMessage(body: String): AiResult<AiResponse> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return AiResult.Err(AiError.BadResponse("JSON mal formado"))
        }
        val content = root["content"] as? JsonArray
            ?: return AiResult.Err(AiError.BadResponse("falta «content»"))
        // Se leen los bloques por tipo: con razonamiento activo pueden llegar bloques `thinking` antes del texto.
        val text = content.mapNotNull { block ->
            val o = block as? JsonObject ?: return@mapNotNull null
            if ((o["type"] as? JsonPrimitive)?.contentOrNull == "text") (o["text"] as? JsonPrimitive)?.contentOrNull else null
        }.joinToString("")
        val usage = root["usage"] as? JsonObject
        val details = root["stop_details"] as? JsonObject
        return AiResult.Ok(
            AiResponse(
                model = (root["model"] as? JsonPrimitive)?.contentOrNull ?: "",
                stopReason = (root["stop_reason"] as? JsonPrimitive)?.contentOrNull,
                stopCategory = (details?.get("category") as? JsonPrimitive)?.contentOrNull,
                text = text,
                usage = AiUsageTokens(
                    inputTokens = (usage?.get("input_tokens") as? JsonPrimitive)?.longOrNull ?: 0L,
                    outputTokens = (usage?.get("output_tokens") as? JsonPrimitive)?.longOrNull ?: 0L,
                ),
            ),
        )
    }

    private fun parseModel(requested: String, body: String): AiResult<AiModelInfo> {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            return AiResult.Err(AiError.BadResponse("JSON mal formado"))
        }
        fun supported(name: String): Boolean =
            (((root["capabilities"] as? JsonObject)?.get(name) as? JsonObject)?.get("supported") as? JsonPrimitive)?.contentOrNull == "true"
        return AiResult.Ok(
            AiModelInfo(
                id = (root["id"] as? JsonPrimitive)?.contentOrNull ?: requested,
                displayName = (root["display_name"] as? JsonPrimitive)?.contentOrNull ?: requested,
                supportsImages = supported("image_input"),
                supportsStructuredOutputs = supported("structured_outputs"),
            ),
        )
    }

    companion object {
        /** Versión de la API vigente en la documentación (la anterior, `2023-06-01`, sigue aceptándose pero admite parámetros desfasados). */
        const val API_VERSION = "2026-10-21"
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_WAIT_MS = 30_000L
        private val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): HttpClient = HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 120_000
                connectTimeoutMillis = 15_000
            }
            // Sin plugin de logging a propósito: las cabeceras llevan la clave.
        }

        /**
         * Cuerpo de `POST /v1/messages`. No lleva `temperature`/`top_p`/`top_k` ni prefill (Haiku 5.5 los rechaza con 400);
         * `output_config.effort` se fija siempre porque el valor por defecto del modelo es `medium`.
         */
        internal fun buildBody(request: AiRequest): JsonObject = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            request.system?.let { put("system", it) }
            putJsonObject("output_config") {
                put("effort", request.effort.wire)
                request.jsonSchema?.let { schema ->
                    putJsonObject("format") {
                        put("type", "json_schema")
                        put("schema", schema)
                    }
                }
            }
            put(
                "messages",
                buildJsonArray {
                    request.messages.forEach { m ->
                        add(
                            buildJsonObject {
                                put("role", m.role)
                                put(
                                    "content",
                                    buildJsonArray {
                                        m.parts.forEach { part ->
                                            add(
                                                when (part) {
                                                    is AiPart.Text -> buildJsonObject {
                                                        put("type", "text")
                                                        put("text", part.text)
                                                    }
                                                    is AiPart.Image -> buildJsonObject {
                                                        put("type", "image")
                                                        putJsonObject("source") {
                                                            put("type", "base64")
                                                            put("media_type", part.mediaType)
                                                            put("data", Base64.encode(part.jpeg))
                                                        }
                                                    }
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }
}
