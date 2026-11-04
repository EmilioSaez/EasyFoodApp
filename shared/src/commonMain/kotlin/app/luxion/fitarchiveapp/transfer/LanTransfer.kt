package app.luxion.fitarchiveapp.transfer

import app.luxion.fitarchiveapp.export.DataImporter
import app.luxion.fitarchiveapp.export.ExportCodec
import app.luxion.fitarchiveapp.export.ImportReport
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readLineStrict
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Transferencia del JSON de exportación por red local, sin servidor ni cuentas.
 *
 * ## Protocolo (TCP, texto + bytes, versión 1)
 * 1. El cliente envía una línea `EASYFOOD-LAN/1 <código>\n`.
 * 2. Si el código es correcto, el anfitrión responde `OK <n>\n` seguido de `n` bytes con el JSON en UTF-8.
 *    En cualquier otro caso responde `DENIED\n` y cierra (no distingue código erróneo de oferta caducada).
 *
 * ## Seguridad
 * - El anfitrión solo sirve el JSON ofrecido; no hay otras rutas ni datos accesibles.
 * - Código de [PAIRING_CODE_LENGTH] dígitos aleatorio (seguro), de **un solo uso**: tras una entrega correcta
 *   la oferta se cierra. Tras [LanTransferHost.maxAttempts] intentos fallidos también se cierra.
 * - La oferta caduca a los [LanTransferHost.ttlMillis] ms.
 * - Lectura de la petición limitada en tamaño y en tiempo.
 * - Límite: el tráfico va **sin cifrar** por la LAN; el código evita que un tercero lo pida, no que un
 *   atacante que espíe la red lo vea. Úsese en redes de confianza.
 *
 * El anfitrión solo da el puerto y el código ([LanOffer]); la UI debe mostrar también la IP local al usuario
 * (o codificarla en el QR de #25).
 */
const val PAIRING_CODE_LENGTH = 6
internal const val PROTOCOL_TAG = "EASYFOOD-LAN/1"
private const val MAX_REQUEST_LINE = 64
private const val REQUEST_TIMEOUT_MS = 5_000L

/** Oferta activa: el cliente necesita la IP del anfitrión, [port] y [code]. */
data class LanOffer(val port: Int, val code: String, val expiresAtMillis: Long)

enum class LanTransferError { Denied, Unreachable, Timeout, TooLarge, Protocol, InvalidData }

class LanTransferException(val error: LanTransferError, message: String, cause: Throwable? = null) :
    Exception(message, cause)

fun generatePairingCode(): String = buildString {
    repeat(PAIRING_CODE_LENGTH) { append(secureRandomInt(10)) }
}

/** Compara sin cortocircuitar, para no filtrar el código por tiempo de respuesta. */
private fun constantTimeEquals(a: String, b: String): Boolean {
    var diff = a.length xor b.length
    for (i in 0 until maxOf(a.length, b.length)) {
        diff = diff or (a.getOrElse(i) { Char(0) }.code xor b.getOrElse(i) { Char(0) }.code)
    }
    return diff == 0
}

class LanTransferHost(
    private val exportJson: suspend () -> String,
    private val nowMillis: () -> Long,
    val ttlMillis: Long = 120_000,
    val maxAttempts: Int = 3,
    private val newCode: () -> String = ::generatePairingCode,
) {
    private val lock = Mutex()
    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var selector: SelectorManager? = null
    private var code: String = ""
    private var expiresAt = 0L
    private var failures = 0
    private var open = false

    val isOpen: Boolean get() = open

    /** Abre la oferta en [port] (0 = puerto libre). Una oferta anterior se cierra. */
    suspend fun start(port: Int = 0): LanOffer {
        stop()
        val sel = SelectorManager(Dispatchers.IO)
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val srv = try {
            aSocket(sel).tcp().bind("0.0.0.0", port)
        } catch (e: Throwable) {
            sel.close()
            sc.cancel()
            throw e
        }
        selector = sel
        scope = sc
        server = srv
        code = newCode()
        expiresAt = nowMillis() + ttlMillis
        failures = 0
        open = true
        val offer = LanOffer((srv.localAddress as InetSocketAddress).port, code, expiresAt)
        sc.launch {
            delay(ttlMillis)
            stop()
        }
        sc.launch { acceptLoop(srv, sc) }
        return offer
    }

    /** Cierra la oferta y deja de escuchar. Es seguro llamarlo varias veces. */
    fun stop() {
        open = false
        runCatching { server?.close() }
        runCatching { scope?.cancel() }
        runCatching { selector?.close() }
        server = null
        scope = null
        selector = null
    }

    private suspend fun acceptLoop(srv: ServerSocket, sc: CoroutineScope) {
        while (open) {
            val socket = try {
                srv.accept()
            } catch (e: Throwable) {
                return
            }
            sc.launch {
                try {
                    handle(socket)
                } catch (_: Throwable) {
                } finally {
                    runCatching { socket.close() }
                    // Oferta consumida o cerrada: libera el socket de escucha y el selector.
                    if (!open) stop()
                }
            }
        }
    }

    private suspend fun handle(socket: Socket) {
        val input = socket.openReadChannel()
        val output = socket.openWriteChannel(autoFlush = true)
        val line = try {
            withTimeout(REQUEST_TIMEOUT_MS) { readRequestLine(input) }
        } catch (_: Throwable) {
            null
        }
        val body: ByteArray? = lock.withLock {
            if (!open) return@withLock null
            if (nowMillis() >= expiresAt) {
                stop()
                return@withLock null
            }
            val given = line?.takeIf { it.startsWith("$PROTOCOL_TAG ") }?.removePrefix("$PROTOCOL_TAG ")
            if (given != null && constantTimeEquals(given, code)) {
                // Un solo uso: se consume antes de enviar, para que dos clientes no lo reciban a la vez.
                open = false
                exportJson().encodeToByteArray()
            } else {
                if (++failures >= maxAttempts) stop()
                null
            }
        }
        if (body == null) {
            output.writeStringUtf8("DENIED\n")
        } else {
            output.writeStringUtf8("OK ${body.size}\n")
            output.writeFully(body)
            output.flush()
            // Cerrar el socket de escucha no corta esta conexión; el scope sí, así que se cierra el socket aquí.
            runCatching { server?.close() }
        }
    }

    private suspend fun readRequestLine(input: ByteReadChannel): String? {
        val line = input.readLineStrict(MAX_REQUEST_LINE + 1L) ?: return null
        return line.takeIf { it.length <= MAX_REQUEST_LINE }
    }
}

object LanTransferClient {
    const val DEFAULT_MAX_BYTES = 32 * 1024 * 1024
    private const val TIMEOUT_MS = 15_000L

    /** Descarga y valida el JSON ofrecido en [host]:[port] con [code]. */
    suspend fun fetch(host: String, port: Int, code: String, maxBytes: Int = DEFAULT_MAX_BYTES): String {
        val selector = SelectorManager(Dispatchers.IO)
        try {
            val text = try {
                withTimeout(TIMEOUT_MS) { download(selector, host, port, code.trim(), maxBytes) }
            } catch (e: LanTransferException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                throw LanTransferException(LanTransferError.Timeout, "Tiempo de espera agotado", e)
            } catch (e: Exception) {
                throw LanTransferException(LanTransferError.Unreachable, "No se pudo conectar con el otro dispositivo", e)
            }
            try {
                ExportCodec.decode(text)
            } catch (e: Exception) {
                throw LanTransferException(LanTransferError.InvalidData, "Los datos recibidos no son una exportación válida", e)
            }
            return text
        } finally {
            selector.close()
        }
    }

    /** Descarga el JSON y lo pasa al importador (#24). */
    suspend fun fetchAndImport(
        importer: DataImporter,
        host: String,
        port: Int,
        code: String,
        maxBytes: Int = DEFAULT_MAX_BYTES,
    ): ImportReport = importer.import(fetch(host, port, code, maxBytes))

    private suspend fun download(selector: SelectorManager, host: String, port: Int, code: String, maxBytes: Int): String {
        val socket = aSocket(selector).tcp().connect(host, port)
        try {
            val output = socket.openWriteChannel(autoFlush = true)
            val input = socket.openReadChannel()
            output.writeStringUtf8("$PROTOCOL_TAG $code\n")
            val header = input.readLineStrict(32L)
                ?: throw LanTransferException(LanTransferError.Protocol, "Respuesta vacía")
            if (header == "DENIED") {
                throw LanTransferException(LanTransferError.Denied, "Código incorrecto o oferta caducada")
            }
            val size = header.takeIf { it.startsWith("OK ") }?.removePrefix("OK ")?.toIntOrNull()
                ?: throw LanTransferException(LanTransferError.Protocol, "Respuesta no válida")
            if (size < 0 || size > maxBytes) {
                throw LanTransferException(LanTransferError.TooLarge, "Los datos superan el tamaño máximo")
            }
            val bytes = ByteArray(size)
            input.readFully(bytes)
            return bytes.decodeToString()
        } finally {
            socket.close()
        }
    }
}
