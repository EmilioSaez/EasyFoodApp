package app.luxion.fitarchiveapp.ai

/** Esfuerzo de razonamiento (`output_config.effort`). Haiku 5.5 usa `medium` por defecto, por eso se fija siempre. */
enum class AiEffort(val wire: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }

/**
 * Modelo seleccionable. Precios en USD por millón de tokens (hasta 100K tokens de prompt; Haiku 5.5 cuesta más
 * con prompts mayores, que aquí no se dan). Son una estimación: la factura real es la de Anthropic.
 */
data class AiModelOption(
    val id: String,
    val label: String,
    val inputUsdPerMTok: Double,
    val outputUsdPerMTok: Double,
    val hint: String,
)

object AiModels {
    const val DEFAULT_ID = "claude-haiku-5-5"
    const val PHOTO_ID = "claude-sonnet-5-5"

    val options: List<AiModelOption> = listOf(
        AiModelOption("claude-haiku-5-5", "Claude Haiku 5.5", 0.10, 0.50, "El más barato y rápido (por defecto)"),
        AiModelOption("claude-sonnet-5-5", "Claude Sonnet 5.5", 2.0, 10.0, "Más preciso; útil para la foto del plato"),
        AiModelOption("claude-opus-5-5", "Claude Opus 5.5", 4.0, 20.0, "El más capaz y el más caro"),
    )

    fun find(id: String): AiModelOption? = options.firstOrNull { it.id == id }

    /** Coste estimado en micro-dólares (1 µ$ = 0,000001 $): tokens × ($/MTok) da directamente µ$. Un modelo desconocido usa el precio de Haiku. */
    fun costMicroUsd(modelId: String, inputTokens: Long, outputTokens: Long): Long {
        val m = find(modelId) ?: options.first()
        return kotlin.math.ceil(inputTokens * m.inputUsdPerMTok + outputTokens * m.outputUsdPerMTok).toLong()
    }
}
