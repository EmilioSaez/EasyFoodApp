package app.luxion.fitarchiveapp.backup

/** La actualización de la base de datos falló. Si había copia previa, ya se ha intentado restaurar ([restored]). */
class MigrationFailedException(message: String, val restored: Boolean, cause: Throwable?) : Exception(message, cause)

/** Copia del archivo de la base hecha justo antes de migrarla de la versión [from] a la [to]. */
data class MigrationCopy(val name: String, val from: Long, val to: Long, val date: String, val createdAtMillis: Long)

/**
 * Red de seguridad al cambiar la versión del esquema. Una exportación JSON no sirve aquí (el código nuevo no sabe
 * leer el esquema viejo), así que se copia el archivo de la base tal cual, antes de aplicar la migración, y se
 * conservan las [keep] últimas. Si la migración falla, la base se restaura desde esa copia.
 */
class MigrationBackup(
    private val files: BackupFiles,
    private val nowMillis: () -> Long,
    private val today: () -> String,
    private val keep: Int = DEFAULT_KEEP,
) {
    init { require(keep >= 1) }

    /**
     * Si la base existe y su versión es menor que [targetVersion], la copia y devuelve la copia hecha. Devuelve `null`
     * si no hay nada que migrar o si no se pudo copiar (no se bloquea el arranque por una copia fallida).
     */
    fun protect(targetVersion: Long): MigrationCopy? = try {
        val from = files.databaseVersion()
        if (from == null || from >= targetVersion) {
            null
        } else {
            val now = nowMillis()
            val date = today()
            val name = "$PREFIX$from-a-$targetVersion-$date-$now$SUFFIX"
            files.copyDatabaseTo(name)
            copies().drop(keep).forEach { files.delete(it.name) }
            MigrationCopy(name, from, targetVersion, date, now)
        }
    } catch (_: Exception) {
        null
    }

    /** Copias previas a una migración, de la más reciente a la más antigua. */
    fun copies(): List<MigrationCopy> =
        files.names().mapNotNull { parse(it) }.sortedByDescending { it.createdAtMillis }

    /** Restaura la base desde [copy]. La base no debe estar abierta. */
    fun restore(copy: MigrationCopy) = files.restoreDatabaseFrom(copy.name)

    /**
     * Abre la base con [open] (que es quien migra) protegiéndola: copia antes y, si [open] falla, restaura la copia
     * y lanza [MigrationFailedException]. [open] debe cerrar lo que haya abierto antes de lanzar, para que el
     * archivo se pueda reemplazar. Si no había nada que migrar, el error se relanza tal cual.
     */
    fun <T> openProtected(targetVersion: Long, open: () -> T): T {
        val copy = protect(targetVersion)
        try {
            return open()
        } catch (e: Exception) {
            if (copy == null) throw e
            val restored = try {
                restore(copy)
                true
            } catch (_: Exception) {
                false
            }
            throw MigrationFailedException(failureMessage(copy, restored), restored, e)
        }
    }

    private fun failureMessage(copy: MigrationCopy, restored: Boolean): String =
        "No se pudo actualizar la base de datos de la versión ${copy.from} a la ${copy.to}. " + if (restored) {
            "Tus datos se han recuperado desde la copia hecha antes de actualizar (${copy.name})."
        } else {
            "No se pudo restaurar automáticamente; la copia anterior a la actualización está en la carpeta de copias (${copy.name})."
        }

    companion object {
        const val DEFAULT_KEEP = 3
        private const val PREFIX = "pre-migracion-v"
        private const val SUFFIX = ".db"
        private val NAME = Regex("""^pre-migracion-v(\d+)-a-(\d+)-(\d{4}-\d{2}-\d{2})-(\d+)\.db$""")

        internal fun parse(name: String): MigrationCopy? {
            val m = NAME.matchEntire(name) ?: return null
            val (from, to, date, millis) = m.destructured
            return MigrationCopy(
                name, from.toLongOrNull() ?: return null, to.toLongOrNull() ?: return null, date,
                millis.toLongOrNull() ?: return null,
            )
        }
    }
}
