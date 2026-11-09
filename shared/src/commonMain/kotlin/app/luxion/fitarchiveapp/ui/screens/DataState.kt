package app.luxion.fitarchiveapp.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.luxion.fitarchiveapp.export.DataExporter
import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportFormatException
import app.luxion.fitarchiveapp.export.ImportCounts
import app.luxion.fitarchiveapp.export.ImportPlan
import app.luxion.fitarchiveapp.export.ImportReport
import app.luxion.fitarchiveapp.export.qr.QrScanResult
import app.luxion.fitarchiveapp.export.qr.QrTransferReceiver
import app.luxion.fitarchiveapp.transfer.LanTransferError
import app.luxion.fitarchiveapp.transfer.LanTransferException
import app.luxion.fitarchiveapp.transfer.PAIRING_CODE_LENGTH
import kotlinx.coroutines.CancellationException

/** Nombre de archivo sugerido para la exportación de [isoDate] (`YYYY-MM-DD`). */
fun exportFileName(isoDate: String) = "easyfood-$isoDate.json"

/** Texto en español para un fallo de exportación, importación o transferencia. */
fun dataErrorMessage(e: Throwable): String = when (e) {
    is ExportFormatException -> (e.message ?: "El archivo no es válido").let { if (it.endsWith('.')) it else "$it." }
    is LanTransferException -> lanErrorMessage(e.error)
    else -> "Ha ocurrido un error inesperado. Inténtalo de nuevo."
}

fun lanErrorMessage(error: LanTransferError): String = when (error) {
    LanTransferError.Denied -> "Código incorrecto o la oferta ha caducado. Pide un código nuevo en el otro dispositivo."
    LanTransferError.Unreachable -> "No se pudo conectar. Comprueba la dirección y que ambos dispositivos estén en la misma red."
    LanTransferError.Timeout -> "El otro dispositivo no ha respondido a tiempo."
    LanTransferError.TooLarge -> "Los datos son demasiado grandes para transferirlos."
    LanTransferError.Protocol -> "El otro dispositivo no usa el mismo protocolo de transferencia."
    LanTransferError.InvalidData -> "Los datos recibidos no son una exportación válida de EasyFood."
}

/** Una fila de la vista previa de importación. */
data class ImportPreviewRow(val label: String, val counts: ImportCounts) {
    val summary: String get() = counts.describe()
}

/** Vista previa de lo que haría una importación, antes de confirmarla. */
data class ImportPreview(
    val rows: List<ImportPreviewRow>,
    val invalidItems: List<String>,
    /** `true` si hay algo que escribir; si no, solo hay duplicados y no se ofrece confirmar. */
    val canConfirm: Boolean,
) {
    val duplicates: Int get() = rows.sumOf { it.counts.unchanged }
    val conflicts: Int get() = rows.sumOf { it.counts.keptLocal }
}

fun ImportPlan.toPreview() = ImportPreview(
    rows = report.rows(),
    invalidItems = report.invalidItems,
    canConfirm = !changes.isEmpty,
)

fun ImportReport.rows(): List<ImportPreviewRow> = listOf(
    ImportPreviewRow("Productos", products),
    ImportPreviewRow("Alimentos propios", customFoods),
    ImportPreviewRow("Entradas del diario", diaryEntries),
    ImportPreviewRow("Pesos", weights),
    ImportPreviewRow("Agua", water),
    ImportPreviewRow("Recetas", recipes),
    ImportPreviewRow("Favoritos", favorites),
).filter { it.counts.hasAny }

private val ImportCounts.hasAny get() = added + updated + unchanged + keptLocal + invalid > 0

/** «3 nuevos · 1 actualizado · 2 duplicados» (omite lo que vale cero). */
fun ImportCounts.describe(): String = listOfNotNull(
    added.takeIf { it > 0 }?.let { if (it == 1) "1 nuevo" else "$it nuevos" },
    updated.takeIf { it > 0 }?.let { if (it == 1) "1 actualizado" else "$it actualizados" },
    unchanged.takeIf { it > 0 }?.let { if (it == 1) "1 duplicado" else "$it duplicados" },
    keptLocal.takeIf { it > 0 }?.let { if (it == 1) "1 conflicto (se conserva el local)" else "$it conflictos (se conserva el local)" },
    invalid.takeIf { it > 0 }?.let { if (it == 1) "1 inválido" else "$it inválidos" },
).joinToString(" · ").ifEmpty { "Sin cambios" }

/** Resumen de una sola frase del resultado de una importación ya aplicada. */
fun ImportReport.summary(): String {
    val added = products.added + customFoods.added + diaryEntries.added + weights.added + water.added + recipes.added
    val updated = products.updated + customFoods.updated + diaryEntries.updated + weights.updated + water.updated + recipes.updated
    val skipped = products.unchanged + customFoods.unchanged + diaryEntries.unchanged + weights.unchanged + water.unchanged + recipes.unchanged
    return when {
        added + updated == 0 -> "No había nada nuevo que importar."
        else -> buildString {
            append("Importados $added elementos")
            if (updated > 0) append(" y actualizados $updated")
            if (skipped > 0) append("; $skipped ya existían")
            append(".")
        }
    }
}

/** Fase de la importación en curso, común a archivo, QR y red local. */
sealed interface ImportStage {
    data object Idle : ImportStage
    data object Working : ImportStage
    data class Preview(val json: String, val preview: ImportPreview) : ImportStage
    data class Done(val report: ImportReport) : ImportStage
    data class Failed(val message: String) : ImportStage
}

/**
 * Lógica de la pantalla «Datos»: exportar y el flujo previsualizar → confirmar de cualquier origen
 * (archivo, QR, red local). No sabe nada de la UI ni de la plataforma.
 */
class DataPresenter(
    private val exporter: DataExporter,
    private val importer: DataImporter,
) {
    var stage: ImportStage by mutableStateOf(ImportStage.Idle)
        private set

    /** JSON de exportación, o el mensaje de error. */
    suspend fun export(): Result<String> = guarded { exporter.exportToJson() }

    /** JSON de exportación sin envolver el error (lo usa el anfitrión de red local, que ya gestiona los suyos). */
    suspend fun exportJson(): String = exporter.exportToJson()

    /** Obtiene el JSON con [fetch] (red local, cámara...) y lo previsualiza; un fallo deja la fase en error. */
    suspend fun stageFrom(fetch: suspend () -> String) {
        stage = ImportStage.Working
        val json = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            stage = ImportStage.Failed(dataErrorMessage(e))
            return
        }
        stage(json)
    }

    /** Calcula la vista previa de [json] sin escribir nada. */
    suspend fun stage(json: String) {
        stage = ImportStage.Working
        stage = try {
            val plan = importer.preview(json)
            ImportStage.Preview(json, plan.toPreview())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportStage.Failed(dataErrorMessage(e))
        }
    }

    /** Aplica la importación previsualizada. No hace nada si no hay una vista previa confirmable. */
    suspend fun confirm() {
        val current = stage as? ImportStage.Preview ?: return
        if (!current.preview.canConfirm) return
        stage = ImportStage.Working
        stage = try {
            ImportStage.Done(importer.import(current.json))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImportStage.Failed(dataErrorMessage(e))
        }
    }

    /** Descarta la vista previa o el resultado y vuelve al inicio. */
    fun dismiss() {
        stage = ImportStage.Idle
    }

    fun fail(message: String) {
        stage = ImportStage.Failed(message)
    }

    private suspend fun <T> guarded(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(IllegalStateException(dataErrorMessage(e), e))
    }
}

/** Secuencia de fragmentos QR que se muestra en bucle al emisor. */
class QrFrames(val frames: List<String>) {
    init { require(frames.isNotEmpty()) }

    val size: Int get() = frames.size

    fun next(index: Int) = (index + 1) % size
    fun previous(index: Int) = (index - 1 + size) % size

    /** «Código 3 de 12». */
    fun label(index: Int) = "Código ${index + 1} de $size"

    /** Con tantos códigos es poco práctico: se recomienda archivo o red local. */
    val isLarge: Boolean get() = size > LARGE_THRESHOLD

    companion object {
        const val LARGE_THRESHOLD = 40
        /** Caracteres de datos por QR: menos que el máximo para que se lean bien desde una pantalla. */
        const val CHUNK_SIZE = 400
    }
}

/** Progreso de recepción por QR que se pinta en la cámara. */
data class QrReceiveUi(
    val received: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val complete: Boolean = false,
) {
    val fraction: Float get() = if (total == 0) 0f else received.toFloat() / total
    val label: String
        get() = if (total == 0) "Apunta al primer código" else "Recibidos $received de $total"
}

/** Adapta el [QrTransferReceiver] a un estado de UI inmutable. */
class QrReceiveModel(private val receiver: QrTransferReceiver = QrTransferReceiver()) {
    var ui: QrReceiveUi by mutableStateOf(QrReceiveUi())
        private set

    /** Procesa una lectura de la cámara; devuelve el JSON reensamblado cuando ya están todos los fragmentos. */
    fun offer(text: String): String? {
        if (ui.complete) return null
        when (val r = receiver.accept(text)) {
            is QrScanResult.Accepted -> ui = QrReceiveUi(r.received, r.total, complete = receiver.isComplete)
            is QrScanResult.Duplicate -> return null
            is QrScanResult.Rejected -> {
                // Un QR ajeno o corrupto no debe borrar el progreso; solo se avisa.
                ui = ui.copy(message = r.reason)
                return null
            }
        }
        if (!receiver.isComplete) return null
        return try {
            receiver.assemble()
        } catch (e: ExportFormatException) {
            receiver.reset()
            ui = QrReceiveUi(message = dataErrorMessage(e))
            null
        }
    }

    fun reset() {
        receiver.reset()
        ui = QrReceiveUi()
    }
}

/** Datos validados para pedir una transferencia por red local. */
data class LanRequest(val host: String, val port: Int, val code: String)

/** Campos del formulario «Recibir por red local» y su validación. */
data class LanForm(val address: String = "", val code: String = "") {
    /** Acepta `host`, `host:puerto`. Devuelve el error de cada campo o la petición válida. */
    fun validate(): LanValidation {
        val trimmed = address.trim()
        val host = trimmed.substringBeforeLast(':', trimmed)
        val portText = if (':' in trimmed) trimmed.substringAfterLast(':') else ""
        val port = portText.toIntOrNull()
        val addressError = when {
            trimmed.isEmpty() -> "Escribe la dirección que muestra el otro dispositivo"
            host.isEmpty() || ' ' in host -> "La dirección no es válida"
            ':' !in trimmed || port == null || port !in 1..65535 -> "Falta el puerto, por ejemplo 192.168.1.20:5050"
            else -> null
        }
        val cleanCode = code.trim()
        val codeError = when {
            cleanCode.isEmpty() -> "Escribe el código de emparejamiento"
            cleanCode.length != PAIRING_CODE_LENGTH || !cleanCode.all { it in '0'..'9' } ->
                "El código tiene $PAIRING_CODE_LENGTH dígitos"
            else -> null
        }
        return if (addressError == null && codeError == null) {
            LanValidation(LanRequest(host, port!!, cleanCode), null, null)
        } else {
            LanValidation(null, addressError, codeError)
        }
    }
}

data class LanValidation(val request: LanRequest?, val addressError: String?, val codeError: String?)

/** «192.168.1.20:5050» para mostrar al emisor; `null` si no hay ninguna IP local conocida. */
fun lanAddressLabel(ips: List<String>, port: Int): String? = ips.firstOrNull()?.let { "$it:$port" }

/** Segundos que quedan de una oferta LAN (nunca negativo). */
fun secondsLeft(expiresAtMillis: Long, nowMillis: Long): Int =
    (((expiresAtMillis - nowMillis) + 999) / 1000).coerceAtLeast(0).toInt()
