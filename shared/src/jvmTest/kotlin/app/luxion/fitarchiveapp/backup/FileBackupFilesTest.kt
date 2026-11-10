package app.luxion.fitarchiveapp.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.luxion.fitarchiveapp.db.EasyFoodDatabase
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La implementación real sobre archivos, con una base SQLite de verdad. */
class FileBackupFilesTest {
    private val root = Files.createTempDirectory("easyfood-backup-test").toFile()
    private val dbFile = File(root, "easyfood.db")
    private val files = fileBackupFiles(File(root, "backups").path, dbFile.path)

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun createDb(version: Long, mealsPerDay: Int?) {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        EasyFoodDatabase.Schema.create(driver)
        val db = EasyFoodDatabase(driver)
        mealsPerDay?.let { db.settingsQueries.upsertMealsPerDay(it.toLong()) }
        driver.execute(null, "PRAGMA user_version = $version", 0)
        driver.close()
    }

    private fun meals(): Int? {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        return try {
            EasyFoodDatabase(driver).settingsQueries.selectMealsPerDay().executeAsOneOrNull()?.meals_per_day?.toInt()
        } finally {
            driver.close()
        }
    }

    @Test
    fun readsTheUserVersionFromARealDatabase() {
        assertNull(files.databaseVersion())
        createDb(version = 7, mealsPerDay = 4)
        assertEquals(7L, files.databaseVersion())
    }

    @Test
    fun aLegacyDatabaseWithoutVersionReadsAsZero() {
        createDb(version = 0, mealsPerDay = null)
        assertEquals(0L, files.databaseVersion())
    }

    @Test
    fun copiesAndRestoresTheDatabase() {
        createDb(version = 9, mealsPerDay = 4)
        files.copyDatabaseTo("pre-migracion-v9-a-10-2026-11-10-1.db")

        // La migración «rompe» la base: se cambia un dato y se sube la versión.
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        EasyFoodDatabase(driver).settingsQueries.upsertMealsPerDay(6)
        driver.execute(null, "PRAGMA user_version = 10", 0)
        driver.close()
        assertEquals(6, meals())

        files.restoreDatabaseFrom("pre-migracion-v9-a-10-2026-11-10-1.db")
        assertEquals(4, meals())
        assertEquals(9L, files.databaseVersion())
    }

    @Test
    fun restoringAMissingCopyFailsWithoutTouchingTheDatabase() {
        createDb(version = 9, mealsPerDay = 4)
        assertFailsWith<IllegalArgumentException> { files.restoreDatabaseFrom("no-existe.db") }
        assertEquals(4, meals())
    }

    @Test
    fun walSidecarTravelsWithTheCopy() {
        createDb(version = 9, mealsPerDay = 3)
        File(dbFile.path + "-wal").writeBytes(byteArrayOf(1, 2, 3))
        files.copyDatabaseTo("a.db")
        assertContentEquals(byteArrayOf(1, 2, 3), File(root, "backups/a.db-wal").readBytes())
        files.delete("a.db")
        assertTrue(!File(root, "backups/a.db").exists() && !File(root, "backups/a.db-wal").exists())

        // Una copia sin -wal no deja uno ajeno en la base al restaurar.
        files.copyDatabaseTo("b.db")
        File(root, "backups/b.db-wal").delete()
        files.restoreDatabaseFrom("b.db")
        assertTrue(!File(dbFile.path + "-wal").exists())
    }

    @Test
    fun writesTextAtomicallyAndListsIt() {
        assertTrue(files.names().isEmpty())
        files.writeText("x.json", "uno")
        files.writeText("x.json", "dos")
        assertEquals("dos", files.readText("x.json"))
        assertEquals(listOf("x.json"), files.names())
        files.delete("x.json")
        files.delete("x.json") // no falla si ya no existe
        assertTrue(files.names().isEmpty())
    }

    @Test
    fun fullFlowMigrationFailureRestoresTheRealDatabase() {
        createDb(version = 9, mealsPerDay = 5)
        val migration = MigrationBackup(files, { 1L }, { "2026-11-10" })
        val ex = assertFailsWith<MigrationFailedException> {
            migration.openProtected(10) {
                val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
                driver.execute(null, "DROP TABLE SettingsEntity", 0)
                driver.execute(null, "PRAGMA user_version = 10", 0)
                driver.close()
                error("migración rota")
            }
        }
        assertTrue(ex.restored)
        assertEquals(5, meals())
        assertEquals(9L, files.databaseVersion())
    }
}
