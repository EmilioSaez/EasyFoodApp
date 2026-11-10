package app.luxion.fitarchiveapp.data.migration

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.backup.MigrationBackup
import app.luxion.fitarchiveapp.backup.MigrationFailedException
import app.luxion.fitarchiveapp.backup.fileBackupFiles
import app.luxion.fitarchiveapp.data.migration.MigrationTestKit.use
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * La copia previa a migrar funciona con el esquema actual, sea cual sea (las migraciones nuevas entran solas por
 * [MigrationTestKit.legacyVersions]): para cada versión antigua, con datos de todas las tablas y en un archivo real,
 * la copia es fiel a la base original y, si la migración falla, la base vuelve exactamente a como estaba.
 */
class PreMigrationBackupAllVersionsTest {
    private val root = Files.createTempDirectory("easyfood-premigration").toFile()

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private class Case(val dbFile: File, val before: Map<String, List<Map<String, String>>>)

    /** Base en archivo con el esquema de [version], sembrada con datos representativos y con `user_version` = [version]. */
    private fun legacyFile(version: Int): Case {
        val dir = File(root, "v$version").apply { mkdirs() }
        val dbFile = File(dir, "easyfood.db")
        val before = MigrationTestKit.legacyDatabase(version).use { memory ->
            RepresentativeUserData.seed(memory, version)
            memory.execute(null, "VACUUM INTO '${dbFile.absolutePath.replace("'", "''")}'", 0)
            MigrationTestKit.snapshot(memory)
        }
        fileDriver(dbFile).use { it.execute(null, "PRAGMA user_version = $version", 0) }
        return Case(dbFile, before)
    }

    private fun fileDriver(file: File) = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")

    private fun migration(dbFile: File) = MigrationBackup(
        fileBackupFiles(File(dbFile.parentFile, "backups").path, dbFile.path), nowMillis = { 1L }, today = { "2026-11-10" },
    )

    private fun migrateFile(dbFile: File, from: Int) {
        val driver = fileDriver(dbFile)
        try {
            EasyFoodDatabase.Schema.migrate(driver, from.toLong(), EasyFoodDatabase.Schema.version)
            driver.execute(null, "PRAGMA user_version = ${EasyFoodDatabase.Schema.version}", 0)
        } finally {
            driver.close()
        }
    }

    private fun snapshot(file: File) = fileDriver(file).use { MigrationTestKit.snapshot(it) }

    private fun userVersion(file: File) = fileDriver(file).use { MigrationTestKit.scalar(it, "PRAGMA user_version") }

    @Test
    fun theCopyBeforeMigratingIsFaithfulForEveryVersion() {
        val failures = mutableListOf<String>()
        for (version in MigrationTestKit.legacyVersions) {
            runCatching {
                val case = legacyFile(version)
                val migration = migration(case.dbFile)
                migration.openProtected(EasyFoodDatabase.Schema.version) { migrateFile(case.dbFile, version) }

                val copy = assertNotNull(migration.copies().singleOrNull(), "debe haber una copia")
                assertEquals(version.toLong(), copy.from)
                assertEquals(EasyFoodDatabase.Schema.version, copy.to)
                val copyFile = File(case.dbFile.parentFile, "backups/${copy.name}")
                assertEquals(version.toLong(), userVersion(copyFile), "la copia conserva la versión antigua")
                val problems = MigrationTestKit.differences(case.before, snapshot(copyFile))
                assertTrue(problems.isEmpty(), "la copia no es fiel: $problems")

                // Y la base real quedó migrada a la última versión sin perder datos.
                assertEquals(EasyFoodDatabase.Schema.version, userVersion(case.dbFile))
                val lost = MigrationTestKit.differences(case.before, snapshot(case.dbFile))
                assertTrue(lost.isEmpty(), "la base migrada perdió datos: $lost")
            }.onFailure { failures += "[desde v$version] $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aFailedMigrationRestoresTheExactOriginalForEveryVersion() {
        val failures = mutableListOf<String>()
        for (version in MigrationTestKit.legacyVersions) {
            runCatching {
                val case = legacyFile(version)
                val ex = runCatching {
                    migration(case.dbFile).openProtected(EasyFoodDatabase.Schema.version) {
                        migrateFile(case.dbFile, version)
                        // Fallo a mitad: la base ya está migrada (o a medias) cuando salta el error.
                        fileDriver(case.dbFile).use { it.execute(null, "DROP TABLE ProductEntity", 0) }
                        error("fallo simulado")
                    }
                }.exceptionOrNull()
                assertTrue(ex is MigrationFailedException && ex.restored, "debía restaurar: $ex")

                assertEquals(version.toLong(), userVersion(case.dbFile))
                val problems = MigrationTestKit.differences(case.before, snapshot(case.dbFile))
                assertTrue(problems.isEmpty(), "la restauración no devolvió la base original: $problems")
                // Y lo restaurado se puede volver a migrar con éxito en el siguiente arranque.
                migration(case.dbFile).openProtected(EasyFoodDatabase.Schema.version) { migrateFile(case.dbFile, version) }
                assertEquals(EasyFoodDatabase.Schema.version, userVersion(case.dbFile))
            }.onFailure { failures += "[desde v$version] $it" }
        }
        assertTrue(failures.isEmpty(), failures.joinToString("\n"))
    }

    @Test
    fun aDatabaseAlreadyAtTheLatestVersionMakesNoCopy() {
        val dbFile = File(root, "latest/easyfood.db").apply { parentFile.mkdirs() }
        fileDriver(dbFile).use { EasyFoodDatabase.Schema.create(it); it.execute(null, "PRAGMA user_version = ${EasyFoodDatabase.Schema.version}", 0) }
        val migration = migration(dbFile)
        migration.openProtected(EasyFoodDatabase.Schema.version) { }
        assertTrue(migration.copies().isEmpty())
    }
}
