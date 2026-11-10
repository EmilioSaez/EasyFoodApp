package app.luxion.fitarchiveapp.ai

/** Texto del aviso de privacidad que se acepta antes de la primera llamada (docs/design.md §2.16). */
object AiPrivacy {
    const val TITLE = "Antes de usar la IA"

    val sent: List<String> = listOf(
        "La foto que elijas (reducida a 1024 px como máximo y sin metadatos).",
        "Un resumen mínimo del día: kcal y macros consumidos y tus objetivos de kcal y macros.",
        "La indicación de la tarea (por ejemplo, «lee esta etiqueta»).",
        "Para sugerencias y consejos: el nombre y las kcal de unos pocos de tus favoritos, recientes, plantillas y recetas, lo ya apuntado en la comida actual, el agua, los totales de fibra, sal y azúcares y las medias de la semana, y el texto que escribas.",
    )

    val notSent: List<String> = listOf(
        "Tu nombre, correo ni ningún dato de cuenta.",
        "Tu historial completo del diario, tus pesos ni el contenido de tus recetas (solo su nombre y kcal por ración).",
        "Tu clave de API: solo viaja a Anthropic, en la cabecera de la petición.",
    )

    /** Id del aviso propio de «¿Qué como?», «¿Cómo voy?» y la pregunta libre (se acepta una vez; ver [AiSettingsStore.hasAck]). */
    const val ADVICE_ACK = "advice"

    const val ADVICE_NOTICE =
        "Para responder se envía a Anthropic un resumen mínimo: tus totales y objetivos, lo ya apuntado en la comida actual, " +
            "agua, y el nombre y las kcal de unos pocos favoritos, recientes, plantillas y recetas, más lo que escribas. " +
            "Nunca tu nombre, correo ni tu historial completo."

    const val FOOTER =
        "Los datos van directamente de tu dispositivo a Anthropic con tu propia clave; EasyFood no tiene servidor ni los ve. " +
            "Anthropic los trata según su política de privacidad y tu configuración de retención. " +
            "Las respuestas son orientativas y pueden contener errores: no sustituyen a un profesional de la salud."
}

/** Resumen mínimo del día que podrán enviar las funciones de IA (nada de nombre, correo ni historial). */
data class AiDaySummary(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val goalKcal: Double?,
    val goalProteinG: Double? = null,
    val goalCarbsG: Double? = null,
    val goalFatG: Double? = null,
) {
    /** Texto para el prompt, con redondeo a unidades enteras. */
    fun toPromptText(): String = buildString {
        append("Hoy: ${kcal.roundToInt()} kcal, proteína ${proteinG.roundToInt()} g, hidratos ${carbsG.roundToInt()} g, grasa ${fatG.roundToInt()} g.")
        if (goalKcal != null) {
            append(" Objetivo: ${goalKcal.roundToInt()} kcal")
            goalProteinG?.let { append(", proteína ${it.roundToInt()} g") }
            goalCarbsG?.let { append(", hidratos ${it.roundToInt()} g") }
            goalFatG?.let { append(", grasa ${it.roundToInt()} g") }
            append(".")
        }
    }

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
