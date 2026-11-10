package app.luxion.fitarchiveapp.backup

/**
 * Carpeta de copias de seguridad y acceso al archivo de la base de datos. Lo implementa cada plataforma
 * ([fileBackupFiles]); la lógica (nombres, rotación, cuándo toca copiar) vive en [BackupManager] y [MigrationBackup].
 * Es E/S bloqueante: quien la llama decide el hilo.
 */
interface BackupFiles {
    /** Nombres de los archivos de la carpeta de copias (vacía si aún no existe). */
    fun names(): List<String>

    fun readText(name: String): String

    /** Escribe [content] creando la carpeta si hace falta; no deja un archivo a medias si falla. */
    fun writeText(name: String, content: String)

    /** Borra el archivo (y su `-wal` si lo tiene). No falla si no existe. */
    fun delete(name: String)

    /** `PRAGMA user_version` de la base, leído de la cabecera del archivo; `null` si la base aún no existe. */
    fun databaseVersion(): Long?

    /** Copia el archivo de la base (y su `-wal` si lo hay) a la carpeta de copias con ese nombre. */
    fun copyDatabaseTo(name: String)

    /** Reemplaza la base por la copia [name]. La base no debe estar abierta. */
    fun restoreDatabaseFrom(name: String)
}

/** [BackupFiles] sobre el sistema de archivos de la plataforma: [directory] para las copias y [databasePath] para la base. */
expect fun fileBackupFiles(directory: String, databasePath: String): BackupFiles

/** `PRAGMA user_version` guardado en la cabecera de un archivo SQLite (bytes 60 a 63), o `null` si no es SQLite. */
fun sqliteUserVersion(header: ByteArray): Long? {
    val magic = "SQLite format 3".encodeToByteArray()
    if (header.size < 64 || header[15].toInt() != 0) return null
    for (i in magic.indices) if (header[i] != magic[i]) return null
    var v = 0L
    for (i in 60..63) v = (v shl 8) or (header[i].toLong() and 0xFF)
    return v
}
