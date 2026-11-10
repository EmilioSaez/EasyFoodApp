package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.fitarchiveapp.ai.AiError
import app.luxion.fitarchiveapp.ai.AiModelInfo
import app.luxion.fitarchiveapp.ai.AiModule
import app.luxion.fitarchiveapp.ai.AiResult
import app.luxion.fitarchiveapp.ai.AiSettings
import app.luxion.fitarchiveapp.ai.AiStorageException
import app.luxion.fitarchiveapp.ai.AiUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.round

/** Resultado de «Probar conexión», listo para pintar. */
sealed interface AiTestResult {
    data object Running : AiTestResult
    data class Success(val info: AiModelInfo) : AiTestResult
    data class Failure(val message: String) : AiTestResult
}

/** Estado y acciones de la pantalla de ajustes de IA. Las operaciones con el almacén van fuera del hilo de UI. */
@Stable
class AiSettingsState(private val module: AiModule, private val today: () -> String) {
    var settings by mutableStateOf(AiSettings())
        private set
    var usage by mutableStateOf(AiUsage(0, 0, 0, 0, 0))
        private set
    var keyHint by mutableStateOf<String?>(null)
        private set
    var test by mutableStateOf<AiTestResult?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    val hasKey: Boolean get() = keyHint != null

    suspend fun refresh() {
        val key = withContext(Dispatchers.Default) { runCatching { module.vault.read() }.getOrNull() }
        keyHint = key?.takeIf { it.isNotBlank() }?.let { "…" + it.takeLast(4) }
        settings = module.settings.load()
        usage = module.settings.usage(today())
    }

    /** Guarda la clave pegada, comprueba la conexión y, si la API la rechaza (401), la descarta. */
    suspend fun saveKey(raw: String) {
        val key = raw.trim()
        message = null
        if (!isPlausibleKey(key)) {
            message = "Esa clave no parece válida: debe empezar por «sk-ant-» y no llevar espacios."
            return
        }
        busy = true
        try {
            withContext(Dispatchers.Default) { module.vault.write(key) }
            refresh()
            test = AiTestResult.Running
            when (val r = module.service.testConnection(settings.model)) {
                is AiResult.Ok -> test = AiTestResult.Success(r.value)
                is AiResult.Err -> {
                    if (r.error == AiError.InvalidKey) {
                        withContext(Dispatchers.Default) { module.vault.clear() }
                        module.settings.setEnabled(false)
                        refresh()
                    }
                    test = AiTestResult.Failure(r.error.message)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiStorageException) {
            message = AiError.Storage(e.message.orEmpty()).message
        } finally {
            busy = false
        }
    }

    /** Borra la clave y desactiva la IA. */
    suspend fun clearKey() {
        withContext(Dispatchers.Default) { module.vault.clear() }
        module.settings.setEnabled(false)
        test = null
        message = null
        refresh()
    }

    suspend fun runTest() {
        if (!hasKey) return
        test = AiTestResult.Running
        test = when (val r = module.service.testConnection(settings.model)) {
            is AiResult.Ok -> AiTestResult.Success(r.value)
            is AiResult.Err -> AiTestResult.Failure(r.error.message)
        }
    }

    /** Activar exige clave y aviso de privacidad aceptado; devuelve `false` si falta el aviso (la pantalla lo muestra). */
    suspend fun setEnabled(enabled: Boolean): Boolean {
        if (enabled && !hasKey) return true
        if (enabled && !settings.consentAccepted) return false
        module.settings.setEnabled(enabled)
        refresh()
        return true
    }

    suspend fun acceptConsentAndEnable() {
        module.settings.setConsent(true)
        if (hasKey) module.settings.setEnabled(true)
        refresh()
    }

    suspend fun setModel(id: String) {
        module.settings.setModel(id)
        test = null
        refresh()
    }

    suspend fun adjustLimit(delta: Int) {
        module.settings.setDailyLimit(settings.dailyLimit + delta)
        refresh()
    }

    suspend fun resetUsage() {
        module.settings.resetUsage()
        refresh()
    }

    companion object {
        fun isPlausibleKey(key: String): Boolean =
            key.startsWith("sk-ant-") && key.length >= 20 && key.none { it.isWhitespace() }

        /** Gasto estimado, p. ej. «~0,02 $» (coma decimal); «menos de 0,01 $» si es una fracción de céntimo. */
        fun formatUsd(usd: Double): String {
            val cents = round(usd * 100).toLong()
            if (cents == 0L) return if (usd > 0.0) "menos de 0,01 $" else "0,00 $"
            return "~" + (cents / 100) + "," + (cents % 100).toString().padStart(2, '0') + " $"
        }
    }
}
