package app.luxion.fitarchiveapp.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Una copia automática guardada: el JSON de exportación de [date] (`YYYY-MM-DD`, día local en que se hizo). */
data class BackupInfo(val name: String, val date: String, val createdAtMillis: Long)

/**
 * Copia de seguridad periódica con la exportación JSON de siempre. Se hace al abrir la app (y mientras siga abierta)
 * si han pasado [INTERVAL_MILLIS] desde la última; se conservan las [keep] últimas. El ajuste de activarla o no
 * se guarda como un archivo en la propia carpeta de copias, así que no necesita tabla ni migración.
 */
class BackupManager(
    private val files: BackupFiles,
    /** Genera el JSON de exportación (el mismo de «Guardar archivo JSON»). */
    private val export: suspend () -> String,
    private val nowMillis: () -> Long,
    private val today: () -> String,
    private val keep: Int = DEFAULT_KEEP,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    init { require(keep >= 1) }

    /** Activada por defecto: solo se desactiva si el usuario lo pide. */
    suspend fun isEnabled(): Boolean = withContext(dispatcher) {
        try {
            files.readText(SETTINGS_FILE).trim() != OFF
        } catch (_: Exception) {
            true
        }
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(dispatcher) {
        files.writeText(SETTINGS_FILE, if (enabled) ON else OFF)
    }

    /** Copias existentes, de la más reciente a la más antigua. */
    suspend fun list(): List<BackupInfo> = withContext(dispatcher) { listSync() }

    suspend fun last(): BackupInfo? = list().firstOrNull()

    /** Hace una copia ahora, esté o no activada la automática, y aplica la rotación. */
    suspend fun backupNow(): BackupInfo {
        val json = export()
        return withContext(dispatcher) {
            val now = nowMillis()
            val date = today()
            val name = "$PREFIX$date-$now$SUFFIX"
            files.writeText(name, json)
            listSync().drop(keep).forEach { files.delete(it.name) }
            BackupInfo(name, date, now)
        }
    }

    /**
     * Hace una copia si está activada y toca (nunca hubo una o pasó la semana). Devuelve `null` si no hacía falta
     * o si falló: una copia automática nunca debe molestar al usuario ni romper la app.
     */
    suspend fun runIfDue(): BackupInfo? = try {
        if (isEnabled() && isDue(last()?.createdAtMillis, nowMillis())) backupNow() else null
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** Contenido JSON de la copia [info]. */
    suspend fun read(info: BackupInfo): String = withContext(dispatcher) { files.readText(info.name) }

    private fun listSync(): List<BackupInfo> =
        files.names().mapNotNull { parse(it) }.sortedByDescending { it.createdAtMillis }

    companion object {
        const val INTERVAL_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val DEFAULT_KEEP = 6
        internal const val SETTINGS_FILE = "auto-backup.txt"
        private const val ON = "on"
        private const val OFF = "off"
        private const val PREFIX = "easyfood-auto-"
        private const val SUFFIX = ".json"
        private val NAME = Regex("""^easyfood-auto-(\d{4}-\d{2}-\d{2})-(\d+)\.json$""")

        /** Toca copiar si nunca se hizo o pasó el intervalo (un reloj retrasado no bloquea las copias para siempre). */
        fun isDue(lastMillis: Long?, nowMillis: Long): Boolean =
            lastMillis == null || nowMillis - lastMillis >= INTERVAL_MILLIS || nowMillis < lastMillis

        internal fun parse(name: String): BackupInfo? {
            val m = NAME.matchEntire(name) ?: return null
            val millis = m.groupValues[2].toLongOrNull() ?: return null
            return BackupInfo(name, m.groupValues[1], millis)
        }
    }
}
