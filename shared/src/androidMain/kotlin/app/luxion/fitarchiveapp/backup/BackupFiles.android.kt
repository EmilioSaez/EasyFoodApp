package app.luxion.fitarchiveapp.backup

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

actual fun fileBackupFiles(directory: String, databasePath: String): BackupFiles =
    JavaFileBackupFiles(File(directory), File(databasePath))

/** [BackupFiles] con `java.io` (idéntica a la de escritorio: los dos módulos no comparten conjunto de fuentes). */
internal class JavaFileBackupFiles(private val dir: File, private val database: File) : BackupFiles {
    private fun wal(file: File) = File(file.path + "-wal")

    override fun names(): List<String> = dir.list()?.toList().orEmpty()

    override fun readText(name: String): String = File(dir, name).readText()

    override fun writeText(name: String, content: String) {
        dir.mkdirs()
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), File(dir, name).toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    override fun delete(name: String) {
        File(dir, name).delete()
        wal(File(dir, name)).delete()
    }

    override fun databaseVersion(): Long? {
        if (!database.isFile) return null
        val header = ByteArray(HEADER_SIZE)
        val read = database.inputStream().use { input ->
            var total = 0
            while (total < HEADER_SIZE) {
                val n = input.read(header, total, HEADER_SIZE - total)
                if (n < 0) break
                total += n
            }
            total
        }
        return sqliteUserVersion(header.copyOf(read))
    }

    override fun copyDatabaseTo(name: String) {
        dir.mkdirs()
        val target = File(dir, name)
        database.copyTo(target, overwrite = true)
        val sidecar = wal(database)
        if (sidecar.isFile) sidecar.copyTo(wal(target), overwrite = true) else wal(target).delete()
    }

    override fun restoreDatabaseFrom(name: String) {
        val source = File(dir, name)
        require(source.isFile) { "No existe la copia $name" }
        File(database.path + "-shm").delete()
        wal(database).delete()
        source.copyTo(database, overwrite = true)
        if (wal(source).isFile) wal(source).copyTo(wal(database), overwrite = true)
    }

    private companion object {
        const val HEADER_SIZE = 100
    }
}
