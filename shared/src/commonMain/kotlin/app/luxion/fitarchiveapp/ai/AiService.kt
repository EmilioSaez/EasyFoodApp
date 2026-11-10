package app.luxion.fitarchiveapp.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Punto de entrada único de la IA para el resto de la app (etiqueta por foto, consejos, foto del plato...).
 * Aplica las reglas antes de llamar: IA activada, clave guardada, aviso de privacidad aceptado y tope diario.
 * Sin clave la IA queda desactivada y el resto de la app no cambia.
 */
class AiService(
    private val vault: AiKeyVault,
    private val settings: AiSettingsStore,
    private val today: () -> String,
    private val client: ClaudeClient = ClaudeClient(apiKey = { vault.read() }),
) {
    /** `true` si hay una clave guardada (no la lee entera fuera del almacén). */
    fun hasKey(): Boolean = try {
        !vault.read().isNullOrBlank()
    } catch (e: Exception) {
        false
    }

    /** `true` si la IA se puede usar ya: activada, con clave y con el aviso aceptado. */
    fun isReady(): Boolean {
        val s = settings.load()
        return s.enabled && s.consentAccepted && hasKey()
    }

    /** Estado de las comprobaciones previas; `null` si se puede llamar. */
    fun blocker(): AiError? {
        val s = settings.load()
        return when {
            !s.enabled -> AiError.Disabled
            !hasKey() -> AiError.NoKey
            !s.consentAccepted -> AiError.ConsentRequired
            settings.usage(today()).callsToday >= s.dailyLimit -> AiError.LimitReached(s.dailyLimit)
            else -> null
        }
    }

    /** Envía [request] con el modelo de los ajustes. Una respuesta 200 (incluida una negativa) cuenta como llamada y suma su gasto. */
    suspend fun send(request: AiRequest): AiResult<AiResponse> {
        blocker()?.let { return AiResult.Err(it) }
        val model = settings.load().model
        val result = client.createMessage(request.copy(model = model))
        if (result is AiResult.Ok) settings.recordCall(today(), model, result.value.usage)
        return result
    }

    /** Como [send] pero devuelve el texto de una respuesta completa (negativa y corte se convierten en error). */
    suspend fun askText(request: AiRequest): AiResult<String> = when (val r = send(request)) {
        is AiResult.Err -> r
        is AiResult.Ok -> r.value.requireText()
    }

    /** Salida estructurada: pide `request.jsonSchema` y devuelve el JSON ya parseado (JSON mal formado = `BadResponse`). */
    suspend fun askJson(request: AiRequest): AiResult<JsonElement> {
        require(request.jsonSchema != null) { "askJson necesita un esquema JSON" }
        return when (val t = askText(request)) {
            is AiResult.Err -> t
            is AiResult.Ok -> try {
                AiResult.Ok(Json.parseToJsonElement(t.value))
            } catch (e: Exception) {
                AiResult.Err(AiError.BadResponse("JSON mal formado"))
            }
        }
    }

    /** Prueba la conexión con la clave guardada y el modelo elegido. No cuenta como llamada ni envía datos personales. */
    suspend fun testConnection(model: String = settings.load().model): AiResult<AiModelInfo> = client.getModel(model)
}
