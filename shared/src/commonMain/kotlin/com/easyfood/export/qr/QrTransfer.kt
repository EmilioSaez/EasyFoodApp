package com.easyfood.export.qr

import com.easyfood.export.DataImporter
import com.easyfood.export.ExportFormatException
import com.easyfood.export.ImportPlan
import com.easyfood.export.ImportReport
import kotlinx.serialization.json.Json

/**
 * Transferencia del JSON de exportación entre dispositivos mediante una secuencia de códigos QR.
 *
 * ## Formato de fragmento (texto, para codificar en un QR)
 * `EFQ1:<transferId>:<índice>:<total>:<crcFragmento>:<datos>`
 * - `transferId`: CRC-32 (8 hex) del texto completo transmitido; identifica la transferencia y sirve de
 *   validación final al reensamblar.
 * - `índice`: 1..`total`. `crcFragmento`: CRC-32 (8 hex) de `datos`, para descartar lecturas corruptas.
 * - `datos`: trozo del JSON compactado (sin saltos de línea ni espacios sobrantes). Nunca parte un par
 *   subrogado.
 */
object QrTransfer {
    const val PREFIX = "EFQ1"
    const val DEFAULT_CHUNK_SIZE = 600

    /** Compacta [exportJson] y lo parte en fragmentos de como máximo [chunkSize] caracteres de datos. */
    fun split(exportJson: String, chunkSize: Int = DEFAULT_CHUNK_SIZE): List<String> {
        require(chunkSize >= 2) { "chunkSize demasiado pequeño" }
        val compact = try {
            Json.parseToJsonElement(exportJson).toString()
        } catch (e: Exception) {
            throw ExportFormatException("El archivo no es un JSON de exportación válido", e)
        }
        val chunks = chunk(compact, chunkSize)
        val id = Crc32.hex(compact)
        return chunks.mapIndexed { i, data -> "$PREFIX:$id:${i + 1}:${chunks.size}:${Crc32.hex(data)}:$data" }
    }

    private fun chunk(text: String, size: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + size, text.length)
            if (end < text.length && text[end - 1].isHighSurrogate()) end--
            out += text.substring(start, end)
            start = end
        }
        return out
    }

    internal fun parse(text: String): QrFragment? {
        val parts = text.split(':', limit = 6)
        if (parts.size != 6 || parts[0] != PREFIX) return null
        val index = parts[2].toIntOrNull() ?: return null
        val total = parts[3].toIntOrNull() ?: return null
        if (total < 1 || index !in 1..total) return null
        if (parts[1].length != 8 || parts[4].length != 8) return null
        return QrFragment(parts[1], index, total, parts[4], parts[5])
    }
}

internal data class QrFragment(val transferId: String, val index: Int, val total: Int, val crc: String, val data: String)

/** Resultado de ofrecer una lectura al [QrTransferReceiver]. */
sealed interface QrScanResult {
    /** Fragmento nuevo aceptado. */
    data class Accepted(val received: Int, val total: Int) : QrScanResult
    /** Fragmento ya recibido (misma lectura repetida); se ignora. */
    data class Duplicate(val received: Int, val total: Int) : QrScanResult
    /** No es un fragmento válido de esta transferencia (otro QR, corrupto, o de otra transferencia). */
    data class Rejected(val reason: String) : QrScanResult
}

/**
 * Receptor: acumula fragmentos en cualquier orden, tolerando repeticiones, y reensambla y valida el JSON
 * cuando están todos. La primera lectura válida fija la transferencia; fragmentos de otra se rechazan.
 */
class QrTransferReceiver {
    private var transferId: String? = null
    private var total = 0
    private val parts = HashMap<Int, QrFragment>()

    val receivedCount: Int get() = parts.size
    val totalCount: Int get() = total
    val isComplete: Boolean get() = total > 0 && parts.size == total

    /** Índices (1..total) que aún faltan; vacío si no se ha leído nada o ya está completo. */
    val missing: List<Int> get() = (1..total).filter { it !in parts }

    fun accept(text: String): QrScanResult {
        val f = QrTransfer.parse(text) ?: return QrScanResult.Rejected("No es un fragmento de transferencia")
        if (Crc32.hex(f.data) != f.crc) return QrScanResult.Rejected("Fragmento ${f.index} dañado, vuelve a escanearlo")
        val id = transferId
        if (id == null) {
            transferId = f.transferId
            total = f.total
        } else if (id != f.transferId) {
            return QrScanResult.Rejected("Pertenece a otra transferencia")
        } else if (f.total != total) {
            return QrScanResult.Rejected("Número total de fragmentos inconsistente")
        }
        val existing = parts[f.index]
        if (existing != null) {
            return if (existing.data == f.data) QrScanResult.Duplicate(parts.size, total)
            else QrScanResult.Rejected("Fragmento ${f.index} contradictorio")
        }
        parts[f.index] = f
        return QrScanResult.Accepted(parts.size, total)
    }

    /** Reensambla el JSON. Lanza [ExportFormatException] si faltan fragmentos o el checksum global no coincide. */
    fun assemble(): String {
        if (!isComplete) throw ExportFormatException("Faltan fragmentos: ${missing.joinToString()}")
        val text = (1..total).joinToString("") { parts.getValue(it).data }
        if (Crc32.hex(text) != transferId) throw ExportFormatException("La transferencia está dañada (checksum no coincide)")
        return text
    }

    fun reset() {
        transferId = null
        total = 0
        parts.clear()
    }

    /** Previsualiza la importación del JSON reensamblado (conecta con [DataImporter], sin escribir). */
    suspend fun preview(importer: DataImporter): ImportPlan = importer.preview(assemble())

    /** Importa el JSON reensamblado con [importer]. */
    suspend fun import(importer: DataImporter): ImportReport = importer.import(assemble())
}

/** Matriz de módulos de un QR: `true` = módulo oscuro. */
class QrMatrix(val size: Int, private val modules: BooleanArray) {
    init { require(modules.size == size * size) }
    operator fun get(x: Int, y: Int): Boolean = modules[y * size + x]
}

/** Abstracción de generación de QR; la implementación concreta es de cada plataforma. */
fun interface QrEncoder {
    fun encode(text: String): QrMatrix
}

/** Abstracción de lectura de QR: cada texto leído se entrega a [onScan]. Se cancela con el handle devuelto. */
interface QrScanner {
    fun start(onScan: (String) -> Unit): AutoCloseable
}

/** CRC-32 (IEEE) sobre UTF-8, sin dependencias de plataforma. */
internal object Crc32 {
    private val table = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun of(text: String): Int {
        var c = -1
        for (b in text.encodeToByteArray()) c = table[(c xor b.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    fun hex(text: String): String = of(text).toUInt().toString(16).padStart(8, '0')
}
