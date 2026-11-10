package app.luxion.fitarchiveapp.ai

/** Ajustes no sensibles de la IA (por dispositivo; no viajan en exportar/importar). */
data class AiSettings(
    val enabled: Boolean = false,
    val model: String = AiModels.DEFAULT_ID,
    val dailyLimit: Int = AiSettingsStore.DEFAULT_DAILY_LIMIT,
    val consentAccepted: Boolean = false,
)

/** Uso acumulado: llamadas de hoy y totales, y gasto estimado en micro-dólares. */
data class AiUsage(
    val callsToday: Int,
    val callsTotal: Long,
    val inputTokensTotal: Long,
    val outputTokensTotal: Long,
    val estimatedMicroUsd: Long,
) {
    val estimatedUsd: Double get() = estimatedMicroUsd / 1_000_000.0
}

/** Lee y escribe [AiSettings] y el contador de uso sobre [AiPreferences]. */
class AiSettingsStore(private val prefs: AiPreferences) {
    fun load(): AiSettings = AiSettings(
        enabled = prefs.getString(ENABLED) == "1",
        model = prefs.getString(MODEL)?.takeIf { it.isNotBlank() } ?: AiModels.DEFAULT_ID,
        dailyLimit = prefs.getString(LIMIT)?.toIntOrNull()?.coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT) ?: DEFAULT_DAILY_LIMIT,
        consentAccepted = prefs.getString(CONSENT) == "1",
    )

    fun setEnabled(value: Boolean) = prefs.putString(ENABLED, if (value) "1" else "0")
    fun setModel(id: String) {
        require(id.isNotBlank()) { "El modelo no puede estar vacío" }
        prefs.putString(MODEL, id.trim())
    }
    fun setDailyLimit(value: Int) = prefs.putString(LIMIT, value.coerceIn(MIN_DAILY_LIMIT, MAX_DAILY_LIMIT).toString())
    fun setConsent(accepted: Boolean) = prefs.putString(CONSENT, if (accepted) "1" else "0")

    fun usage(today: String): AiUsage = AiUsage(
        callsToday = if (prefs.getString(USAGE_DATE) == today) prefs.getString(USAGE_CALLS_TODAY)?.toIntOrNull() ?: 0 else 0,
        callsTotal = long(USAGE_CALLS_TOTAL),
        inputTokensTotal = long(USAGE_IN),
        outputTokensTotal = long(USAGE_OUT),
        estimatedMicroUsd = long(USAGE_MICRO_USD),
    )

    /** Anota una llamada completada con sus tokens y el coste estimado para el [model] usado. */
    fun recordCall(today: String, model: String, tokens: AiUsageTokens) {
        val current = usage(today)
        prefs.putString(USAGE_DATE, today)
        prefs.putString(USAGE_CALLS_TODAY, (current.callsToday + 1).toString())
        prefs.putString(USAGE_CALLS_TOTAL, (current.callsTotal + 1).toString())
        prefs.putString(USAGE_IN, (current.inputTokensTotal + tokens.inputTokens).toString())
        prefs.putString(USAGE_OUT, (current.outputTokensTotal + tokens.outputTokens).toString())
        prefs.putString(
            USAGE_MICRO_USD,
            (current.estimatedMicroUsd + AiModels.costMicroUsd(model, tokens.inputTokens, tokens.outputTokens)).toString(),
        )
    }

    fun resetUsage() {
        listOf(USAGE_DATE, USAGE_CALLS_TODAY, USAGE_CALLS_TOTAL, USAGE_IN, USAGE_OUT, USAGE_MICRO_USD).forEach(prefs::remove)
    }

    private fun long(name: String): Long = prefs.getString(name)?.toLongOrNull() ?: 0L

    companion object {
        const val DEFAULT_DAILY_LIMIT = 30
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 500

        private const val ENABLED = "ai.enabled"
        private const val MODEL = "ai.model"
        private const val LIMIT = "ai.dailyLimit"
        private const val CONSENT = "ai.consent"
        private const val USAGE_DATE = "ai.usage.date"
        private const val USAGE_CALLS_TODAY = "ai.usage.callsToday"
        private const val USAGE_CALLS_TOTAL = "ai.usage.callsTotal"
        private const val USAGE_IN = "ai.usage.inputTokens"
        private const val USAGE_OUT = "ai.usage.outputTokens"
        private const val USAGE_MICRO_USD = "ai.usage.microUsd"
    }
}
