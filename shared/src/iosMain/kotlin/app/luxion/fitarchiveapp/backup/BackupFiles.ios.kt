package app.luxion.fitarchiveapp.backup

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMakeRange
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.subdataWithRange
import platform.Foundation.writeToFile
import platform.posix.memcpy

actual fun fileBackupFiles(directory: String, databasePath: String): BackupFiles =
    IosBackupFiles(directory, databasePath)

/**
 * [BackupFiles] con `NSFileManager`. Sin Mac no se ha podido compilar ni probar (ver docs/ios-pendiente.md); la lógica
 * está toda en common y probada en JVM con la misma interfaz.
 */
internal class IosBackupFiles(private val directory: String, private val databasePath: String) : BackupFiles {
    private val fm = NSFileManager.defaultManager

    private fun path(name: String) = "$directory/$name"

    override fun names(): List<String> =
        (fm.contentsOfDirectoryAtPath(directory, null) as? List<*>).orEmpty().filterIsInstance<String>()

    override fun readText(name: String): String =
        NSString.stringWithContentsOfFile(path(name), NSUTF8StringEncoding, null) ?: error("No se pudo leer $name")

    override fun writeText(name: String, content: String) {
        fm.createDirectoryAtPath(directory, true, null, null)
        @Suppress("CAST_NEVER_SUCCEEDS")
        val ok = (content as NSString).writeToFile(path(name), true, NSUTF8StringEncoding, null)
        check(ok) { "No se pudo escribir $name" }
    }

    override fun delete(name: String) {
        fm.removeItemAtPath(path(name), null)
        fm.removeItemAtPath(path(name) + "-wal", null)
    }

    override fun databaseVersion(): Long? {
        if (!fm.fileExistsAtPath(databasePath)) return null
        val data = NSData.dataWithContentsOfFile(databasePath) ?: return null
        val head = data.subdataWithRange(NSMakeRange(0u, minOf(HEADER_SIZE.toULong(), data.length)))
        val bytes = ByteArray(head.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), head.bytes, head.length) }
        return sqliteUserVersion(bytes)
    }

    override fun copyDatabaseTo(name: String) {
        fm.createDirectoryAtPath(directory, true, null, null)
        replace(databasePath, path(name))
        if (fm.fileExistsAtPath("$databasePath-wal")) replace("$databasePath-wal", path(name) + "-wal")
        else fm.removeItemAtPath(path(name) + "-wal", null)
    }

    override fun restoreDatabaseFrom(name: String) {
        check(fm.fileExistsAtPath(path(name))) { "No existe la copia $name" }
        fm.removeItemAtPath("$databasePath-shm", null)
        fm.removeItemAtPath("$databasePath-wal", null)
        replace(path(name), databasePath)
        if (fm.fileExistsAtPath(path(name) + "-wal")) replace(path(name) + "-wal", "$databasePath-wal")
    }

    private fun replace(from: String, to: String) {
        fm.removeItemAtPath(to, null)
        check(fm.copyItemAtPath(from, to, null)) { "No se pudo copiar $from" }
    }

    private companion object {
        const val HEADER_SIZE = 100
    }
}
